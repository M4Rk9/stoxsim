package com.stoxsim.auth.google;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.stoxsim.common.error.UnauthorizedException;

@Service
@ConditionalOnProperty(name = "stoxsim.auth.google.enabled", havingValue = "true")
public class GoogleLoginChallenge {
    public static final String COOKIE = "stoxsim_google_challenge";
    public static final Duration TTL = Duration.ofMinutes(5);
    private final SecureRandom random = new SecureRandom();
    private final StringRedisTemplate redis;
    public GoogleLoginChallenge(StringRedisTemplate redis) { this.redis = redis; }

    public Challenge create() { return create("web"); }
    public Challenge createNative() { return create("native"); }
    private Challenge create(String channel) {
        String binding = randomValue();
        String nonce = randomValue();
        redis.opsForValue().set(key(channel, binding), nonce, TTL);
        return new Challenge(binding, nonce);
    }

    public String consume(String binding) { return consume("web", binding); }
    public String consumeNative(String binding) { return consume("native", binding); }
    private String consume(String channel, String binding) {
        if (binding == null || !binding.matches("[A-Za-z0-9_-]{43}")) throw expired();
        // GETDEL is atomic: a credential cannot be replayed by concurrent requests.
        String nonce = redis.opsForValue().getAndDelete(key(channel, binding));
        if (nonce == null) throw expired();
        return nonce;
    }

    private String key(String channel, String binding) { return "stoxsim:google-challenge:" + channel + ":" + binding; }
    private UnauthorizedException expired() { return new UnauthorizedException("Google sign-in expired. Please try again."); }
    private String randomValue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public record Challenge(String binding, String nonce) {}
}
