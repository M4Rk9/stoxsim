package com.stoxsim.auth.google;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.stoxsim.common.error.UnauthorizedException;

class GoogleLoginChallengeTest {
    @Test @SuppressWarnings("unchecked") void usesExpiringRandomNonceAndAtomicSingleUseConsumption() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var service = new GoogleLoginChallenge(redis);
        var challenge = service.create();
        assertTrue(challenge.binding().matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(challenge.binding(), challenge.nonce());
        verify(values).set("stoxsim:google-challenge:web:" + challenge.binding(), challenge.nonce(), GoogleLoginChallenge.TTL);
        when(values.getAndDelete(anyString())).thenReturn(challenge.nonce(), (String) null);
        assertEquals(challenge.nonce(), service.consume(challenge.binding()));
        assertThrows(UnauthorizedException.class, () -> service.consume(challenge.binding()));
        assertThrows(UnauthorizedException.class, () -> service.consume(null));
        assertThrows(UnauthorizedException.class, () -> service.consume("attacker"));
    }
    @Test @SuppressWarnings("unchecked") void nativeAndBrowserChallengesCannotBeInterchanged() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var service = new GoogleLoginChallenge(redis);
        var challenge = service.createNative();
        verify(values).set("stoxsim:google-challenge:native:" + challenge.binding(), challenge.nonce(), GoogleLoginChallenge.TTL);
        when(values.getAndDelete("stoxsim:google-challenge:native:" + challenge.binding())).thenReturn(challenge.nonce(), (String) null);
        assertThrows(UnauthorizedException.class, () -> service.consume(challenge.binding()));
        assertEquals(challenge.nonce(), service.consumeNative(challenge.binding()));
        assertThrows(UnauthorizedException.class, () -> service.consumeNative(challenge.binding()));
    }
    @Test void storageFailureCannotIssueAChallenge() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("offline"));
        assertThrows(IllegalStateException.class, () -> new GoogleLoginChallenge(redis).create());
    }
}
