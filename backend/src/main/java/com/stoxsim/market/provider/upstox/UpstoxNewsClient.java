package com.stoxsim.market.provider.upstox;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
public class UpstoxNewsClient {
    private final UpstoxClientFactory factory;
    private final RestClient client;
    private static RestClient newsClient() {
        var requests = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(5000);
        requests.setReadTimeout(5000);
        return RestClient.builder().baseUrl("https://api.upstox.com").requestFactory(requests).build();
    }
    public UpstoxNewsClient(UpstoxClientFactory factory) { this.factory = factory; this.client = newsClient(); }
    public JsonNode get(String instrumentKey) {
        return client.get().uri(uri -> uri.path("/v2/news")
            .queryParam("category", "instrument_keys").queryParam("instrument_keys", "{key}")
            .queryParam("page_size", 10).queryParam("page_number", 1).build(instrumentKey))
            .headers(headers -> factory.authorizationHeaders().forEach(headers::set))
            .retrieve().body(JsonNode.class);
    }
}
