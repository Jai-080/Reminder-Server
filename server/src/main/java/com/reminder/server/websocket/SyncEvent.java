package com.reminder.server.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncEvent {
    private String entityType;  // "NOTE", "REMINDER", "PAYMENT"
    private String operation;   // "CREATED", "UPDATED", "DELETED"
    private Long serverId;
    private String username;
    private Instant updatedAt;
}
