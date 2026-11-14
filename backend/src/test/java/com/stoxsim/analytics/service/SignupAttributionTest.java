package com.stoxsim.analytics.service;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class SignupAttributionTest {
    @Test void categoriesNeverRetainRawInput() {
        assertThat(SignupAttribution.source("https://example.test/private?email=x")).isEqualTo("UNKNOWN");
        assertThat(SignupAttribution.source(null)).isEqualTo("UNKNOWN");
        assertThat(SignupAttribution.source("SOCIAL")).isEqualTo("SOCIAL");
        assertThat(SignupAttribution.device("StoxSim-Android/0.6.0")).isEqualTo("ANDROID_APP");
        assertThat(SignupAttribution.device("Mozilla/5.0 (Linux; Android 15) Mobile")).isEqualTo("MOBILE_WEB");
        assertThat(SignupAttribution.device("Mozilla/5.0 (Windows NT 10.0)")).isEqualTo("DESKTOP_WEB");
        assertThat(SignupAttribution.device("unknown raw device")).isEqualTo("UNKNOWN");
    }
}
