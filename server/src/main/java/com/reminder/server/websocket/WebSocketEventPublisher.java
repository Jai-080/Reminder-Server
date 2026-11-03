package com.reminder.server.websocket;

import com.reminder.server.util.LoggingUtils;
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

        log.info("[WS-PUBLISH] entityType={} operation={} serverId={} user={} timestamp={}",
                entityType, operation, serverId, LoggingUtils.maskEmail(email), updatedAt);
        log.info("[WS-PUBLISH] destination: {}",
                LoggingUtils.maskDestination("/user/" + email + "/topic/sync"));

        // Publish strictly to the user-specific channel using email as the principal identifier
        messagingTemplate.convertAndSendToUser(email, "/topic/sync", event);
    }
}
