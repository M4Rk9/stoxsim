package com.stoxsim.market.provider.upstox;

import org.springframework.stereotype.Component;

import com.upstox.ApiException;
import com.stoxsim.market.api.StockInsightsResponse.CompanyProfile;
import com.stoxsim.market.api.StockInsightsResponse.FinancialPerformance;

import io.swagger.client.api.FundamentalsApi;
import tools.jackson.databind.ObjectMapper;

@Component
public class UpstoxFundamentalsClient {

    private final UpstoxClientFactory clientFactory;
    private final ObjectMapper objectMapper;

    public UpstoxFundamentalsClient(
        UpstoxClientFactory clientFactory,
        ObjectMapper objectMapper
    ) {
        this.clientFactory = clientFactory;
        this.objectMapper = objectMapper;
    }

    public CompanyProfile getProfile(String isin) throws ApiException {
        var response = api().getCompanyProfile(isin);
        return response == null
            ? null
            : UpstoxFundamentalsMapper.profile(objectMapper.valueToTree(response.getData()));
    }

    public java.util.List<com.stoxsim.market.api.StockInsightsResponse.FundamentalRatio>
        getRatios(String isin) throws ApiException {
        var response = api().getKeyRatios(isin);
        return response == null
            ? java.util.List.of()
            : UpstoxFundamentalsMapper.ratios(objectMapper.valueToTree(response.getData()));
    }

    public FinancialPerformance getFinancials(
        String isin,
        String timePeriod
    ) throws ApiException {
        var response = api().getIncomeStatement(
            isin,
            "consolidated",
            timePeriod,
            false
        );
        return response == null
            ? null
            : UpstoxFundamentalsMapper.financials(
                objectMapper.valueToTree(response.getData())
            );
    }

    private FundamentalsApi api() {
        return new FundamentalsApi(clientFactory.createClient());
    }
}
