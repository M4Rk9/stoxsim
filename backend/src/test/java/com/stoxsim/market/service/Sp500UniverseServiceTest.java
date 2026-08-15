package com.stoxsim.market.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Sp500UniverseServiceTest {

    @Test
    void parsesOnlyEquityHoldingsAndNormalizesClassShareTickers() {
        String csv = """
            iShares Core S&P 500 ETF
            Fund Holdings as of,"Aug 07, 2026"
            Ticker,Name,Sector,Asset Class,Market Value
            "AAPL","APPLE INC","Information Technology","Equity","1,000"
            "BRKB","BERKSHIRE HATHAWAY INC CLASS B","Financials","Equity","900"
            "BFB","BROWN FORMAN INC CLASS B","Consumer Staples","Equity","800"
            "USD","USD CASH","Cash and/or Derivatives","Cash","100"
            "ESU6","S&P 500 EMINI FUT SEP 26","Cash and/or Derivatives","Futures","50"
            """;

        var parsed = new Sp500UniverseService().parse(csv);

        assertThat(parsed)
            .contains("AAPL", "BRK.B", "BF.B")
            .doesNotContain("USD", "ESU6");
    }
}
