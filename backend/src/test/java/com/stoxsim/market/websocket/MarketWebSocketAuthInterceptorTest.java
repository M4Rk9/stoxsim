package com.stoxsim.market.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class MarketWebSocketAuthInterceptorTest {

    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    private final MarketWebSocketAuthInterceptor interceptor =
        new MarketWebSocketAuthInterceptor(jwtDecoder);
    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void authenticatesAConnectFrameWithAValidBearerToken() {
        Jwt jwt = Jwt.withTokenValue("valid-token")
            .header("alg", "HS256")
            .subject("user-123")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(900))
            .build();
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

        Message<byte[]> result = cast(interceptor.preSend(
            connectMessage("Bearer valid-token"),
            channel
        ));

        var accessor = StompHeaderAccessor.wrap(result);
        var authentication = assertInstanceOf(
            JwtAuthenticationToken.class,
            accessor.getUser()
        );
        assertEquals("user-123", authentication.getName());
    }

    @Test
    void rejectsAConnectFrameWithoutABearerToken() {
        assertThrows(
            MessagingException.class,
            () -> interceptor.preSend(connectMessage(null), channel)
        );
    }

    private Message<byte[]> connectMessage(String authorization) {
        var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @SuppressWarnings("unchecked")
    private Message<byte[]> cast(Message<?> message) {
        return (Message<byte[]>) message;
    }
}
