package com.reminder.server.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("originalDueDay")
    private Integer originalDueDay;

    @JsonProperty("customIntervalDays")
    private Integer customIntervalDays;

    private Instant createdAt;
    private Instant updatedAt;
}
