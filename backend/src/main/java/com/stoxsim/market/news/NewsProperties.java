package com.stoxsim.market.news;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Separate from quote permissions: enable only with headline display rights. */
@Component
@ConfigurationProperties(prefix = "stoxsim.news")
public class NewsProperties {
    private boolean upstoxEnabled;
    private boolean alpacaEnabled;
    public boolean isUpstoxEnabled() { return upstoxEnabled; }
    public void setUpstoxEnabled(boolean value) { upstoxEnabled = value; }
    public boolean isAlpacaEnabled() { return alpacaEnabled; }
    public void setAlpacaEnabled(boolean value) { alpacaEnabled = value; }
}
