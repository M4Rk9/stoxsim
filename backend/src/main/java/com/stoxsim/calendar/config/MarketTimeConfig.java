package com.stoxsim.calendar.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MarketTimeConfig {

    @Bean
    Clock marketClock() {
        return Clock.systemUTC();
    }
}
