package com.stoxsim.market.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(MarketWebSocketLimits.class)
public class MarketWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String frontendUrl;
    private final MarketWebSocketAuthInterceptor authInterceptor;
    private final MarketWebSocketQuota quota;
    private final MarketWebSocketLimits limits;

    public MarketWebSocketConfig(
        @Value("${stoxsim.frontend-url}") String frontendUrl,
        MarketWebSocketAuthInterceptor authInterceptor,
        MarketWebSocketQuota quota,
        MarketWebSocketLimits limits
    ) {
        this.frontendUrl = frontendUrl;
        this.authInterceptor = authInterceptor;
        this.quota = quota;
        this.limits = limits;
    }

    @Bean(name = "marketWebSocketQuotaScheduler", defaultCandidate = false)
    public ThreadPoolTaskScheduler quotaScheduler() {
        // Provider/database jobs must not postpone authentication deadlines.
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("market-websocket-quota-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
        registration.taskExecutor().corePoolSize(2).maxPoolSize(4).queueCapacity(256);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor.outbound());
        registration.taskExecutor().corePoolSize(2).maxPoolSize(4).queueCapacity(256);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(limits.maxFrameBytes())
            .setTimeToFirstMessage(limits.authenticationTimeoutSeconds() * 1000)
            .setSendBufferSizeLimit(64 * 1024).setSendTimeLimit(5000)
            .addDecoratorFactory(handler -> new MarketWebSocketHandler(handler, quota, limits));
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Keep subscribe/unsubscribe application budgets aligned with broker order.
        registry.setPreserveReceiveOrder(true);
        registry.addEndpoint("/ws/market").setAllowedOrigins(frontendUrl);
    }
}
