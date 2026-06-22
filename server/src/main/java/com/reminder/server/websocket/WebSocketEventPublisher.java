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

        System.out.println("entityType: " + entityType);
        log.info("entityType: " + entityType);
        System.out.println("operation: " + operation);
        log.info("operation: " + operation);
        System.out.println("email: " + email);
        log.info("email: " + email);
        System.out.println("serverId: " + serverId);
        log.info("serverId: " + serverId);
        System.out.println("timestamp: " + updatedAt);
        log.info("timestamp: " + updatedAt);
        String destination = "/user/" + email + "/topic/sync";
        System.out.println("destination: " + destination);
        log.info("destination: " + destination);

        // Publish strictly to the user-specific channel using email as the principal identifier
        messagingTemplate.convertAndSendToUser(email, "/topic/sync", event);
    }
}
