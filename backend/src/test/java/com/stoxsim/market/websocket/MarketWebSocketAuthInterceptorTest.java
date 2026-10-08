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

    @Test
    void rejectsClientPublishingAndWildcardAndAnonymousSubscriptions() {
        for (var command : new StompCommand[] {StompCommand.SEND, StompCommand.SUBSCRIBE}) {
            var headers = StompHeaderAccessor.create(command);
            headers.setSessionId("socket-1");
            headers.setDestination(command == StompCommand.SEND ? MarketTickBroadcaster.QUOTE_TOPIC : "/topic/**");
            assertThrows(MessagingException.class, () -> interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), channel));
        }
        var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination(MarketTickBroadcaster.QUOTE_TOPIC);
        assertThrows(MessagingException.class, () -> interceptor.preSend(
            MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), channel));
    }

    @Test
    void revocationBlocksExistingSubscriptionsAndQuoteDelivery() {
        Jwt jwt = Jwt.withTokenValue("valid-token").header("alg", "HS256").subject("user-123")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build();
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);
        var connection = interceptor.preSend(connectMessage("Bearer valid-token"), channel);
        var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setSessionId("socket-1");
        headers.setDestination(MarketTickBroadcaster.QUOTE_TOPIC);
        headers.setUser(StompHeaderAccessor.wrap(connection).getUser());
        var subscription = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        org.junit.jupiter.api.Assertions.assertNotNull(interceptor.preSend(subscription, channel));
        var outgoing = org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(
            org.springframework.messaging.simp.SimpMessageType.MESSAGE);
        outgoing.setSessionId("socket-1");
        var quote = MessageBuilder.createMessage(new byte[0], outgoing.getMessageHeaders());
        org.junit.jupiter.api.Assertions.assertNotNull(interceptor.outbound().preSend(quote, channel));
        when(jwtDecoder.decode("valid-token")).thenThrow(new org.springframework.security.oauth2.jwt.JwtException("revoked"));
        assertThrows(MessagingException.class, () -> interceptor.preSend(subscription, channel));
        org.junit.jupiter.api.Assertions.assertNull(interceptor.outbound().preSend(quote, channel));
    }

    private Message<byte[]> connectMessage(String authorization) {
        var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("socket-1");
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
