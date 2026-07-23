package com.stoxsim.market.provider.upstox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class UpstoxFundamentalsMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsCompanyProfileAndRatiosWithoutInventingUnavailableFields() throws Exception {
        var profile = UpstoxFundamentalsMapper.profile(objectMapper.readTree("""
            {
              "company_profile": "A diversified listed company.",
              "sector": "Industrials",
              "sector_market_cap_inr": {
                "value": 123456.78,
                "unit": "crore",
                "formatted": "1,23,456.78 Cr"
              }
            }
            """));
        var ratios = UpstoxFundamentalsMapper.ratios(objectMapper.readTree("""
            [
              { "name": "P/E", "company_value": "20.15", "sector_value": "12.46" },
              { "name": "ROE", "company_value": "8.94%", "sector_value": "16.46%" }
            ]
            """));

        assertThat(profile.sector()).isEqualTo("Industrials");
        assertThat(profile.sectorMarketCapInrCrore()).isEqualByComparingTo("123456.78");
        assertThat(ratios).hasSize(2);
        assertThat(ratios.get(1).companyValue()).isEqualTo("8.94%");
    }

    @Test
    void mapsQuarterlyRevenueAndProfitHistory() throws Exception {
        var financials = UpstoxFundamentalsMapper.financials(objectMapper.readTree("""
            {
              "type": "consolidated",
              "time_period": "quarterly",
              "units_in": "crore",
              "income_statement": [
                {
                  "category": "revenue",
                  "history": [
                    { "value": 133110, "period": "Jun 2026", "change": "+13.85%" }
                  ]
                },
                {
                  "category": "net_profit",
                  "history": [
                    { "value": 20383, "period": "Jun 2026", "change": "-3.28%" }
                  ]
                }
              ]
            }
            """));

        assertThat(financials.timePeriod()).isEqualTo("quarterly");
        assertThat(financials.metrics()).hasSize(2);
        assertThat(financials.metrics().getFirst().history().getFirst().value())
            .isEqualByComparingTo("133110");
    }
}
