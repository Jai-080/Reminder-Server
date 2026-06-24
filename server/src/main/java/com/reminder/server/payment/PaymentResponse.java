package com.reminder.server.payment;

import lombok.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private Long id;
    private String name;
    private Long dueDate;
    private Boolean completed;
    private Long lastPaidAt;
    private java.math.BigDecimal amount;
    private String recurrence;
    private String notificationOffsets;
    private Instant createdAt;
    private Instant updatedAt;
}
