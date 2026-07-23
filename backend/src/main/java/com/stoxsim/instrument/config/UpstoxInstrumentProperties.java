package com.stoxsim.instrument.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stoxsim.market-data.upstox")
public class UpstoxInstrumentProperties {

    private String nseInstrumentMasterUrl;
    private String bseInstrumentMasterUrl;

    public String getNseInstrumentMasterUrl() {
        return nseInstrumentMasterUrl;
    }

    public void setNseInstrumentMasterUrl(String nseInstrumentMasterUrl) {
        this.nseInstrumentMasterUrl = nseInstrumentMasterUrl;
    }

    public String getBseInstrumentMasterUrl() {
        return bseInstrumentMasterUrl;
    }

    public void setBseInstrumentMasterUrl(String bseInstrumentMasterUrl) {
        this.bseInstrumentMasterUrl = bseInstrumentMasterUrl;
    }

    public List<String> instrumentMasterUrls() {
        return List.of(nseInstrumentMasterUrl, bseInstrumentMasterUrl);
    }
}
