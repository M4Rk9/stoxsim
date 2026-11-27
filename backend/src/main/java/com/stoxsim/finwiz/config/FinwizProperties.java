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
    private int attemptTimeoutSeconds = 8;
    private int deadlineSeconds = 25;
    private int providerConcurrency = 4;
    private int plusCapacityPercent = 25;
    private int proCapacityPercent = 25;
    public int getPlusCapacityPercent() { return plusCapacityPercent; }
    public void setPlusCapacityPercent(int value) { plusCapacityPercent = value; }
    public int getProCapacityPercent() { return proCapacityPercent; }
    public void setProCapacityPercent(int value) { proCapacityPercent = value; }

    private boolean geminiFreeCapacityConfirmed;
    public boolean isGeminiFreeCapacityConfirmed() { return geminiFreeCapacityConfirmed; }
    public void setGeminiFreeCapacityConfirmed(boolean confirmed) { geminiFreeCapacityConfirmed = confirmed; }

    @jakarta.annotation.PostConstruct
    void validateCapacity() {
        if (plusCapacityPercent < 0 || proCapacityPercent < 0 || (long) plusCapacityPercent + proCapacityPercent > 100)
            throw new IllegalStateException("Finwiz capacity shares must be nonnegative and total at most 100");
    }

    private int geminiDailyLimit = 500;
    private int geminiRequestsPerMinute = 10;
    private int geminiTokensPerMinute = 40000;
    private int geminiTokensPerDay = 1000000;
    public int getGeminiRequestsPerMinute() { return geminiRequestsPerMinute; }
    public void setGeminiRequestsPerMinute(int value) { geminiRequestsPerMinute=value; }
    public int getGeminiTokensPerMinute() { return geminiTokensPerMinute; }
    public void setGeminiTokensPerMinute(int value) { geminiTokensPerMinute=value; }
    public int getGeminiTokensPerDay() { return geminiTokensPerDay; }
    public void setGeminiTokensPerDay(int value) { geminiTokensPerDay=value; }
    private final Provider groq = new Provider("https://api.groq.com/openai/v1", "openai/gpt-oss-20b", 100);
    private final Provider cloudflare = new Provider("https://api.cloudflare.com/client/v4", "@cf/openai/gpt-oss-20b", 50);
    private final Provider openrouter = new Provider("https://openrouter.ai/api/v1", "", 30);
    private String cloudflareAccountId = "";
    private String openrouterUpstream = "";

    public static class Provider {
        private boolean enabled;
        private boolean freeCapacityConfirmed;
        private String apiKey;
        private String baseUrl;
        private String model;
        private int dailyLimit;
        private int requestsPerMinute=5;
        private int tokensPerMinute=8000;
        private int tokensPerDay=180000;
        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int value) { requestsPerMinute=value; }
        public int getTokensPerMinute() { return tokensPerMinute; }
        public void setTokensPerMinute(int value) { tokensPerMinute=value; }
        public int getTokensPerDay() { return tokensPerDay; }
        public void setTokensPerDay(int value) { tokensPerDay=value; }
        public Provider(String baseUrl, String model, int dailyLimit) { this.baseUrl=baseUrl; this.model=model; this.dailyLimit=dailyLimit; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled=value; }
        public boolean isFreeCapacityConfirmed() { return freeCapacityConfirmed; }
        public void setFreeCapacityConfirmed(boolean value) { freeCapacityConfirmed=value; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String value) { apiKey=value; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String value) { baseUrl=value; }
        public String getModel() { return model; }
        public void setModel(String value) { model=value; }
        public int getDailyLimit() { return dailyLimit; }
        public void setDailyLimit(int value) { dailyLimit=value; }
        public boolean configured() { return enabled && freeCapacityConfirmed && apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank() && dailyLimit > 0; }
    }
    public Provider getGroq() { return groq; }
    public Provider getCloudflare() { return cloudflare; }
    public Provider getOpenrouter() { return openrouter; }
    public String getCloudflareAccountId() { return cloudflareAccountId; }
    public void setCloudflareAccountId(String value) { cloudflareAccountId=value; }
    public String getOpenrouterUpstream() { return openrouterUpstream; }
    public void setOpenrouterUpstream(String value) { openrouterUpstream=value; }
    public int getAttemptTimeoutSeconds() { return attemptTimeoutSeconds; }
    public void setAttemptTimeoutSeconds(int value) { attemptTimeoutSeconds=value; }
    public int getDeadlineSeconds() { return deadlineSeconds; }
    public void setDeadlineSeconds(int value) { deadlineSeconds=value; }
    public int getProviderConcurrency() { return providerConcurrency; }
    public void setProviderConcurrency(int value) { providerConcurrency=value; }
    public int getGeminiDailyLimit() { return geminiDailyLimit; }
    public void setGeminiDailyLimit(int value) { geminiDailyLimit=value; }

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
