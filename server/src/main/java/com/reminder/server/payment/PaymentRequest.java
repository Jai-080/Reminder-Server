package com.reminder.server.payment;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotBlank(message = "Name cannot be blank")
    private String name;

    @NotNull(message = "Due date is required")
    private Long dueDate;

    @NotNull(message = "Completed status is required")
    private Boolean completed;

    private Long lastPaidAt;

    private java.math.BigDecimal amount;

    private String recurrence;

    private String notificationOffsets;

    private String updatedAt;

    @Min(value = 1, message = "Original due day must be between 1 and 31")
    @Max(value = 31, message = "Original due day must be between 1 and 31")
    @JsonProperty("originalDueDay")
    @JsonAlias({"original_due_day", "originalDueDay"})
    private Integer originalDueDay;

    @Min(value = 1, message = "Custom interval days must be at least 1")
    @Max(value = 365, message = "Custom interval days must not exceed 365")
    @JsonProperty("customIntervalDays")
    @JsonAlias({"custom_interval_days", "customIntervalDays"})
    private Integer customIntervalDays;
}
