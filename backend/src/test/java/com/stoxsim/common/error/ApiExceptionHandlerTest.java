package com.stoxsim.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.stoxsim.order.service.TradingValidationException;

class ApiExceptionHandlerTest {

    @Test
    void exposesTradingValidationMessagesAsUnprocessableEntity() {
        var meterRegistry = new SimpleMeterRegistry();
        var handler = new ApiExceptionHandler(meterRegistry);

        var response = handler.handleTradingValidation(
            new TradingValidationException(
                "Stale quote cannot be used for an order"
            )
        );

        assertThat(response.getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
            .isEqualTo("Stale quote cannot be used for an order");
        assertThat(meterRegistry
            .get("stoxsim.api.errors")
            .tag("status", "422")
            .tag("category", "trading_validation")
            .counter()
            .count())
            .isEqualTo(1.0);
    }
}
