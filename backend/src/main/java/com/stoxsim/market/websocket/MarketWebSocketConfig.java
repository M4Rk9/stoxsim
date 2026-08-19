package com.stoxsim.market.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class MarketWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String frontendUrl;
    private final MarketWebSocketAuthInterceptor authInterceptor;

    public MarketWebSocketConfig(
        @Value("${stoxsim.frontend-url}") String frontendUrl,
        MarketWebSocketAuthInterceptor authInterceptor
    ) {
        this.frontendUrl = frontendUrl;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/market").setAllowedOrigins(frontendUrl);
    }
}
