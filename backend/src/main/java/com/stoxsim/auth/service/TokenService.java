package com.stoxsim.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.stoxsim.auth.config.AuthProperties;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.domain.RefreshToken;
import com.stoxsim.auth.repository.RefreshTokenRepository;

@Service
public class TokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_USER_AGENT_LENGTH = 200;

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthProperties properties;

    public TokenService(
        JwtEncoder jwtEncoder,
        RefreshTokenRepository refreshTokenRepository,
        AuthProperties properties
    ) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.properties = properties;
    }

    public TokenPair issueTokenPair(AppUser user) {
        return issueTokenPair(user, "Unknown device");
    }

    public TokenPair issueTokenPair(AppUser user, String userAgent) {
        Instant now = Instant.now();
        return createTokenPair(user, UUID.randomUUID(), now, userAgent, now);
    }

    public TokenPair rotateTokenPair(
        AppUser user,
        RefreshToken previous,
        String userAgent
    ) {
        Instant now = Instant.now();
        String resolvedAgent = StringUtils.hasText(userAgent)
            ? userAgent
            : previous.getUserAgent();
        return createTokenPair(
            user,
            previous.getSessionId(),
            previous.getSessionStartedAt(),
            resolvedAgent,
            now
        );
    }

    private TokenPair createTokenPair(
        AppUser user,
        UUID sessionId,
        Instant sessionStartedAt,
        String userAgent,
        Instant issuedAt
    ) {
        Instant accessExpiry = issuedAt.plus(Duration.ofMinutes(properties.getAccessTokenMinutes()));

        var claims = JwtClaimsSet.builder()
            .issuer("stoxsim")
            .subject(user.getId().toString())
            .issuedAt(issuedAt)
            .expiresAt(accessExpiry)
            .claim("email", user.getEmail())
            .claim("displayName", user.getDisplayName())
            .claim("emailVerified", user.isEmailVerified())
            .build();

        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        String accessToken = jwtEncoder
            .encode(JwtEncoderParameters.from(header, claims))
            .getTokenValue();

        String rawRefreshToken = generateRefreshToken();
        Instant refreshExpiry = issuedAt.plus(Duration.ofDays(properties.getRefreshTokenDays()));
        refreshTokenRepository.save(new RefreshToken(
            user,
            hash(rawRefreshToken),
            refreshExpiry,
            sessionId,
            sessionStartedAt,
            sanitizeUserAgent(userAgent)
        ));

        return new TokenPair(
            accessToken,
            rawRefreshToken,
            Duration.between(issuedAt, accessExpiry).toSeconds()
        );
    }

    public String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sanitizeUserAgent(String userAgent) {
        String value = StringUtils.hasText(userAgent) ? userAgent.trim() : "Unknown device";
        return value.substring(0, Math.min(value.length(), MAX_USER_AGENT_LENGTH));
    }

    public record TokenPair(
        String accessToken,
        String refreshToken,
        long expiresInSeconds
    ) {
    }
}
