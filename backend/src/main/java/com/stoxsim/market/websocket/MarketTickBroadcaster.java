package com.stoxsim.market.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.stoxsim.market.data.Quote;

@Component
public class MarketTickBroadcaster {

    public static final String QUOTE_TOPIC = "/topic/market/quotes";

    private final SimpMessagingTemplate messaging;

    public MarketTickBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void broadcast(Quote quote) {
        messaging.convertAndSend(QUOTE_TOPIC, MarketQuoteMessage.from(quote));
    }
}
