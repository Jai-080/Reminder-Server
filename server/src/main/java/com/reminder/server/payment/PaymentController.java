package com.reminder.server.payment;

import com.reminder.server.security.CustomUserPrincipal;
import com.reminder.server.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    public ResponseEntity<?> getPayments(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        User user = principal.getUser();
        if (page != null && size != null) {
            return ResponseEntity.ok(paymentService.getAllPaymentsPaginated(user, PageRequest.of(page, size)));
        }
        return ResponseEntity.ok(paymentService.getAllPayments(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPaymentById(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(paymentService.getPaymentById(principal.getUser(), id));
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody PaymentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createPayment(principal.getUser(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PaymentResponse> updatePayment(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody PaymentRequest request
    ) {
        return ResponseEntity.ok(paymentService.updatePayment(principal.getUser(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayment(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long id
    ) {
        paymentService.deletePayment(principal.getUser(), id);
        return ResponseEntity.noContent().build();
    }
}
