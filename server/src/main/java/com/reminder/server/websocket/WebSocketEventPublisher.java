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

    public void publish(String entityType, String operation, Long serverId, String username, Instant updatedAt) {
        SyncEvent event = SyncEvent.builder()
                .entityType(entityType)
                .operation(operation)
                .serverId(serverId)
                .username(username)
                .updatedAt(updatedAt)
                .build();

        log.info("WebSocket sync event published: user={}, eventType={}, entity={}, serverId={}, timestamp={}",
                username, operation, entityType, serverId, updatedAt);

        // Publish strictly to the user-specific channel
        messagingTemplate.convertAndSendToUser(username, "/topic/sync", event);
    }
}
