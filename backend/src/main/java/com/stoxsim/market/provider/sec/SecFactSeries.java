package com.stoxsim.market.provider.sec;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;

final class SecFactSeries {

    private static final Set<String> QUARTERLY_FORMS = Set.of("10-Q", "10-Q/A");
    private static final Set<String> YEARLY_FORMS = Set.of(
        "10-K", "10-K/A", "20-F", "20-F/A", "40-F", "40-F/A"
    );

    private SecFactSeries() {
    }

    static List<Point> quarterly(
        JsonNode facts,
        String taxonomy,
        List<String> tags,
        String unit
    ) {
        List<FilingFact> values = rawFacts(facts, taxonomy, tags, unit);
        Map<LocalDate, List<FilingFact>> byFiscalStart = values.stream()
            .filter(fact -> fact.start() != null)
            .collect(java.util.stream.Collectors.groupingBy(
                FilingFact::start,
                LinkedHashMap::new,
                java.util.stream.Collectors.toList()
            ));

        List<Point> result = new ArrayList<>();
        for (List<FilingFact> fiscalYear : byFiscalStart.values()) {
            FilingFact q1 = latest(fiscalYear, "Q1", 55, 130, QUARTERLY_FORMS);
            FilingFact q2Cumulative = latest(fiscalYear, "Q2", 131, 230, QUARTERLY_FORMS);
            FilingFact q3Cumulative = latest(fiscalYear, "Q3", 231, 310, QUARTERLY_FORMS);
            FilingFact annual = latest(fiscalYear, "FY", 300, 430, YEARLY_FORMS);

            add(result, q1 == null ? null : q1.point());
            add(result, difference(q2Cumulative, q1));
            add(result, difference(q3Cumulative, q2Cumulative));
            add(result, difference(annual, q3Cumulative));
        }

        values.stream()
            .filter(fact -> QUARTERLY_FORMS.contains(fact.form()))
            .filter(fact -> Set.of("Q2", "Q3").contains(fact.fiscalPeriod()))
            .filter(fact -> fact.durationDays() >= 55 && fact.durationDays() <= 130)
            .map(FilingFact::point)
            .forEach(point -> add(result, point));

        return newestFive(result);
    }

    static List<Point> yearly(
        JsonNode facts,
        String taxonomy,
        List<String> tags,
        String unit
    ) {
        List<Point> annual = rawFacts(facts, taxonomy, tags, unit).stream()
            .filter(fact -> YEARLY_FORMS.contains(fact.form()))
            .filter(fact -> fact.durationDays() >= 300 && fact.durationDays() <= 430)
            .map(FilingFact::point)
            .toList();
        return newestFive(annual);
    }

    static BigDecimal trailingTwelveMonths(
        JsonNode facts,
        String taxonomy,
        List<String> tags,
        String unit
    ) {
        List<Point> quarters = quarterly(facts, taxonomy, tags, unit);
        if (quarters.size() < 4) return null;

        List<Point> latest = quarters.subList(0, 4);
        for (int index = 0; index < latest.size() - 1; index++) {
            long gap = ChronoUnit.DAYS.between(
                latest.get(index + 1).end(),
                latest.get(index).end()
            );
            if (gap < 45 || gap > 150) return null;
        }
        return latest.stream()
            .map(Point::value)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static BigDecimal latestInstant(
        JsonNode facts,
        String taxonomy,
        List<String> tags,
        String unit
    ) {
        List<FilingFact> values = rawFacts(facts, taxonomy, tags, unit);
        return values.stream()
            .filter(fact -> fact.start() == null)
            .max(Comparator.comparing(FilingFact::end).thenComparing(FilingFact::filed))
            .or(() -> values.stream().max(
                Comparator.comparing(FilingFact::end).thenComparing(FilingFact::filed)
            ))
            .map(FilingFact::value)
            .orElse(null);
    }

    private static FilingFact latest(
        List<FilingFact> facts,
        String fiscalPeriod,
        long minimumDays,
        long maximumDays,
        Set<String> forms
    ) {
        return facts.stream()
            .filter(fact -> forms.contains(fact.form()))
            .filter(fact -> fiscalPeriod.equals(fact.fiscalPeriod()))
            .filter(fact -> fact.durationDays() >= minimumDays)
            .filter(fact -> fact.durationDays() <= maximumDays)
            .max(Comparator.comparing(FilingFact::filed))
            .orElse(null);
    }

    private static Point difference(FilingFact total, FilingFact previous) {
        if (total == null || previous == null) return null;
        if (total.start() == null || !total.start().equals(previous.start())) return null;
        return new Point(
            total.end(),
            total.value().subtract(previous.value()),
            total.filed()
        );
    }

    private static void add(List<Point> target, Point point) {
        if (point != null) target.add(point);
    }

    private static List<Point> newestFive(List<Point> points) {
        Map<LocalDate, Point> byEnd = new LinkedHashMap<>();
        for (Point point : points) {
            Point existing = byEnd.get(point.end());
            if (existing == null || point.filed().isAfter(existing.filed())) {
                byEnd.put(point.end(), point);
            }
        }
        return byEnd.values().stream()
            .sorted(Comparator.comparing(Point::end).reversed())
            .limit(5)
            .toList();
    }

    private static List<FilingFact> rawFacts(
        JsonNode facts,
        String taxonomy,
        List<String> tags,
        String unit
    ) {
        JsonNode taxonomyNode = facts == null
            ? null
            : facts.path("facts").path(taxonomy);
        if (taxonomyNode == null || taxonomyNode.isMissingNode()) return List.of();

        for (String tag : tags) {
            JsonNode values = taxonomyNode.path(tag).path("units").path(unit);
            if (!values.isArray()) continue;
            List<FilingFact> result = new ArrayList<>();
            for (JsonNode value : values) {
                LocalDate end = date(value, "end");
                BigDecimal amount = decimal(value.get("val"));
                if (end == null || amount == null) continue;
                LocalDate start = date(value, "start");
                String form = text(value, "form");
                String fiscalPeriod = fiscalPeriod(value);
                result.add(new FilingFact(
                    start,
                    end,
                    amount,
                    parseFiled(text(value, "filed")),
                    form == null ? "" : form,
                    fiscalPeriod
                ));
            }
            if (!result.isEmpty()) return List.copyOf(result);
        }
        return List.of();
    }

    private static String fiscalPeriod(JsonNode value) {
        String period = text(value, "fp");
        if (period != null) return period.toUpperCase(Locale.ROOT);
        String frame = text(value, "frame");
        if (frame == null) return "";
        String normalized = frame.toUpperCase(Locale.ROOT);
        if (normalized.matches(".*Q[1-4].*")) {
            int marker = normalized.indexOf('Q');
            return normalized.substring(marker, marker + 2);
        }
        return normalized.matches(".*\\d{4}.*") ? "FY" : "";
    }

    private static LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Instant parseFiled(String value) {
        if (value == null) return Instant.EPOCH;
        try {
            return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }

    private static BigDecimal decimal(JsonNode value) {
        if (value == null || value.isNull() || !value.isNumber()) return null;
        try {
            return value.decimalValue();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        String result = value.asText();
        return result == null || result.isBlank() ? null : result.trim();
    }

    record Point(LocalDate end, BigDecimal value, Instant filed) {
    }

    private record FilingFact(
        LocalDate start,
        LocalDate end,
        BigDecimal value,
        Instant filed,
        String form,
        String fiscalPeriod
    ) {
        long durationDays() {
            return start == null ? 0 : ChronoUnit.DAYS.between(start, end) + 1;
        }

        Point point() {
            return new Point(end, value, filed);
        }
    }
}
