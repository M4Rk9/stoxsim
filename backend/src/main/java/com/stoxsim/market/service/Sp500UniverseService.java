package com.stoxsim.market.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class Sp500UniverseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(Sp500UniverseService.class);
    private static final String HOLDINGS_URL = "https://www.ishares.com/us/products/239726/ishares-core-s-p-500-etf/latest-holdings.csv";
    private static final Duration REFRESH_INTERVAL = Duration.ofHours(12);
    private static final Set<String> SAFE_FALLBACK = Set.of(
        "AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "GOOG", "META", "AVGO",
        "LLY", "JPM", "WMT", "V", "MA", "ORCL", "XOM", "COST", "NFLX",
        "HD", "PG", "JNJ", "ABBV", "BAC", "KO", "CRM", "CSCO", "CVX",
        "IBM", "AMD", "GE", "PM", "MRK", "MCD", "ADBE", "PEP", "TMO",
        "DIS", "LIN", "WFC", "QCOM", "TXN", "CAT", "AMGN", "ISRG", "NOW",
        "GS", "INTU", "RTX", "BKNG", "SPGI", "UNH", "T", "VZ", "COP",
        "AMAT", "PANW", "MU", "PFE", "PLTR", "LRCX", "GEV", "MS", "KLAC",
        "C", "CRWD", "APH", "BRK.B"
    );

    private final RestClient client;
    private volatile Set<String> symbols = SAFE_FALLBACK;
    private volatile Instant refreshedAt = Instant.EPOCH;

    public Sp500UniverseService() {
        this.client = RestClient.builder()
            .defaultHeader("User-Agent", "StoxSim/1.0 (+https://stoxsim.com)")
            .build();
    }

    public Set<String> symbols() {
        if (refreshedAt.isBefore(Instant.now().minus(REFRESH_INTERVAL))) {
            refresh();
        }
        return symbols;
    }

    public boolean contains(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        String normalized = normalize(symbol);
        return symbols().stream().anyMatch(candidate -> normalize(candidate).equals(normalized));
    }

    public synchronized void refresh() {
        if (!refreshedAt.isBefore(Instant.now().minus(REFRESH_INTERVAL))) {
            return;
        }
        try {
            String csv = client.get()
                .uri(HOLDINGS_URL)
                .retrieve()
                .body(String.class);
            Set<String> parsed = parse(csv);
            if (parsed.size() < 450) {
                throw new IllegalStateException(
                    "S&P 500 holdings source returned only " + parsed.size() + " equity symbols"
                );
            }
            symbols = Set.copyOf(parsed);
            refreshedAt = Instant.now();
            LOGGER.info("Refreshed S&P 500 universe with {} symbols", symbols.size());
        } catch (RestClientException | IllegalStateException exception) {
            refreshedAt = Instant.now();
            LOGGER.warn(
                "Could not refresh the S&P 500 constituent universe; retaining {} verified fallback symbols",
                symbols.size(),
                exception
            );
        }
    }

    Set<String> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> parsed = new LinkedHashSet<>();
        for (String line : csv.replace("\r\n", "\n").split("\n")) {
            List<String> columns = parseCsvLine(line);
            if (columns.size() < 4) continue;
            if (!"Equity".equalsIgnoreCase(columns.get(3).trim())) continue;
            String ticker = columns.get(0).trim();
            if (ticker.isBlank() || "Ticker".equalsIgnoreCase(ticker)) continue;
            parsed.add(normalizeProviderTicker(ticker));
        }
        return Set.copyOf(parsed);
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(current);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    private String normalizeProviderTicker(String ticker) {
        String normalized = ticker.trim().toUpperCase(Locale.ROOT);
        if ("BRKB".equals(normalized)) return "BRK.B";
        if ("BFB".equals(normalized)) return "BF.B";
        return normalized;
    }

    private String normalize(String symbol) {
        return symbol.toUpperCase(Locale.ROOT).replace(".", "").replace("-", "");
    }
}
