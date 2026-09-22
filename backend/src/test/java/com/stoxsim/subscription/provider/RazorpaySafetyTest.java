package com.stoxsim.subscription.provider;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class RazorpaySafetyTest {
    @Test void disabledNeedsNoCredentialsButEnabledRejectsLiveKeys() {
        assertThatCode(()->new RazorpayTestConfig(false,"","","","","")).doesNotThrowAnyException();
        assertThatThrownBy(()->new RazorpayTestConfig(true,"rzp_live_example","secret","x".repeat(32),"plan_plus","plan_pro"))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new RazorpayTestConfig(true,"rzp_test_example","secret","short","plan_plus","plan_pro"))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new RazorpayTestConfig(true,"rzp_test_example","secret","x".repeat(32),"plan_same","plan_same"))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test void signatureCoversExactBytesAndRejectsMalformedOrDifferentSecrets() throws Exception {
        byte[] raw="{\"event\": \"subscription.charged\"}".getBytes(StandardCharsets.UTF_8);
        Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String signature=HexFormat.of().formatHex(mac.doFinal(raw));
        assertThat(RazorpaySignature.valid(raw,signature,"test-secret")).isTrue();
        assertThat(RazorpaySignature.valid(raw,signature,"another-secret")).isFalse();
        assertThat(RazorpaySignature.valid("{}".getBytes(StandardCharsets.UTF_8),signature,"test-secret")).isFalse();
        assertThat(RazorpaySignature.valid(raw,null,"test-secret")).isFalse();
        assertThat(RazorpaySignature.valid(raw,"not-hex","test-secret")).isFalse();
    }
}
