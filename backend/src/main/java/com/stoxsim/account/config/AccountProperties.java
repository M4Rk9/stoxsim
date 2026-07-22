package com.stoxsim.account.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stoxsim.accounts")
public class AccountProperties {

    private BigDecimal indiaStartingBalance;
    private BigDecimal unitedStatesStartingBalance;

    public BigDecimal getIndiaStartingBalance() {
        return indiaStartingBalance;
    }

    public void setIndiaStartingBalance(BigDecimal indiaStartingBalance) {
        this.indiaStartingBalance = indiaStartingBalance;
    }

    public BigDecimal getUnitedStatesStartingBalance() {
        return unitedStatesStartingBalance;
    }

    public void setUnitedStatesStartingBalance(BigDecimal unitedStatesStartingBalance) {
        this.unitedStatesStartingBalance = unitedStatesStartingBalance;
    }
}
