package com.stoxsim.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stoxsim.trading")
public class TradingProperties {

    private int slippageBasisPoints = 5;

    public int getSlippageBasisPoints() {
        return slippageBasisPoints;
    }

    public void setSlippageBasisPoints(int slippageBasisPoints) {
        if (slippageBasisPoints < 0 || slippageBasisPoints > 1000) {
            throw new IllegalArgumentException("Slippage basis points must be between 0 and 1000");
        }
        this.slippageBasisPoints = slippageBasisPoints;
    }
}
