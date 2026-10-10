package com.stoxsim.market.websocket;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;

@Component
public class MarketWebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final MarketWebSocketQuota quota;
    private final ConcurrentHashMap<String, Jwt> connected = new ConcurrentHashMap<>();

    public MarketWebSocketAuthInterceptor(JwtDecoder jwtDecoder, MarketWebSocketQuota quota) {
        this.jwtDecoder = jwtDecoder;
        this.quota = quota;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        String id = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        try {
            return process(message, channel);
        } catch (MessagingException invalid) {
            if (id != null) {
                connected.remove(id);
                quota.terminate(id, CloseStatus.POLICY_VIOLATION, "stomp_policy");
            }
            throw invalid;
        }
    }

    private Message<?> process(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
            message,
            StompHeaderAccessor.class
        );
        if (accessor == null) throw new MessagingException("Invalid market-stream frame");
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.DISCONNECT) {
            if (accessor.getSessionId() != null) connected.remove(accessor.getSessionId());
            return message;
        }
        // A single WebSocket message can contain multiple STOMP frames.
        if (!quota.stompFrame(accessor.getSessionId())) throw new MessagingException("Market-stream frame quota exceeded");
        // This is a read-only stream. Clients must never impersonate the broadcaster.
        if (command == StompCommand.SEND) throw new MessagingException("Market streaming is read-only");
        if (command != StompCommand.CONNECT) {
            if (command == StompCommand.SUBSCRIBE
                && !MarketTickBroadcaster.QUOTE_TOPIC.equals(accessor.getDestination())) {
                throw new MessagingException("Only the market quote topic may be subscribed to");
            }
            if (command != StompCommand.SUBSCRIBE && command != StompCommand.UNSUBSCRIBE
                && accessor.getMessageType() != SimpMessageType.HEARTBEAT) {
                throw new MessagingException("Unsupported market-stream frame");
            }
            if (!(accessor.getUser() instanceof JwtAuthenticationToken authentication)
                || !authentication.isAuthenticated()) {
                throw new MessagingException("An authenticated market-stream session is required");
            }
            validate(authentication.getToken().getTokenValue());
            if (command == StompCommand.SUBSCRIBE && !quota.subscribe(accessor.getSessionId(), accessor.getSubscriptionId())) {
                throw new MessagingException("Only one quote subscription is allowed per connection");
            }
            if (command == StompCommand.UNSUBSCRIBE) quota.unsubscribe(accessor.getSessionId(), accessor.getSubscriptionId());
            return message;
        }

        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)
            || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new MessagingException("A bearer token is required for market streaming");
        }

        String tokenValue = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(tokenValue)) {
            throw new MessagingException("A bearer token is required for market streaming");
        }

        try {
            Jwt jwt = validate(tokenValue);
            if (accessor.getSessionId() == null) throw new MessagingException("Market-stream session is required");
            if (!quota.authenticate(accessor.getSessionId(), jwt.getSubject(), jwt.getExpiresAt())) {
                throw new MessagingException("Market-stream account connection quota exceeded");
            }
            connected.put(accessor.getSessionId(), jwt);
            accessor.setUser(new JwtAuthenticationToken(
                jwt,
                AuthorityUtils.NO_AUTHORITIES,
                jwt.getSubject()
            ));
            return message;
        } catch (JwtException exception) {
            throw new MessagingException("The market-stream token is invalid or expired", exception);
        }
    }
    private Jwt validate(String value) {
        try { return jwtDecoder.decode(value); }
        catch (JwtException failure) { throw new MessagingException("The market-stream session is invalid or expired"); }
    }

    /** Recheck before returning a quote, including to an already-open subscription. */
    public ChannelInterceptor outbound() {
        return new ChannelInterceptor() {
            @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
                if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) return message;
                String id = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
                Jwt jwt = id == null ? null : connected.get(id);
                if (jwt == null) return null;
                try { jwtDecoder.decode(jwt.getTokenValue()); return message; }
                catch (JwtException invalid) {
                    connected.remove(id);
                    quota.terminate(id, CloseStatus.POLICY_VIOLATION, "revoked_session");
                    return null;
                }
            }
        };
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) { connected.remove(event.getSessionId()); }

}
