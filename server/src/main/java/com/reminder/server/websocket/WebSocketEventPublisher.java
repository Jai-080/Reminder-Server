package com.reminder.server.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public void publish(String entityType, String operation, Long serverId, String email, Instant updatedAt) {
        SyncEvent event = SyncEvent.builder()
                .entityType(entityType)
                .operation(operation)
                .serverId(serverId)
                .username(email)
                .updatedAt(updatedAt)
                .build();

        log.info("WebSocket sync event published: user={}, eventType={}, entity={}, serverId={}, timestamp={}",
                email, operation, entityType, serverId, updatedAt);

        System.out.println("Publishing event:\nentityType=" + entityType + "\noperation=" + operation + "\nemail=" + email + "\nserverId=" + serverId);

        // Publish strictly to the user-specific channel using email as the principal identifier
        messagingTemplate.convertAndSendToUser(email, "/topic/sync", event);
    }
}
