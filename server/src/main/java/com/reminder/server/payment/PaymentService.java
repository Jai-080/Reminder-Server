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
        MonthlyPayment payment = MonthlyPayment.builder()
                .user(user)
                .name(request.getName())
                .dueDate(request.getDueDate())
                .completed(request.getCompleted())
                .amount(request.getAmount())
                .recurrence(request.getRecurrence() != null ? RecurrenceType.valueOf(request.getRecurrence().toUpperCase()) : RecurrenceType.MONTHLY)
                .notificationOffsets(request.getNotificationOffsets() != null ? request.getNotificationOffsets() : "0")
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

            payment.setName(request.getName());
            payment.setDueDate(request.getDueDate());
            payment.setCompleted(request.getCompleted());
            payment.setAmount(request.getAmount());
            if (request.getRecurrence() != null) {
                payment.setRecurrence(RecurrenceType.valueOf(request.getRecurrence().toUpperCase()));
            }
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
    public void deletePayment(User user, Long id) {
        MonthlyPayment payment = monthlyPaymentRepository.findByIdAndUserIdAndDeletedFalse(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("MonthlyPayment not found with id: " + id));

        payment.setDeleted(true);
        payment.setDeletedAt(Instant.now());
        MonthlyPayment saved = monthlyPaymentRepository.save(payment);
        webSocketEventPublisher.publish("PAYMENT", "PAYMENT_DELETED", id, user.getEmail(), saved.getDeletedAt());
    }

    private PaymentResponse mapToResponse(MonthlyPayment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .name(payment.getName())
                .dueDate(payment.getDueDate())
                .completed(payment.getCompleted())
                .amount(payment.getAmount())
                .recurrence(payment.getRecurrence() != null ? payment.getRecurrence().name() : "MONTHLY")
                .notificationOffsets(payment.getNotificationOffsets() != null ? payment.getNotificationOffsets() : "0")
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
