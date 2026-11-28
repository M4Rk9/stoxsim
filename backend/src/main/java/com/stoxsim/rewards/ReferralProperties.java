package com.stoxsim.rewards;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stoxsim.rewards")
public class ReferralProperties {
    private boolean enabled;
    private String identityKey = "";
    private int minimumAgeHours = 24;
    private int qualificationDays = 90;
    private int dailyAttributionLimit = 20;
    private int weeklyRewardLimit = 5;

    @jakarta.annotation.PostConstruct
    void validate() {
        if (enabled && !retainsIdentityHolds())
            throw new IllegalStateException("Virtual rewards require an independent identity key of at least 32 characters");
        if (minimumAgeHours < 0 || minimumAgeHours > 720 || qualificationDays < 1 || qualificationDays > 180
            || minimumAgeHours >= qualificationDays * 24 || dailyAttributionLimit < 1
            || dailyAttributionLimit > 100 || weeklyRewardLimit < 1 || weeklyRewardLimit > 50)
            throw new IllegalStateException("Invalid referral qualification or issuance limits");
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    boolean retainsIdentityHolds() { return identityKey.length() >= 32; }
    String identityKey() { return identityKey; }
    public void setIdentityKey(String identityKey) { this.identityKey = identityKey; }
    public int getMinimumAgeHours() { return minimumAgeHours; }
    public void setMinimumAgeHours(int value) { minimumAgeHours = value; }
    public int getQualificationDays() { return qualificationDays; }
    public void setQualificationDays(int value) { qualificationDays = value; }
    public int getDailyAttributionLimit() { return dailyAttributionLimit; }
    public void setDailyAttributionLimit(int value) { dailyAttributionLimit = value; }
    public int getWeeklyRewardLimit() { return weeklyRewardLimit; }
    public void setWeeklyRewardLimit(int value) { weeklyRewardLimit = value; }
}
