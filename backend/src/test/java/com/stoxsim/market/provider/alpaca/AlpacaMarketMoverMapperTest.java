package com.stoxsim.market.provider.alpaca;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class AlpacaMarketMoverMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AlpacaMarketMoverMapper mapper = new AlpacaMarketMoverMapper();

    @Test
    void mapsGainersAndSupportsDocumentedPercentField() throws Exception {
        var root = objectMapper.readTree("""
            {
              "gainers": [
                {
                  "symbol": "AAPL",
                  "price": 214.50,
                  "change": 5.20,
                  "percent_change": 2.4841
                }
              ],
              "losers": []
            }
            """);

        var movers = mapper.map(root, "gainers");

        assertThat(movers).hasSize(1);
        assertThat(movers.getFirst().symbol()).isEqualTo("AAPL");
        assertThat(movers.getFirst().price()).isEqualByComparingTo("214.50");
        assertThat(movers.getFirst().percentChange())
            .isEqualByComparingTo("2.4841");
    }
}
