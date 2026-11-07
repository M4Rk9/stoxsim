package com.stoxsim.billing;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.Date;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.server.ResponseStatusException;

class PlayNotificationVerifierTest {
    private static final String AUD="https://api.stoxsim.test/api/v1/billing/play/notifications",EMAIL="push@fixture.iam.gserviceaccount.com";
    private final BillingConfig config=new BillingConfig(true,false,"","","","","",true,"com.stoxsim.app","plus","pro","monthly","monthly","/fixture",
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",AUD,EMAIL);
    private final RSAKey key;private final PlayNotificationVerifier verifier;
    PlayNotificationVerifierTest()throws Exception {key=new RSAKeyGenerator(2048).generate();verifier=new PlayNotificationVerifier(config,NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build());}
    String token(String issuer,String audience,String email,boolean verified,Instant issued,Instant expires)throws Exception{
        var claims=new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject("fixture-push-service").issueTime(Date.from(issued)).expirationTime(Date.from(expires)).claim("email",email).claim("email_verified",verified).build();
        SignedJWT jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.RS256),claims);jwt.sign(new RSASSASigner(key));return "Bearer "+jwt.serialize();}
    void rejected(String token){assertThatThrownBy(()->verifier.verify(token)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(401));}
    @Test void acceptsValidSignedGooglePushIdentity()throws Exception{verifier.verify(token("https://accounts.google.com",AUD,EMAIL,true,Instant.now().minusSeconds(2),Instant.now().plusSeconds(300)));}
    @Test void rejectsIssuerAudienceEmailAndUnverifiedEmail()throws Exception{
        var now=Instant.now();rejected(token("https://attacker.test",AUD,EMAIL,true,now,now.plusSeconds(300)));rejected(token("https://accounts.google.com","wrong",EMAIL,true,now,now.plusSeconds(300)));
        rejected(token("https://accounts.google.com",AUD,"other@fixture.iam.gserviceaccount.com",true,now,now.plusSeconds(300)));rejected(token("https://accounts.google.com",AUD,EMAIL,false,now,now.plusSeconds(300)));}
    @Test void rejectsExpiredFutureAndForgedTokens()throws Exception{var now=Instant.now();rejected(token("https://accounts.google.com",AUD,EMAIL,true,now.minusSeconds(600),now.minusSeconds(1)));rejected(token("https://accounts.google.com",AUD,EMAIL,true,now.plusSeconds(90),now.plusSeconds(300)));rejected("Bearer invalid");rejected(null);}
    @Test void rejectsSignatureFromAnotherSigningKey()throws Exception{var wrong=new PlayNotificationVerifier(config,NimbusJwtDecoder.withPublicKey(new RSAKeyGenerator(2048).generate().toRSAPublicKey()).build());assertThatThrownBy(()->wrong.verify(token("https://accounts.google.com",AUD,EMAIL,true,Instant.now(),Instant.now().plusSeconds(300)))).isInstanceOf(ResponseStatusException.class);}
    @Test void encryptedPurchaseTokensDoNotRevealContentAndRejectTampering(){var vault=new PurchaseTokenVault(config);String cipher=vault.encrypt("private-purchase-token");assertThat(cipher).doesNotContain("private-purchase-token");assertThat(vault.decrypt(cipher)).isEqualTo("private-purchase-token");assertThatThrownBy(()->vault.decrypt("AAAAAAAAAAAAAAAA:"+cipher.split(":")[1])).isInstanceOf(IllegalStateException.class);assertThat(vault.hash("private-purchase-token")).hasSize(64);}
    @Test void configurationRequiresExplicitApprovalAndLiveKeys(){assertThatThrownBy(()->new BillingConfig(false,true,"rzp_live_fixture","secret","a".repeat(32),"plan_plus","plan_pro",false,"com.stoxsim.app","","","","","","","","")).isInstanceOf(IllegalStateException.class);assertThatThrownBy(()->new BillingConfig(true,true,"rzp_test_fixture","secret","a".repeat(32),"plan_plus","plan_pro",false,"com.stoxsim.app","","","","","","","","")).isInstanceOf(IllegalStateException.class);}
}
