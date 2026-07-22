package com.stoxsim.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import com.stoxsim.auth.config.AuthProperties;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.domain.RefreshToken;
import com.stoxsim.auth.repository.RefreshTokenRepository;

@Service
public class TokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
        Instant issuedAt = Instant.now();
        Instant accessExpiry = issuedAt.plus(Duration.ofMinutes(properties.getAccessTokenMinutes()));

        var claims = JwtClaimsSet.builder()
            .issuer("stoxsim")
            .subject(user.getId().toString())
            .issuedAt(issuedAt)
            .expiresAt(accessExpiry)
            .claim("email", user.getEmail())
            .claim("displayName", user.getDisplayName())
            .build();

        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        String accessToken = jwtEncoder
            .encode(JwtEncoderParameters.from(header, claims))
            .getTokenValue();

        String rawRefreshToken = generateRefreshToken();
        String tokenHash = hash(rawRefreshToken);
        Instant refreshExpiry = issuedAt.plus(Duration.ofDays(properties.getRefreshTokenDays()));
        refreshTokenRepository.save(new RefreshToken(user, tokenHash, refreshExpiry));

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

    public record TokenPair(
        String accessToken,
        String refreshToken,
        long expiresInSeconds
    ) {
    }
}
