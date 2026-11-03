package com.reminder.server.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.reminder.server.config.RequestLoggingFilter;
import com.reminder.server.websocket.SyncEvent;
import com.reminder.server.websocket.WebSocketEventListener;
import com.reminder.server.websocket.WebSocketEventPublisher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class LoggingPrivacyTest {

    private ListAppender<ILoggingEvent> publisherAppender;
    private ListAppender<ILoggingEvent> listenerAppender;
    private ListAppender<ILoggingEvent> filterAppender;

    private Logger publisherLogger;
    private Logger listenerLogger;
    private Logger filterLogger;

    @BeforeEach
    void setUp() {
        publisherLogger = (Logger) LoggerFactory.getLogger(WebSocketEventPublisher.class);
        publisherAppender = new ListAppender<>();
        publisherAppender.start();
        publisherLogger.addAppender(publisherAppender);

        listenerLogger = (Logger) LoggerFactory.getLogger(WebSocketEventListener.class);
        listenerAppender = new ListAppender<>();
        listenerAppender.start();
        listenerLogger.addAppender(listenerAppender);

        filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        filterAppender = new ListAppender<>();
        filterAppender.start();
        filterLogger.addAppender(filterAppender);

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        if (publisherLogger != null && publisherAppender != null) {
            publisherLogger.detachAppender(publisherAppender);
        }
        if (listenerLogger != null && listenerAppender != null) {
            listenerLogger.detachAppender(listenerAppender);
        }
        if (filterLogger != null && filterAppender != null) {
            filterLogger.detachAppender(filterAppender);
        }
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("LoggingUtils.maskEmail masks normal emails, plain usernames, and corner cases")
    void testMaskEmailVariants() {
        // Standard emails
        assertEquals("de***@localdev.internal", LoggingUtils.maskEmail("dev_tester@localdev.internal"));
        assertEquals("jo***@example.com", LoggingUtils.maskEmail("john.doe@example.com"));
        assertEquals("al***@company.org", LoggingUtils.maskEmail("alice@company.org"));
        assertEquals("a***@example.com", LoggingUtils.maskEmail("a@example.com"));
        assertEquals("***@example.com", LoggingUtils.maskEmail("@example.com"));

        // Plain usernames without @
        assertEquals("ad***", LoggingUtils.maskEmail("admin"));
        assertEquals("te***", LoggingUtils.maskEmail("testuser"));
        assertEquals("***", LoggingUtils.maskEmail("ab"));
        assertEquals("***", LoggingUtils.maskEmail("a"));

        // Corner cases & anonymous
        assertEquals("[EMPTY]", LoggingUtils.maskEmail(null));
        assertEquals("[EMPTY]", LoggingUtils.maskEmail(""));
        assertEquals("[EMPTY]", LoggingUtils.maskEmail("   "));
        assertEquals("anonymous", LoggingUtils.maskEmail("anonymous"));
        assertEquals("anonymous", LoggingUtils.maskEmail("anonymousUser"));
    }

    @Test
    @DisplayName("LoggingUtils.maskDestination masks embedded emails in paths while preserving static paths")
    void testMaskDestinationVariants() {
        assertEquals("/user/de***@localdev.internal/topic/sync",
                LoggingUtils.maskDestination("/user/dev_tester@localdev.internal/topic/sync"));
        assertEquals("/user/jo***@example.com/queue/notifications",
                LoggingUtils.maskDestination("/user/john.doe@example.com/queue/notifications"));

        // Static or standard paths without email segments
        assertEquals("/user/topic/sync", LoggingUtils.maskDestination("/user/topic/sync"));
        assertEquals("/topic/sync", LoggingUtils.maskDestination("/topic/sync"));
        assertEquals("/app/chat", LoggingUtils.maskDestination("/app/chat"));

        // Null and empty
        assertEquals("", LoggingUtils.maskDestination(null));
        assertEquals("", LoggingUtils.maskDestination(""));
        assertEquals("", LoggingUtils.maskDestination("   "));
    }

    @Test
    @DisplayName("WebSocketEventPublisher never logs full email and preserves actual STOMP routing")
    void testWebSocketEventPublisherPrivacyAndRouting() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        WebSocketEventPublisher publisher = new WebSocketEventPublisher(messagingTemplate);

        String rawEmail = "sensitive_owner@company.com";
        Instant now = Instant.now();

        publisher.publish("PAYMENT", "PAYMENT_CREATED", 101L, rawEmail, now);

        // 1. Verify log content
        List<ILoggingEvent> logs = publisherAppender.list;
        assertFalse(logs.isEmpty(), "Logs should be emitted by publisher");

        for (ILoggingEvent event : logs) {
            String formattedMsg = event.getFormattedMessage();
            assertFalse(formattedMsg.contains(rawEmail),
                    "Log statement must not contain raw email: " + formattedMsg);
        }

        boolean foundMaskedUser = logs.stream()
                .anyMatch(e -> e.getFormattedMessage().contains("se***@company.com"));
        assertTrue(foundMaskedUser, "Logs must include masked email identifier");

        boolean foundMaskedDestination = logs.stream()
                .anyMatch(e -> e.getFormattedMessage().contains("/user/se***@company.com/topic/sync"));
        assertTrue(foundMaskedDestination, "Logs must include masked destination");

        // 2. Verify STOMP routing was UNTOUCHED (must use rawEmail)
        ArgumentCaptor<SyncEvent> eventCaptor = ArgumentCaptor.forClass(SyncEvent.class);
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq(rawEmail),
                eq("/topic/sync"),
                eventCaptor.capture()
        );

        SyncEvent captured = eventCaptor.getValue();
        assertEquals("PAYMENT", captured.getEntityType());
        assertEquals("PAYMENT_CREATED", captured.getOperation());
        assertEquals(101L, captured.getServerId());
        assertEquals(rawEmail, captured.getUsername());
    }

    @Test
    @DisplayName("WebSocketEventListener masks user in connection, subscription, and disconnection events")
    void testWebSocketEventListenerPrivacy() {
        WebSocketEventListener listener = new WebSocketEventListener();

        String rawUserEmail = "ws_user@domain.com";
        Principal mockPrincipal = () -> rawUserEmail;

        // 1. Session Connected
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
        connectAccessor.setSessionId("sess-001");
        connectAccessor.setUser(mockPrincipal);
        Message<byte[]> connectMsg = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());
        listener.handleSessionConnected(new SessionConnectedEvent(this, connectMsg));

        // 2. Session Subscribe
        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setSessionId("sess-001");
        subAccessor.setUser(mockPrincipal);
        subAccessor.setDestination("/user/" + rawUserEmail + "/topic/sync");
        Message<byte[]> subMsg = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());
        listener.handleSessionSubscribe(new SessionSubscribeEvent(this, subMsg));

        // 3. Session Disconnect
        StompHeaderAccessor discAccessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        discAccessor.setSessionId("sess-001");
        discAccessor.setUser(mockPrincipal);
        Message<byte[]> discMsg = MessageBuilder.createMessage(new byte[0], discAccessor.getMessageHeaders());
        listener.handleSessionDisconnect(new SessionDisconnectEvent(this, discMsg, "sess-001", CloseStatus.NORMAL));

        // Verification
        List<ILoggingEvent> logs = listenerAppender.list;
        assertFalse(logs.isEmpty(), "Listener should emit log events");

        for (ILoggingEvent event : logs) {
            String msg = event.getFormattedMessage();
            assertFalse(msg.contains(rawUserEmail),
                    "Listener log must not contain raw email: " + msg);
        }

        boolean hasMaskedUser = logs.stream()
                .anyMatch(e -> e.getFormattedMessage().contains("ws***@domain.com"));
        assertTrue(hasMaskedUser, "Listener log must contain masked email");

        boolean hasMaskedDest = logs.stream()
                .anyMatch(e -> e.getFormattedMessage().contains("/user/ws***@domain.com/topic/sync"));
        assertTrue(hasMaskedDest, "Listener subscription log must contain masked destination");
    }

    @Test
    @DisplayName("RequestLoggingFilter masks authenticated usernames and logs anonymous correctly")
    void testRequestLoggingFilterPrivacy() throws ServletException, IOException {
        RequestLoggingFilter filter = new RequestLoggingFilter();

        // 1. Authenticated request with raw email
        String authenticatedEmail = "alice.smith@securecorp.com";
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        authenticatedEmail,
                        "dummyCredentials",
                        java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {};

        filter.doFilter(request, response, chain);

        List<ILoggingEvent> logs = filterAppender.list;
        assertFalse(logs.isEmpty(), "Request filter must log request");

        ILoggingEvent authEvent = logs.get(0);
        String authMsg = authEvent.getFormattedMessage();
        assertFalse(authMsg.contains(authenticatedEmail),
                "Request log must not contain full authenticated email: " + authMsg);
        assertTrue(authMsg.contains("user=al***@securecorp.com"),
                "Request log must contain masked authenticated user: " + authMsg);

        // 2. Unauthenticated request
        SecurityContextHolder.clearContext();
        filterAppender.list.clear();

        MockHttpServletRequest anonRequest = new MockHttpServletRequest("GET", "/api/public/status");
        MockHttpServletResponse anonResponse = new MockHttpServletResponse();

        filter.doFilter(anonRequest, anonResponse, chain);

        assertFalse(filterAppender.list.isEmpty());
        String anonMsg = filterAppender.list.get(0).getFormattedMessage();
        assertTrue(anonMsg.contains("user=anonymous"),
                "Unauthenticated request must log user=anonymous: " + anonMsg);
    }
}
