package com.stoxsim.instrument.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stoxsim.market-data.upstox")
public class UpstoxInstrumentProperties {

    private String instrumentMasterUrl;

    public String getInstrumentMasterUrl() {
        return instrumentMasterUrl;
    }

    public void setInstrumentMasterUrl(String instrumentMasterUrl) {
        this.instrumentMasterUrl = instrumentMasterUrl;
    }
}
