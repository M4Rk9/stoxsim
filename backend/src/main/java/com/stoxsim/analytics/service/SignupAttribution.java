package com.stoxsim.analytics.service;

import java.util.Locale;
import java.util.Set;

/** Bounded descriptive categories only; never used to authorize a request. */
public final class SignupAttribution {
    private SignupAttribution() { }
    public static String source(String value) {
        return value != null && Set.of("DIRECT", "SEARCH", "SOCIAL", "REFERRAL").contains(value) ? value : "UNKNOWN";
    }
    public static String device(String agent) {
        if (agent == null || agent.isBlank()) return "UNKNOWN";
        String ua = agent.toLowerCase(Locale.ROOT);
        if (ua.startsWith("stoxsim-android/")) return "ANDROID_APP";
        if (!ua.contains("mozilla/")) return "UNKNOWN";
        return ua.contains("android") || ua.contains("iphone") || ua.contains("ipad") || ua.contains("mobile")
            ? "MOBILE_WEB" : "DESKTOP_WEB";
    }
}
