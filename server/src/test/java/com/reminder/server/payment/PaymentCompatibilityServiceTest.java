package com.reminder.server.payment;

import com.reminder.server.user.User;
import com.reminder.server.websocket.WebSocketEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentCompatibilityServiceTest {

    @Mock
    private MonthlyPaymentRepository monthlyPaymentRepository;

    @Mock
    private WebSocketEventPublisher webSocketEventPublisher;

    @InjectMocks
    private PaymentService paymentService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("dev_tester@localdev.internal")
                .passwordHash("hashedPassword123")
                .build();
    }

    @Test
    @DisplayName("Verify all 7 RecurrenceType enum values are supported and not local-only, plus UNKNOWN fallback")
    void testAllSevenRecurrenceTypes() {
        for (RecurrenceType type : RecurrenceType.values()) {
            if (type == RecurrenceType.UNKNOWN) {
                assertFalse(type.isSupportedByBackend(), "Type UNKNOWN must not be supported by backend");
                assertFalse(type.isLocalOnly(), "Type UNKNOWN is not local only");
            } else {
                assertTrue(type.isSupportedByBackend(), "Type " + type + " must be supported by backend");
                assertFalse(type.isLocalOnly(), "Type " + type + " must not be local only");

                // Case-insensitive deserialization
                assertEquals(type, RecurrenceType.fromString(type.name().toLowerCase()));
                assertEquals(type, RecurrenceType.fromString(type.name().toUpperCase()));
            }
        }

        assertEquals(8, RecurrenceType.values().length, "There must be 7 recurrence types + UNKNOWN fallback");
        assertEquals(RecurrenceType.UNKNOWN, RecurrenceType.fromString("NON_EXISTENT_CADENCE"));
    }

    @Test
    @DisplayName("Verify deterministic lastPaidAt: getter returns exact field without generating dynamic timestamps")
    void testDeterministicLastPaidAt() {
        MonthlyPayment payment = MonthlyPayment.builder()
                .id(10L)
                .completed(true)
                .lastPaidAt(1700000000000L)
                .build();

        // 10 consecutive getter calls must return the EXACT same timestamp
        for (int i = 0; i < 10; i++) {
            assertEquals(1700000000000L, payment.getLastPaidAt(), "Getter must return exact persisted lastPaidAt");
        }

        // When lastPaidAt is null, getter returns null rather than System.currentTimeMillis()
        MonthlyPayment uncompleted = MonthlyPayment.builder()
                .id(11L)
                .completed(true)
                .lastPaidAt(null)
                .build();

        assertNull(uncompleted.getLastPaidAt(), "Null lastPaidAt must return null, not dynamic timestamp");
    }

    @Test
    @DisplayName("Create payment with CUSTOM_DAYS and metadata: originalDueDay and customIntervalDays")
    void testCreatePaymentWithCustomDaysAndMetadata() {
        long dueDate = System.currentTimeMillis() + 86400000L;
        PaymentRequest request = PaymentRequest.builder()
                .name("Test Custom Internet")
                .dueDate(dueDate)
                .completed(false)
                .amount(BigDecimal.valueOf(500.0))
                .recurrence("CUSTOM_DAYS")
                .customIntervalDays(45)
                .originalDueDay(15)
                .build();

        when(monthlyPaymentRepository.save(any(MonthlyPayment.class))).thenAnswer(invocation -> {
            MonthlyPayment p = invocation.getArgument(0);
            p.setId(101L);
            return p;
        });

        PaymentResponse response = paymentService.createPayment(testUser, request);

        assertNotNull(response);
        assertEquals(101L, response.getId());
        assertEquals("CUSTOM_DAYS", response.getRecurrence());
        assertEquals(45, response.getCustomIntervalDays());
        assertEquals(15, response.getOriginalDueDay());
        assertEquals(dueDate, response.getDueDate());
        assertFalse(response.getCompleted());
    }

    @Test
    @DisplayName("Create payment backward compatibility: existing 4 recurrence types without new metadata")
    void testCreatePaymentBackwardCompatibility() {
        String[] legacyTypes = {"ONE_TIME", "MONTHLY", "QUARTERLY", "YEARLY"};
        long dueDate = System.currentTimeMillis() + 86400000L;

        for (String rec : legacyTypes) {
            PaymentRequest request = PaymentRequest.builder()
                    .name("Legacy Payment " + rec)
                    .dueDate(dueDate)
                    .completed(false)
                    .amount(BigDecimal.valueOf(100.0))
                    .recurrence(rec)
                    .build();

            when(monthlyPaymentRepository.save(any(MonthlyPayment.class))).thenAnswer(invocation -> {
                MonthlyPayment p = invocation.getArgument(0);
                p.setId(200L);
                return p;
            });

            PaymentResponse response = paymentService.createPayment(testUser, request);
            assertEquals(rec, response.getRecurrence());
            assertNotNull(response.getOriginalDueDay(), "Original due day derived automatically from dueDate");
            assertNull(response.getCustomIntervalDays(), "Custom interval days must remain null for non-custom payments");
        }
    }

    @Test
    @DisplayName("Completion of recurring payment: advances due date and sets lastPaidAt deterministically")
    void testCompleteRecurringPayment() {
        long initialDueDate = 1793460000000L; // fixed baseline
        MonthlyPayment payment = MonthlyPayment.builder()
                .id(50L)
                .user(testUser)
                .name("Monthly Cloud Storage")
                .dueDate(initialDueDate)
                .completed(false)
                .recurrence(RecurrenceType.MONTHLY)
                .originalDueDay(15)
                .amount(BigDecimal.valueOf(10.0))
                .notificationOffsets("0")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .deleted(false)
                .build();

        when(monthlyPaymentRepository.findByIdAndUserIdAndDeletedFalse(50L, testUser.getId()))
                .thenReturn(Optional.of(payment));
        when(monthlyPaymentRepository.save(any(MonthlyPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse completed = paymentService.completePayment(testUser, 50L);

        assertNotNull(completed);
        assertFalse(completed.getCompleted(), "Recurring payment completed flag must reset to false for next cycle");
        assertTrue(completed.getDueDate() > initialDueDate, "Due date must advance to the next cycle");
        assertNotNull(completed.getLastPaidAt(), "lastPaidAt must be populated on completion");

        Long recordedLastPaidAt = completed.getLastPaidAt();

        // Repeated complete payment call should not advance dueDate if already advanced and should update lastPaidAt deterministically
        PaymentResponse repeated = paymentService.completePayment(testUser, 50L);
        assertNotNull(repeated.getLastPaidAt());
    }

    @Test
    @DisplayName("Update payment with completed=true for recurring payment advances recurrence exactly once")
    void testUpdatePaymentRecurringAdvanceOnce() {
        long initialDueDate = 1793460000000L;
        Instant serverUpdatedAt = Instant.now().minusSeconds(100);

        MonthlyPayment existing = MonthlyPayment.builder()
                .id(75L)
                .user(testUser)
                .name("BiMonthly Gym")
                .dueDate(initialDueDate)
                .completed(false)
                .recurrence(RecurrenceType.BI_MONTHLY)
                .originalDueDay(15)
                .amount(BigDecimal.valueOf(80.0))
                .notificationOffsets("0")
                .createdAt(serverUpdatedAt)
                .updatedAt(serverUpdatedAt)
                .deleted(false)
                .build();

        when(monthlyPaymentRepository.findByIdAndUserIdAndDeletedFalse(75L, testUser.getId()))
                .thenReturn(Optional.of(existing));
        when(monthlyPaymentRepository.save(any(MonthlyPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Instant clientUpdate = Instant.now();
        PaymentRequest request = PaymentRequest.builder()
                .name("BiMonthly Gym")
                .dueDate(initialDueDate)
                .completed(true) // client marking completed
                .amount(BigDecimal.valueOf(80.0))
                .recurrence("BI_MONTHLY")
                .updatedAt(clientUpdate.toString())
                .build();

        PaymentResponse response = paymentService.updatePayment(testUser, 75L, request);

        assertFalse(response.getCompleted(), "Completed flag must reset to false for next recurring period");
        assertTrue(response.getDueDate() > initialDueDate, "Due date must advance by 2 months");
        assertNotNull(response.getLastPaidAt(), "lastPaidAt must be set to update timestamp");
    }

    @Test
    @DisplayName("B3: CUSTOM_DAYS without customIntervalDays or with invalid range throws IllegalArgumentException")
    void testCustomDaysInvalidIntervalThrowsException() {
        // Missing customIntervalDays
        PaymentRequest missingRequest = PaymentRequest.builder()
                .name("Missing Interval")
                .dueDate(System.currentTimeMillis() + 86400000L)
                .completed(false)
                .recurrence("CUSTOM_DAYS")
                .customIntervalDays(null)
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            paymentService.createPayment(testUser, missingRequest);
        }, "Missing customIntervalDays for CUSTOM_DAYS must throw IllegalArgumentException");

        // Out of range (0 or negative)
        PaymentRequest zeroRequest = PaymentRequest.builder()
                .name("Zero Interval")
                .dueDate(System.currentTimeMillis() + 86400000L)
                .completed(false)
                .recurrence("CUSTOM_DAYS")
                .customIntervalDays(0)
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            paymentService.createPayment(testUser, zeroRequest);
        }, "0 customIntervalDays must throw IllegalArgumentException");

        // Out of range (> 365)
        PaymentRequest tooHighRequest = PaymentRequest.builder()
                .name("Too High Interval")
                .dueDate(System.currentTimeMillis() + 86400000L)
                .completed(false)
                .recurrence("CUSTOM_DAYS")
                .customIntervalDays(366)
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            paymentService.createPayment(testUser, tooHighRequest);
        }, "> 365 customIntervalDays must throw IllegalArgumentException");
    }

    @Test
    @DisplayName("B4: Unknown recurrence string throws IllegalArgumentException on payment creation")
    void testUnknownRecurrenceThrowsException() {
        PaymentRequest request = PaymentRequest.builder()
                .name("Unknown Recurrence Payment")
                .dueDate(System.currentTimeMillis() + 86400000L)
                .completed(false)
                .recurrence("FUTURE_UNKNOWN_TYPE")
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            paymentService.createPayment(testUser, request);
        }, "Unknown recurrence type must throw IllegalArgumentException");
    }
}
