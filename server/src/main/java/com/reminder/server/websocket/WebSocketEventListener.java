package com.reminder.server.websocket;

import com.reminder.server.util.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class WebSocketEventListener {

    private final Map<String, Long> sessionStartTimes = new ConcurrentHashMap<>();

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (sessionId != null) {
            sessionStartTimes.put(sessionId, System.currentTimeMillis());
        }
        log.info("[WS-EVENT] STOMP connect initiated - sessionId={}", sessionId);
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String user = accessor.getUser() != null ? accessor.getUser().getName() : "anonymous";
        log.info("[WS-EVENT] STOMP session connected - sessionId={} user={}",
                sessionId, LoggingUtils.maskEmail(user));
    }

    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String destination = accessor.getDestination();
        String user = accessor.getUser() != null ? accessor.getUser().getName() : "anonymous";
        log.info("[WS-EVENT] STOMP subscribed - sessionId={} user={} destination={}",
                sessionId, LoggingUtils.maskEmail(user), LoggingUtils.maskDestination(destination));
    }

    @EventListener
    public void handleSessionUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        log.info("[WS-EVENT] STOMP unsubscribed - sessionId={} subId={}", sessionId, subscriptionId);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String user = accessor.getUser() != null ? accessor.getUser().getName() : "anonymous";
        Long startTime = sessionId != null ? sessionStartTimes.remove(sessionId) : null;
        long durationMs = startTime != null ? (System.currentTimeMillis() - startTime) : -1;

        log.info("[WS-EVENT] STOMP session disconnected - sessionId={} user={} duration={}ms closeStatus={}",
                sessionId, LoggingUtils.maskEmail(user), durationMs, event.getCloseStatus());
    }
}
