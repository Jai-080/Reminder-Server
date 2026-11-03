package com.reminder.server.websocket;

import com.reminder.server.security.JwtService;
import com.reminder.server.util.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new HttpHandshakeInterceptor());
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new HttpHandshakeInterceptor())
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null) {
                    StompCommand command = accessor.getCommand();
                    log.info("[WebSocketConfig] Intercepted STOMP command: {}", command);
                    if (StompCommand.CONNECT.equals(command)) {
                        String token = null;

                        // 1. Try native Authorization header (sanitized logging)
                        String authHeader = accessor.getFirstNativeHeader("Authorization");
                        log.info("[WebSocketConfig] CONNECT Native Authorization header present: {}", (authHeader != null));
                        if (authHeader != null && authHeader.startsWith("Bearer ")) {
                            token = authHeader.substring(7);
                        }

                        // 2. Try native passcode header (fallback)
                        if (token == null || token.isEmpty()) {
                            token = accessor.getPasscode();
                            log.info("[WebSocketConfig] CONNECT Passcode fallback token: {}", (token != null ? "present" : "null"));
                        }

                        // 3. Try query parameter extracted from handshake (fallback)
                        if (token == null || token.isEmpty()) {
                            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                            if (sessionAttributes != null) {
                                token = (String) sessionAttributes.get("token");
                                log.info("[WebSocketConfig] CONNECT SessionAttributes token: {}", (token != null ? "present" : "null"));
                            }
                        }

                        log.info("[WebSocketConfig] CONNECT Token present: {}", (token != null && !token.trim().isEmpty()));

                        if (token != null && !token.trim().isEmpty()) {
                            try {
                                String username = jwtService.extractUsername(token);
                                log.info("[WebSocketConfig] CONNECT Extracted username: {}", LoggingUtils.maskEmail(username));
                                if (username != null) {
                                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                                    boolean isValid = jwtService.isTokenValid(token, userDetails);
                                    log.info("[WebSocketConfig] CONNECT Is token valid: {}", isValid);
                                    if (isValid) {
                                        UsernamePasswordAuthenticationToken authentication =
                                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                                        accessor.setUser(authentication);
                                        log.info("[WebSocketConfig] CONNECT User authenticated successfully as: {}", LoggingUtils.maskEmail(username));
                                    } else {
                                        log.warn("[WebSocketConfig] CONNECT Token is invalid for user: {}", LoggingUtils.maskEmail(username));
                                        throw new MessageDeliveryException("Token is invalid/expired");
                                    }
                                } else {
                                    throw new MessageDeliveryException("Invalid token payload");
                                }
                            } catch (Exception e) {
                                log.error("[WebSocketConfig] CONNECT Authentication failed: {}", e.getMessage());
                                if (e instanceof MessageDeliveryException) {
                                    throw (MessageDeliveryException) e;
                                }
                                throw new MessageDeliveryException("Authentication failed: " + e.getMessage());
                            }
                        } else {
                            log.warn("[WebSocketConfig] CONNECT No token found in any fallback location");
                            throw new MessageDeliveryException("Missing token");
                        }
                    } else if (StompCommand.SUBSCRIBE.equals(command)) {
                        log.info("[WebSocketConfig] SUBSCRIBE Destination: {}", LoggingUtils.maskDestination(accessor.getDestination()));
                        log.info("[WebSocketConfig] SUBSCRIBE User Principal: {}", LoggingUtils.maskEmail(accessor.getUser() != null ? accessor.getUser().getName() : "anonymous"));
                        if (accessor.getUser() == null) {
                            log.warn("[WebSocketConfig] Rejecting unauthorized SUBSCRIBE request");
                            throw new MessageDeliveryException("Unauthorized subscription: principal is missing");
                        }
                    }
                }
                return message;
            }
        });
    }

    private static class HttpHandshakeInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
            log.info("[HttpHandshakeInterceptor] beforeHandshake entered");
            if (request instanceof ServletServerHttpRequest) {
                ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
                String token = servletRequest.getServletRequest().getParameter("token");
                log.info("[HttpHandshakeInterceptor] Extracted query param token present: {}", (token != null && !token.trim().isEmpty()));
                if (token != null) {
                    attributes.put("token", token);
                }
            } else {
                log.warn("[HttpHandshakeInterceptor] Request is not ServletServerHttpRequest: {}", request.getClass().getName());
            }
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }
    }
}
