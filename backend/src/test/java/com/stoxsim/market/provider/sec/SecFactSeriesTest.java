package com.stoxsim.market.provider.sec;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SecFactSeriesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void derivesStandaloneQuartersAndFourthQuarterFromCumulativeFilings() throws Exception {
        JsonNode facts = facts();

        assertThat(SecFactSeries.quarterly(
            facts,
            "us-gaap",
            List.of("Revenues"),
            "USD"
        )).extracting(point -> point.value())
            .containsExactly(
                new BigDecimal("140"),
                new BigDecimal("140"),
                new BigDecimal("120"),
                new BigDecimal("100")
            );

        assertThat(SecFactSeries.quarterly(
            facts,
            "us-gaap",
            List.of("NetCashProvidedByUsedInOperatingActivities"),
            "USD"
        )).extracting(point -> point.value())
            .containsExactly(
                new BigDecimal("20"),
                new BigDecimal("18"),
                new BigDecimal("17"),
                new BigDecimal("15")
            );
    }

    @Test
    void computesTrailingValuesFromFourCompatibleStandaloneQuarters() throws Exception {
        JsonNode facts = facts();

        assertThat(SecFactSeries.trailingTwelveMonths(
            facts,
            "us-gaap",
            List.of("Revenues"),
            "USD"
        )).isEqualByComparingTo("500");
        assertThat(SecFactSeries.trailingTwelveMonths(
            facts,
            "us-gaap",
            List.of("EarningsPerShareDiluted"),
            "USD/shares"
        )).isEqualByComparingTo("6.5");
    }

    @Test
    void emitsCanonicalMetricsAndConsistentTtmRatios() throws Exception {
        SecFundamentalsClient client = new SecFundamentalsClient("StoxSim tests test@example.com");

        var ratios = client.ratios(facts(), new BigDecimal("65"));
        var financials = client.financials(facts(), "quarterly");

        assertThat(ratios).extracting(ratio -> ratio.name())
            .contains("P/E (TTM)", "Diluted EPS (TTM)", "Net margin", "Operating cash-flow margin");
        assertThat(ratios).filteredOn(ratio -> ratio.name().equals("P/E (TTM)"))
            .extracting(ratio -> ratio.companyValue())
            .containsExactly("10");
        assertThat(ratios).filteredOn(ratio -> ratio.name().equals("Net margin"))
            .extracting(ratio -> ratio.companyValue())
            .containsExactly("13.00%");
        assertThat(financials.unitsIn()).isEqualTo("USD million");
        assertThat(financials.metrics()).extracting(metric -> metric.category())
            .contains("revenue", "net_profit", "operating_cash_flow", "free_cash_flow");
    }

    private JsonNode facts() throws Exception {
        return objectMapper.readTree("""
            {
              "facts": {
                "us-gaap": {
                  "Revenues": {
                    "units": { "USD": [
                      { "start":"2025-01-01", "end":"2025-03-31", "val":100, "filed":"2025-04-20", "form":"10-Q", "fy":2025, "fp":"Q1" },
                      { "start":"2025-01-01", "end":"2025-06-30", "val":220, "filed":"2025-07-20", "form":"10-Q", "fy":2025, "fp":"Q2" },
                      { "start":"2025-01-01", "end":"2025-09-30", "val":360, "filed":"2025-10-20", "form":"10-Q", "fy":2025, "fp":"Q3" },
                      { "start":"2025-01-01", "end":"2025-12-31", "val":500, "filed":"2026-02-20", "form":"10-K", "fy":2025, "fp":"FY" }
                    ] }
                  },
                  "NetIncomeLoss": {
                    "units": { "USD": [
                      { "start":"2025-01-01", "end":"2025-03-31", "val":10, "filed":"2025-04-20", "form":"10-Q", "fy":2025, "fp":"Q1" },
                      { "start":"2025-01-01", "end":"2025-06-30", "val":25, "filed":"2025-07-20", "form":"10-Q", "fy":2025, "fp":"Q2" },
                      { "start":"2025-01-01", "end":"2025-09-30", "val":45, "filed":"2025-10-20", "form":"10-Q", "fy":2025, "fp":"Q3" },
                      { "start":"2025-01-01", "end":"2025-12-31", "val":65, "filed":"2026-02-20", "form":"10-K", "fy":2025, "fp":"FY" }
                    ] }
                  },
                  "OperatingIncomeLoss": {
                    "units": { "USD": [
                      { "start":"2025-01-01", "end":"2025-03-31", "val":12, "filed":"2025-04-20", "form":"10-Q", "fy":2025, "fp":"Q1" },
                      { "start":"2025-01-01", "end":"2025-06-30", "val":30, "filed":"2025-07-20", "form":"10-Q", "fy":2025, "fp":"Q2" },
                      { "start":"2025-01-01", "end":"2025-09-30", "val":52, "filed":"2025-10-20", "form":"10-Q", "fy":2025, "fp":"Q3" },
                      { "start":"2025-01-01", "end":"2025-12-31", "val":78, "filed":"2026-02-20", "form":"10-K", "fy":2025, "fp":"FY" }
                    ] }
                  },
                  "NetCashProvidedByUsedInOperatingActivities": {
                    "units": { "USD": [
                      { "start":"2025-01-01", "end":"2025-03-31", "val":15, "filed":"2025-04-20", "form":"10-Q", "fy":2025, "fp":"Q1" },
                      { "start":"2025-01-01", "end":"2025-06-30", "val":32, "filed":"2025-07-20", "form":"10-Q", "fy":2025, "fp":"Q2" },
                      { "start":"2025-01-01", "end":"2025-09-30", "val":50, "filed":"2025-10-20", "form":"10-Q", "fy":2025, "fp":"Q3" },
                      { "start":"2025-01-01", "end":"2025-12-31", "val":70, "filed":"2026-02-20", "form":"10-K", "fy":2025, "fp":"FY" }
                    ] }
                  },
                  "PaymentsToAcquirePropertyPlantAndEquipment": {
                    "units": { "USD": [
                      { "start":"2025-01-01", "end":"2025-03-31", "val":3, "filed":"2025-04-20", "form":"10-Q", "fy":2025, "fp":"Q1" },
                      { "start":"2025-01-01", "end":"2025-06-30", "val":7, "filed":"2025-07-20", "form":"10-Q", "fy":2025, "fp":"Q2" },
                      { "start":"2025-01-01", "end":"2025-09-30", "val":12, "filed":"2025-10-20", "form":"10-Q", "fy":2025, "fp":"Q3" },
                      { "start":"2025-01-01", "end":"2025-12-31", "val":18, "filed":"2026-02-20", "form":"10-K", "fy":2025, "fp":"FY" }
                    ] }
                  },
                  "EarningsPerShareDiluted": {
                    "units": { "USD/shares": [
                      { "start":"2025-01-01", "end":"2025-03-31", "val":1.0, "filed":"2025-04-20", "form":"10-Q", "fy":2025, "fp":"Q1" },
                      { "start":"2025-01-01", "end":"2025-06-30", "val":2.5, "filed":"2025-07-20", "form":"10-Q", "fy":2025, "fp":"Q2" },
                      { "start":"2025-01-01", "end":"2025-09-30", "val":4.5, "filed":"2025-10-20", "form":"10-Q", "fy":2025, "fp":"Q3" },
                      { "start":"2025-01-01", "end":"2025-12-31", "val":6.5, "filed":"2026-02-20", "form":"10-K", "fy":2025, "fp":"FY" }
                    ] }
                  },
                  "Assets": {
                    "units": { "USD": [
                      { "end":"2025-12-31", "val":1000, "filed":"2026-02-20", "form":"10-K", "fy":2025, "fp":"FY" }
                    ] }
                  },
                  "Liabilities": {
                    "units": { "USD": [
                      { "end":"2025-12-31", "val":400, "filed":"2026-02-20", "form":"10-K", "fy":2025, "fp":"FY" }
                    ] }
                  }
                }
              }
            }
            """);
    }
}
