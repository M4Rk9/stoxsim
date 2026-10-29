package com.stoxsim.auth.google;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.stoxsim.common.error.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class GoogleIdentityVerifierTest {
    static final String CLIENT = "test.apps.googleusercontent.com";
    RSAKey key;
    GoogleIdentityVerifier verifier;
    @BeforeEach void setup() throws Exception {
        key = new RSAKeyGenerator(2048).generate();
        verifier = new GoogleIdentityVerifier(CLIENT, NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build());
    }
    JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer("https://accounts.google.com").subject("google-subject")
            .audience(CLIENT).issueTime(Date.from(Instant.now().minusSeconds(1)))
            .expirationTime(Date.from(Instant.now().plusSeconds(120))).claim("nonce", "challenge");
    }
    String sign(JWTClaimsSet.Builder claims, RSAKey signer) throws Exception {
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(signer));
        return jwt.serialize();
    }
    @Test void acceptsValidSignedIdentityAndBothDocumentedIssuers() throws Exception {
        for (String issuer : List.of("https://accounts.google.com", "accounts.google.com")) {
            assertEquals("google-subject", verifier.verify(sign(claims().issuer(issuer), key), "challenge"));
        }
    }
    @Test void rejectsWrongSignature() throws Exception {
        String token = sign(claims(), new RSAKeyGenerator(2048).generate());
        assertThrows(UnauthorizedException.class, () -> verifier.verify(token, "challenge"));
    }
    @Test void rejectsInvalidClaimsAndExpiredOrIncompleteTokens() throws Exception {
        for (var invalid : List.of(claims().issuer("https://attacker.test"), claims().audience("other-client"),
                claims().claim("azp", "other-client"), claims().audience(List.of(CLIENT, "other-client")),
                claims().subject(""), claims().subject(null), claims().expirationTime(null), claims().issueTime(null),
                claims().issueTime(Date.from(Instant.now().plusSeconds(600))),
                claims().expirationTime(Date.from(Instant.now().minusSeconds(120))))) {
            String token = sign(invalid, key);
            assertThrows(UnauthorizedException.class, () -> verifier.verify(token, "challenge"));
        }
    }
    @Test void rejectsMissingAndMismatchedNonceAndMalformedToken() throws Exception {
        String token = sign(claims(), key);
        assertThrows(UnauthorizedException.class, () -> verifier.verify(token, null));
        assertThrows(UnauthorizedException.class, () -> verifier.verify(token, "different"));
        String missing = sign(claims().claim("nonce", null), key);
        assertThrows(UnauthorizedException.class, () -> verifier.verify(missing, "challenge"));
        assertThrows(UnauthorizedException.class, () -> verifier.verify("not-a-jwt", "challenge"));
    }
    @Test void nativeClientsKeepWebAudienceAndCannotUseBrowserFlow() throws Exception {
        String android = "android.apps.googleusercontent.com";
        var nativeVerifier = new GoogleIdentityVerifier(CLIENT, List.of(android), NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build());
        String token = sign(claims().claim("azp", android), key);
        assertEquals("google-subject", nativeVerifier.identity(token, "challenge", true).subject());
        assertThrows(UnauthorizedException.class, () -> nativeVerifier.verify(token, "challenge"));
        assertThrows(UnauthorizedException.class, () -> verifier.identity(token, "challenge", true));
        String wrongAudience = sign(claims().audience(android).claim("azp", android), key);
        assertThrows(UnauthorizedException.class, () -> nativeVerifier.identity(wrongAudience, "challenge", true));
        String wrongParty = sign(claims().claim("azp", "other.apps.googleusercontent.com"), key);
        assertThrows(UnauthorizedException.class, () -> nativeVerifier.identity(wrongParty, "challenge", true));
    }
    @Test void onlyAuthoritativeVerifiedGoogleEmailCanProvisionPasswordlessAccount() throws Exception {
        assertTrue(verifier.identity(sign(claims().claim("email", "learner@gmail.com").claim("email_verified", true), key), "challenge", false).authoritativeEmail());
        assertTrue(verifier.identity(sign(claims().claim("email", "learner@school.test").claim("email_verified", true).claim("hd", "school.test"), key), "challenge", false).authoritativeEmail());
        assertFalse(verifier.identity(sign(claims().claim("email", "learner@thirdparty.test").claim("email_verified", true), key), "challenge", false).authoritativeEmail());
        assertFalse(verifier.identity(sign(claims().claim("email", "learner@gmail.com").claim("email_verified", false), key), "challenge", false).authoritativeEmail());
    }
    @Test void requiresConfiguredWebClientId() {
        assertThrows(IllegalStateException.class, () -> new GoogleIdentityVerifier(""));
    }
}
