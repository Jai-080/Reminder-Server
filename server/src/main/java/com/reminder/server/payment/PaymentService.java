package com.reminder.server.payment;

import com.reminder.server.config.ResourceNotFoundException;
import com.reminder.server.user.User;
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
                .createdAt(now)
                .updatedAt(now)
                .deleted(false)
                .build();
        return mapToResponse(monthlyPaymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse updatePayment(User user, Long id, PaymentRequest request) {
        MonthlyPayment payment = monthlyPaymentRepository.findByIdAndUserIdAndDeletedFalse(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("MonthlyPayment not found with id: " + id));

        payment.setName(request.getName());
        payment.setDueDate(request.getDueDate());
        payment.setCompleted(request.getCompleted());
        payment.setUpdatedAt(Instant.now());

        return mapToResponse(monthlyPaymentRepository.save(payment));
    }

    @Transactional
    public void deletePayment(User user, Long id) {
        MonthlyPayment payment = monthlyPaymentRepository.findByIdAndUserIdAndDeletedFalse(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("MonthlyPayment not found with id: " + id));

        payment.setDeleted(true);
        payment.setDeletedAt(Instant.now());
        monthlyPaymentRepository.save(payment);
    }

    private PaymentResponse mapToResponse(MonthlyPayment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .name(payment.getName())
                .dueDate(payment.getDueDate())
                .completed(payment.getCompleted())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
