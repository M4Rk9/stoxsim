package com.stoxsim.release;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.market.provider.alpaca.AlpacaMarketDataProperties;
import com.stoxsim.market.provider.upstox.UpstoxMarketDataProperties;

@Component
public class PublicReleaseGuard {

    private final PublicReleaseProperties releaseProperties;
    private final UpstoxMarketDataProperties upstoxProperties;
    private final AlpacaMarketDataProperties alpacaProperties;

    public PublicReleaseGuard(
        PublicReleaseProperties releaseProperties,
        UpstoxMarketDataProperties upstoxProperties,
        AlpacaMarketDataProperties alpacaProperties
    ) {
        this.releaseProperties = releaseProperties;
        this.upstoxProperties = upstoxProperties;
        this.alpacaProperties = alpacaProperties;
    }

    public void requirePublicRegistration() {
        if (!releaseProperties.isPublicRegistrationEnabled()) {
            unavailable("Public registration is temporarily closed");
        }
        if (!upstoxProperties.isPublicServingEnabled()
            || !alpacaProperties.isPublicServingEnabled()) {
            unavailable(
                "Public registration is closed until market-data permissions are enabled"
            );
        }
    }

    private void unavailable(String message) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
