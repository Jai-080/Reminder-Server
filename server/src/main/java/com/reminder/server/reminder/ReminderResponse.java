package com.reminder.server.reminder;

import lombok.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderResponse {
    private Long id;
    private String text;
    private Long reminderTime;
    private Boolean isExpired;
    private Long snoozedTime;
    private Instant createdAt;
    private Instant updatedAt;
}
