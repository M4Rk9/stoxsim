package com.stoxsim.finwiz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("stoxsim.finwiz.spend")
public record FinwizSpendProperties(boolean enabled, boolean approved, String apiKey, String model,
    long inputMicrosPerMillion, long outputMicrosPerMillion, long reasoningMicrosPerMillion,
    long dailyMicros, long monthlyMicros, long plusMonthlyMicros, long proMonthlyMicros,
    long userDailyMicros, int requestsPerMinute, int tokensPerMinute, int tokensPerDay, int dailyAttempts) {

    public void validate(String freeGeminiKey) {
        if (!enabled) return;
        if (!approved || apiKey == null || apiKey.isBlank() || apiKey.equals(freeGeminiKey)
            || model == null || !model.matches("[A-Za-z0-9._-]{1,128}")
            || !validPrice(inputMicrosPerMillion) || !validPrice(outputMicrosPerMillion) || !validPrice(reasoningMicrosPerMillion)
            || dailyMicros <= 0 || monthlyMicros < dailyMicros || userDailyMicros <= 0
            || plusMonthlyMicros <= 0 || proMonthlyMicros <= 0 || requestsPerMinute <= 0
            || tokensPerMinute <= 0 || tokensPerDay < tokensPerMinute || dailyAttempts <= 0) {
            throw new IllegalStateException("Paid Finwiz requires approval, a separate Gemini key, model pricing and positive budget and rate limits");
        }
    }

    private boolean validPrice(long value) {
        return value > 0 && value <= 999_999_999_999_999L;
    }

    @Override public String toString() {
        return "FinwizSpendProperties[enabled=" + enabled + "]";
    }
}
