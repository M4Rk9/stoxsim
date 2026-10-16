package com.stoxsim.finwiz.service;

import com.stoxsim.subscription.domain.SubscriptionPlan;

/** Server-owned educational depth; no request field can select a paid tier. */
public enum FinwizDepth {
    BASIC(350, 1800, 2, "Explain the core concept concisely, one simple example and the key limitation."),
    EXPANDED(650, 3600, 3, "Explain the concept, include a worked numerical example and explain common interpretation mistakes."),
    FULL(900, 6000, 4, "Include a worked example, compare interpretations or assumptions, explain sensitivity and limitations, and end with a practice exercise.");

    final int tokens;
    final int characters;
    final int suggestions;
    final String instruction;

    FinwizDepth(int tokens, int characters, int suggestions, String instruction) {
        this.tokens = tokens;
        this.characters = characters;
        this.suggestions = suggestions;
        this.instruction = instruction;
    }

    static FinwizDepth from(SubscriptionPlan plan) {
        return switch (plan) { case FREE -> BASIC; case PLUS -> EXPANDED; case PRO -> FULL; };
    }

    String bound(String answer) {
        if (answer.length() <= characters) return answer;
        String suffix = "\n\nAsk a focused follow-up to continue.";
        int end = characters - suffix.length();
        int boundary = answer.lastIndexOf(' ', end);
        if (boundary > end - 100) end = boundary;
        // Avoid splitting a Unicode surrogate pair.
        if (end > 0 && Character.isHighSurrogate(answer.charAt(end - 1))) end--;
        return answer.substring(0, end).stripTrailing() + suffix;
    }
}
