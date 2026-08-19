package com.stoxsim.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;

import com.stoxsim.auth.config.AuthProperties;
import com.stoxsim.auth.service.TokenService;

class SecurityConfigTest {

    private SecurityConfig config;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret(
            "security-verification-test-secret-with-more-than-32-characters"
        );
        config = new SecurityConfig("https://stoxsim.test");
        key = config.jwtSecretKey(properties);
    }

    @Test
    void acceptsAnUnexpiredTokenFromTheStoxsimIssuer() {
        String token = encode(TokenService.ISSUER);

        assertEquals(
            TokenService.ISSUER,
            config.jwtDecoder(key).decode(token).getClaimAsString("iss")
        );
    }

    @Test
    void rejectsAnOtherwiseValidTokenFromAnotherIssuer() {
        String token = encode("not-stoxsim");

        assertThrows(
            JwtException.class,
            () -> config.jwtDecoder(key).decode(token)
        );
    }

    private String encode(String issuer) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject("00000000-0000-0000-0000-000000000001")
            .issuedAt(now.minusSeconds(1))
            .expiresAt(now.plusSeconds(300))
            .build();
        JwtEncoder encoder = config.jwtEncoder(key);
        return encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(),
            claims
        )).getTokenValue();
    }
}
