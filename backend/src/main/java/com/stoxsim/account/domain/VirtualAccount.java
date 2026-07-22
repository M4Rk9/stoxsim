package com.stoxsim.account.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.order.service.TradingValidationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "virtual_account")
public class VirtualAccount {

    private static final int MONEY_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_region", nullable = false, length = 24)
    private MarketRegion marketRegion;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "available_cash", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableCash;

    @Column(name = "blocked_cash", nullable = false, precision = 19, scale = 4)
    private BigDecimal blockedCash;

    @Column(name = "realized_profit_loss", nullable = false, precision = 19, scale = 4)
    private BigDecimal realizedProfitLoss;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VirtualAccount() {
    }

    public VirtualAccount(AppUser user, MarketRegion marketRegion, BigDecimal startingBalance) {
        this.user = user;
        this.marketRegion = marketRegion;
        this.currency = marketRegion.currency();
        this.availableCash = money(startingBalance);
        this.blockedCash = money(BigDecimal.ZERO);
        this.realizedProfitLoss = money(BigDecimal.ZERO);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void reserveCash(BigDecimal amount) {
        BigDecimal normalized = money(amount);
        if (normalized.signum() <= 0) {
            throw new TradingValidationException("Reserved cash must be positive");
        }
        if (availableCash.compareTo(normalized) < 0) {
            throw new TradingValidationException("Insufficient available cash");
        }
        availableCash = availableCash.subtract(normalized);
        blockedCash = blockedCash.add(normalized);
        touch();
    }

    public void releaseReservedCash(BigDecimal amount) {
        BigDecimal normalized = money(amount);
        if (blockedCash.compareTo(normalized) < 0) {
            throw new IllegalStateException("Reserved cash invariant violated");
        }
        blockedCash = blockedCash.subtract(normalized);
        availableCash = availableCash.add(normalized);
        touch();
    }

    public void settleReservedCash(BigDecimal reservedAmount, BigDecimal debitAmount) {
        BigDecimal reserved = money(reservedAmount);
        BigDecimal debit = money(debitAmount);
        if (blockedCash.compareTo(reserved) < 0) {
            throw new IllegalStateException("Reserved cash invariant violated");
        }
        BigDecimal newAvailable = availableCash.add(reserved).subtract(debit);
        if (newAvailable.signum() < 0) {
            throw new TradingValidationException("Insufficient cash after market price movement");
        }
        blockedCash = blockedCash.subtract(reserved);
        availableCash = newAvailable;
        touch();
    }

    public void creditCash(BigDecimal amount) {
        BigDecimal normalized = money(amount);
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException("Credit must not be negative");
        }
        availableCash = availableCash.add(normalized);
        touch();
    }

    public void addRealizedProfitLoss(BigDecimal amount) {
        realizedProfitLoss = realizedProfitLoss.add(money(amount));
        touch();
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return user.getId();
    }

    public MarketRegion getMarketRegion() {
        return marketRegion;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAvailableCash() {
        return availableCash;
    }

    public BigDecimal getBlockedCash() {
        return blockedCash;
    }

    public BigDecimal getRealizedProfitLoss() {
        return realizedProfitLoss;
    }
}
