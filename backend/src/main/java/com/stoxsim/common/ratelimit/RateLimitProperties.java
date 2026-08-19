package com.stoxsim.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stoxsim.security.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int generalPerMinute = 300;
    private int authPerMinute = 20;
    private int refreshPerMinute = 60;
    private int finwizPerMinute = 15;
    private int writesPerMinute = 120;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getGeneralPerMinute() {
        return generalPerMinute;
    }

    public void setGeneralPerMinute(int generalPerMinute) {
        this.generalPerMinute = generalPerMinute;
    }

    public int getAuthPerMinute() {
        return authPerMinute;
    }

    public void setAuthPerMinute(int authPerMinute) {
        this.authPerMinute = authPerMinute;
    }

    public int getRefreshPerMinute() {
        return refreshPerMinute;
    }

    public void setRefreshPerMinute(int refreshPerMinute) {
        this.refreshPerMinute = refreshPerMinute;
    }

    public int getFinwizPerMinute() {
        return finwizPerMinute;
    }

    public void setFinwizPerMinute(int finwizPerMinute) {
        this.finwizPerMinute = finwizPerMinute;
    }

    public int getWritesPerMinute() {
        return writesPerMinute;
    }

    public void setWritesPerMinute(int writesPerMinute) {
        this.writesPerMinute = writesPerMinute;
    }
}
