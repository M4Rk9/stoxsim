package com.stoxsim.finwiz.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.regex.Pattern;

/** A conservative output check. It validates literal quantitative claims, not narrative truth. */
final class FinwizOutputPolicy {
    private static final Pattern LINKS = Pattern.compile("(?i)(?:https?://|www\\.)");
    private static final Pattern ADVICE = Pattern.compile(
        "(?im)(?:\\byou\\s+(?:should|must|need to)\\s+(?:buy|sell|hold|short)\\b"
        + "|^\\s*(?:\\*\\*|[-*]\\s+)?(?:buy|sell|hold|short)\\s+(?:(?-i:[A-Z][A-Z.]{1,9})\\b|the stock\\b|the shares\\b)"
        + "|(?<!no )(?<!not )(?<!without )\\bguaranteed\\s+(?:(?:positive|annual|monthly)\\s+)?(?:returns?|profits?|gains?)\\b"
        + "|\\b(?:my |a )?(?:price target|target price)\\s*(?:is|of|:)\\s*(?:[₹$]|INR|USD|\\d))");
    private static final String NUMBER = "[+-]?(?:\\d{1,3}(?:,\\d{3})+|\\d{1,20})(?:\\.\\d{1,16})?";
    private static final Pattern EVIDENCE_NUMBERS = Pattern.compile("(?<![\\w.])" + NUMBER + "(?![\\w.])");
    private static final Pattern AMOUNTS = Pattern.compile(
        "(?i)(?:[₹$]|\\b(?:INR|USD)\\s+)\\s*(" + NUMBER + ")|(?<![\\w.])(" + NUMBER
        + ")\\s*(?:%|×|crore\\b|lakh\\b|million\\b|billion\\b|trillion\\b|rupees?\\b|dollars?\\b|basis points?\\b)");

    private FinwizOutputPolicy() { }

    static boolean allows(String answer, String companyEvidence) {
        if (LINKS.matcher(answer).find() || ADVICE.matcher(answer).find()) return false;
        if (companyEvidence == null) return true;

        var evidenceMatcher = EVIDENCE_NUMBERS.matcher(companyEvidence);
        var evidence = new HashSet<BigDecimal>();
        while (evidenceMatcher.find()) evidence.add(number(evidenceMatcher.group()));
        var claims = AMOUNTS.matcher(answer);
        while (claims.find()) {
            String match = claims.group(1) != null ? claims.group(1) : claims.group(2);
            if (!evidence.contains(number(match))) return false;
        }
        // A single-stock context has exactly one supported quote currency.
        if (companyEvidence.contains("Quote: currency=INR")
            && Pattern.compile("(?i)(?:\\bUSD\\s+\\d|\\$\\s*\\d)").matcher(answer).find()) return false;
        if (companyEvidence.contains("Quote: currency=USD")
            && Pattern.compile("(?i)(?:\\bINR\\s+\\d|₹\\s*\\d)").matcher(answer).find()) return false;
        return true;
    }

    private static BigDecimal number(String value) {
        return new BigDecimal(value.replace(",", "")).stripTrailingZeros();
    }
}
