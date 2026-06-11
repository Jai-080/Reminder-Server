package com.reminder.server.reminder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderRequest {

    @NotBlank(message = "Text cannot be blank")
    private String text;

    @NotNull(message = "Reminder time is required")
    private Long reminderTime;

    @NotNull(message = "isExpired status is required")
    private Boolean isExpired;

    private Long snoozedTime;
}
