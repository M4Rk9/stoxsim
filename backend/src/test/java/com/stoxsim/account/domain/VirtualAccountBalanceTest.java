package com.stoxsim.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.order.service.TradingValidationException;

class VirtualAccountBalanceTest {

    @Test
    void reservesSettlesAndReleasesCashWithoutLosingPrecision() {
        var account = account();

        account.reserveCash(new BigDecimal("100000.00"));
        assertThat(account.getAvailableCash()).isEqualByComparingTo("400000.0000");
        assertThat(account.getBlockedCash()).isEqualByComparingTo("100000.0000");

        account.settleReservedCash(
            new BigDecimal("100000.00"),
            new BigDecimal("99850.25")
        );

        assertThat(account.getAvailableCash()).isEqualByComparingTo("400149.7500");
        assertThat(account.getBlockedCash()).isEqualByComparingTo("0.0000");
    }

    @Test
    void refusesToReserveMoreThanAvailableCash() {
        var account = account();

        assertThatThrownBy(() -> account.reserveCash(new BigDecimal("500000.01")))
            .isInstanceOf(TradingValidationException.class)
            .hasMessage("Insufficient available cash");
    }

    private VirtualAccount account() {
        return new VirtualAccount(
            new AppUser("learner@example.com", "hash", "Learner"),
            MarketRegion.INDIA,
            new BigDecimal("500000.00")
        );
    }
}
