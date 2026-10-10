package com.stoxsim.market.websocket;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("stoxsim.security.websocket")
public record MarketWebSocketLimits(
    @Min(1) @DefaultValue("256") int maxConnections,
    @Min(1) @DefaultValue("32") int connectionsPerIp,
    @Min(1) @DefaultValue("4") int connectionsPerUser,
    @Min(1) @DefaultValue("10") int framesPerSecond,
    @Min(1) @DefaultValue("20") int userFramesPerSecond,
    @Min(1) @DefaultValue("256") int globalFramesPerSecond,
    @Min(1024) @DefaultValue("8192") int maxFrameBytes,
    @Min(1) @DefaultValue("10") int authenticationTimeoutSeconds
) {
    public static MarketWebSocketLimits defaults() {
        return new MarketWebSocketLimits(256, 32, 4, 10, 20, 256, 8192, 10);
    }
}
