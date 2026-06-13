package com.reminder.server.payment;

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

    private String updatedAt;
}
