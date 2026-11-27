package com.stoxsim.finwiz.service;

import com.stoxsim.subscription.domain.SubscriptionPlan;

/** Server-owned educational depth; no request field can select a paid tier. */
public enum FinwizDepth {
    BASIC(350, 1800, 3200, 2, "Aim for at most 100 words. Explain the core concept concisely, one simple example and the key limitation."),
    EXPANDED(650, 3600, 6000, 3, "Aim for at most 200 words. Explain the concept, include a worked numerical example and explain common interpretation mistakes."),
    FULL(900, 6000, 10000, 4, "Aim for at most 280 words. Include a worked example, compare interpretations or assumptions, explain sensitivity and limitations, and end with a practice exercise.");

    final int tokens;
    final int characters;
    final int contextCharacters;
    final int suggestions;
    final String instruction;

    FinwizDepth(int tokens, int characters, int contextCharacters, int suggestions, String instruction) {
        this.tokens = tokens;
        this.characters = characters;
        this.contextCharacters = contextCharacters;
        this.suggestions = suggestions;
        this.instruction = instruction;
    }

    static FinwizDepth from(SubscriptionPlan plan) {
        return switch (plan) { case FREE -> BASIC; case PLUS -> EXPANDED; case PRO -> FULL; };
    }

    String boundContext(String context) {
        if (context.length() <= contextCharacters) return context;
        int lastLine = context.lastIndexOf('\n', contextCharacters);
        return (lastLine < 0 ? "" : context.substring(0,lastLine + 1))
            + "Some source data exceeded the context limit. State when the supplied evidence is insufficient.";
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
