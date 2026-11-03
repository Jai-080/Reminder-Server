package com.reminder.server.payment;

import com.reminder.server.config.ResourceNotFoundException;
import com.reminder.server.user.User;
import com.reminder.server.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final MonthlyPaymentRepository monthlyPaymentRepository;
    private final WebSocketEventPublisher webSocketEventPublisher;

    public List<PaymentResponse> getAllPayments(User user) {
        return monthlyPaymentRepository.findByUserIdAndDeletedFalse(user.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public Page<PaymentResponse> getAllPaymentsPaginated(User user, Pageable pageable) {
        return monthlyPaymentRepository.findByUserIdAndDeletedFalse(user.getId(), pageable)
                .map(this::mapToResponse);
    }

    public PaymentResponse getPaymentById(User user, Long id) {
        MonthlyPayment payment = monthlyPaymentRepository.findByIdAndUserIdAndDeletedFalse(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("MonthlyPayment not found with id: " + id));
        return mapToResponse(payment);
    }

    @Transactional
    public PaymentResponse createPayment(User user, PaymentRequest request) {
        Instant now = Instant.now();
        RecurrenceType recurrence = request.getRecurrence() != null
                ? RecurrenceType.fromString(request.getRecurrence())
                : RecurrenceType.MONTHLY;
        if (recurrence == RecurrenceType.UNKNOWN) {
            throw new IllegalArgumentException("Unknown or unsupported recurrence type: " + request.getRecurrence());
        }

        Integer originalDueDay = resolveOriginalDueDay(request.getOriginalDueDay(), request.getDueDate());
        Integer customIntervalDays = resolveCustomIntervalDays(recurrence, request.getCustomIntervalDays());

        Long dueDate = request.getDueDate();
        Boolean completed = request.getCompleted();
        Long lastPaidAt = request.getLastPaidAt();

        // If completed is true on creation of a recurring payment, advance to next cycle
        if (Boolean.TRUE.equals(completed) && recurrence != RecurrenceType.ONE_TIME) {
            if (lastPaidAt == null) {
                lastPaidAt = now.toEpochMilli();
            }
            dueDate = RecurrenceCalculator.calculateNextDueDate(
                    request.getDueDate(),
                    recurrence,
                    originalDueDay,
                    customIntervalDays,
                    now.toEpochMilli()
            );
            completed = false;
        } else if (Boolean.TRUE.equals(completed) && lastPaidAt == null) {
            lastPaidAt = now.toEpochMilli();
        }

        MonthlyPayment payment = MonthlyPayment.builder()
                .user(user)
                .name(request.getName())
                .dueDate(dueDate)
                .completed(completed)
                .lastPaidAt(lastPaidAt)
                .amount(request.getAmount())
                .recurrence(recurrence)
                .notificationOffsets(request.getNotificationOffsets() != null ? request.getNotificationOffsets() : "0")
                .originalDueDay(originalDueDay)
                .customIntervalDays(customIntervalDays)
                .createdAt(now)
                .updatedAt(now)
                .deleted(false)
                .build();

        MonthlyPayment saved = monthlyPaymentRepository.save(payment);
        PaymentResponse response = mapToResponse(saved);
        webSocketEventPublisher.publish("PAYMENT", "PAYMENT_CREATED", saved.getId(), user.getEmail(), saved.getUpdatedAt());
        return response;
    }

    @Transactional
    public PaymentResponse updatePayment(User user, Long id, PaymentRequest request) {
        MonthlyPayment payment = monthlyPaymentRepository.findByIdAndUserIdAndDeletedFalse(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("MonthlyPayment not found with id: " + id));

        Instant incomingUpdatedAt = parseInstant(request.getUpdatedAt());
        Instant existingUpdatedAt = payment.getUpdatedAt();

        if (incomingUpdatedAt.isAfter(existingUpdatedAt)) {
            System.out.println("[LWW]\n" +
                    "Entity=Payment\n" +
                    "Id=" + id + "\n" +
                    "ServerUpdatedAt=" + existingUpdatedAt.toEpochMilli() + "\n" +
                    "IncomingUpdatedAt=" + incomingUpdatedAt.toEpochMilli() + "\n" +
                    "Decision=ACCEPTED");

            RecurrenceType recurrence = request.getRecurrence() != null
                    ? RecurrenceType.fromString(request.getRecurrence())
                    : payment.getRecurrence();
            if (recurrence == RecurrenceType.UNKNOWN) {
                throw new IllegalArgumentException("Unknown or unsupported recurrence type: " + request.getRecurrence());
            }

            Integer originalDueDay = request.getOriginalDueDay() != null
                    ? request.getOriginalDueDay()
                    : payment.getOriginalDueDay();
            originalDueDay = resolveOriginalDueDay(originalDueDay, request.getDueDate());

            Integer customIntervalDays = request.getCustomIntervalDays() != null
                    ? request.getCustomIntervalDays()
                    : payment.getCustomIntervalDays();
            customIntervalDays = resolveCustomIntervalDays(recurrence, customIntervalDays);

            Long dueDate = request.getDueDate();
            Boolean completed = request.getCompleted();
            Long lastPaidAt = request.getLastPaidAt();

            // Check if payment was submitted as completed for a recurring payment
            if (Boolean.TRUE.equals(request.getCompleted()) && recurrence != RecurrenceType.ONE_TIME) {
                if (lastPaidAt == null) {
                    lastPaidAt = incomingUpdatedAt.toEpochMilli();
                }
                // Advance due date deterministically
                dueDate = RecurrenceCalculator.calculateNextDueDate(
                        request.getDueDate() != null ? request.getDueDate() : payment.getDueDate(),
                        recurrence,
                        originalDueDay,
                        customIntervalDays,
                        incomingUpdatedAt.toEpochMilli()
                );
                completed = false;
            } else {
                if (lastPaidAt == null) {
                    if (Boolean.TRUE.equals(request.getCompleted()) && payment.getLastPaidAt() == null) {
                        lastPaidAt = incomingUpdatedAt.toEpochMilli();
                    } else {
                        lastPaidAt = payment.getLastPaidAt(); // preserve existing
                    }
                }
            }

            payment.setName(request.getName());
            payment.setDueDate(dueDate);
            payment.setCompleted(completed);
            payment.setLastPaidAt(lastPaidAt);
            payment.setAmount(request.getAmount());
            payment.setRecurrence(recurrence);
            payment.setOriginalDueDay(originalDueDay);
            payment.setCustomIntervalDays(customIntervalDays);

            if (request.getNotificationOffsets() != null) {
                payment.setNotificationOffsets(request.getNotificationOffsets());
            }
            payment.setUpdatedAt(incomingUpdatedAt);
            MonthlyPayment saved = monthlyPaymentRepository.save(payment);
            webSocketEventPublisher.publish("PAYMENT", "PAYMENT_UPDATED", saved.getId(), user.getEmail(), saved.getUpdatedAt());
            return mapToResponse(saved);
        } else {
            System.out.println("[LWW]\n" +
                    "Entity=Payment\n" +
                    "Id=" + id + "\n" +
                    "ServerUpdatedAt=" + existingUpdatedAt.toEpochMilli() + "\n" +
                    "IncomingUpdatedAt=" + incomingUpdatedAt.toEpochMilli() + "\n" +
                    "Decision=REJECTED");

            return mapToResponse(payment);
        }
    }

    @Transactional
    public PaymentResponse completePayment(User user, Long id) {
        MonthlyPayment payment = monthlyPaymentRepository.findByIdAndUserIdAndDeletedFalse(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("MonthlyPayment not found with id: " + id));

        Instant now = Instant.now();
        long nowMillis = now.toEpochMilli();
        payment.setLastPaidAt(nowMillis);

        if (payment.getRecurrence() == RecurrenceType.ONE_TIME || payment.getRecurrence() == RecurrenceType.UNKNOWN) {
            payment.setCompleted(true);
        } else {
            int anchorDay = payment.getEffectiveOriginalDueDay();
            int customInterval = payment.getEffectiveCustomIntervalDays();
            long nextDueDate = RecurrenceCalculator.calculateNextDueDate(
                    payment.getDueDate(),
                    payment.getRecurrence(),
                    anchorDay,
                    customInterval,
                    nowMillis
            );
            payment.setDueDate(nextDueDate);
            payment.setCompleted(false);
        }
        payment.setUpdatedAt(now);
        MonthlyPayment saved = monthlyPaymentRepository.save(payment);
        webSocketEventPublisher.publish("PAYMENT", "PAYMENT_UPDATED", saved.getId(), user.getEmail(), saved.getUpdatedAt());
        return mapToResponse(saved);
    }

    @Transactional
    public void deletePayment(User user, Long id) {
        MonthlyPayment payment = monthlyPaymentRepository.findByIdAndUserIdAndDeletedFalse(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("MonthlyPayment not found with id: " + id));

        payment.setDeleted(true);
        payment.setDeletedAt(Instant.now());
        MonthlyPayment saved = monthlyPaymentRepository.save(payment);
        webSocketEventPublisher.publish("PAYMENT", "PAYMENT_DELETED", id, user.getEmail(), saved.getDeletedAt());
    }

    private Integer resolveOriginalDueDay(Integer originalDueDay, Long dueDate) {
        if (originalDueDay != null && originalDueDay >= 1 && originalDueDay <= 31) {
            return originalDueDay;
        }
        if (dueDate != null && dueDate > 0) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(dueDate);
            return cal.get(Calendar.DAY_OF_MONTH);
        }
        return null;
    }

    private Integer resolveCustomIntervalDays(RecurrenceType recurrence, Integer customIntervalDays) {
        if (recurrence == RecurrenceType.CUSTOM_DAYS) {
            if (customIntervalDays == null || customIntervalDays < 1 || customIntervalDays > 365) {
                throw new IllegalArgumentException("customIntervalDays must be between 1 and 365 for CUSTOM_DAYS recurrence");
            }
            return customIntervalDays;
        }
        return customIntervalDays;
    }

    private PaymentResponse mapToResponse(MonthlyPayment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .name(payment.getName())
                .dueDate(payment.getDueDate())
                .completed(payment.getCompleted())
                .lastPaidAt(payment.getLastPaidAt())
                .amount(payment.getAmount())
                .recurrence(payment.getRecurrence() != null ? payment.getRecurrence().name() : "MONTHLY")
                .notificationOffsets(payment.getNotificationOffsets() != null ? payment.getNotificationOffsets() : "0")
                .originalDueDay(payment.getOriginalDueDay())
                .customIntervalDays(payment.getCustomIntervalDays())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    private Instant parseInstant(String instantStr) {
        if (instantStr == null || instantStr.isEmpty()) {
            return Instant.now();
        }
        try {
            return Instant.parse(instantStr);
        } catch (Exception e) {
            try {
                return Instant.ofEpochMilli(Long.parseLong(instantStr));
            } catch (Exception ex) {
                return Instant.now();
            }
        }
    }
}
