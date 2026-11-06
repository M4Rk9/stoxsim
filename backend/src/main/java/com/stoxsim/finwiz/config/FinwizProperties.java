package com.stoxsim.finwiz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stoxsim.finwiz")
public class FinwizProperties {

    private boolean enabled = true;
    private String apiKey;
    private String baseUrl = "https://generativelanguage.googleapis.com";
    private String model = "gemini-3.6-flash";
    private int maxQuestionCharacters = 2000;
    private int maxOutputTokens = 900;
    private String thinkingLevel = "minimal";
    private int basicDailyProviderRequests = 10;
    private int expandedDailyProviderRequests = 30;
    private int fullDailyProviderRequests = 60;
    private int globalDailyProviderRequests = 500;

    public int getBasicDailyProviderRequests() { return basicDailyProviderRequests; }
    public void setBasicDailyProviderRequests(int value) { basicDailyProviderRequests = value; }
    public int getExpandedDailyProviderRequests() { return expandedDailyProviderRequests; }
    public void setExpandedDailyProviderRequests(int value) { expandedDailyProviderRequests = value; }
    public int getFullDailyProviderRequests() { return fullDailyProviderRequests; }
    public void setFullDailyProviderRequests(int value) { fullDailyProviderRequests = value; }
    public int getGlobalDailyProviderRequests() { return globalDailyProviderRequests; }
    public void setGlobalDailyProviderRequests(int value) { globalDailyProviderRequests = value; }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getMaxQuestionCharacters() {
        return maxQuestionCharacters;
    }

    public void setMaxQuestionCharacters(int maxQuestionCharacters) {
        this.maxQuestionCharacters = maxQuestionCharacters;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public String getThinkingLevel() {
        return thinkingLevel;
    }

    public void setThinkingLevel(String thinkingLevel) {
        this.thinkingLevel = thinkingLevel;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
