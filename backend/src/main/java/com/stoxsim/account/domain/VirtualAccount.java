package com.stoxsim.account.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.market.domain.MarketRegion;

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
        this.availableCash = startingBalance;
        this.blockedCash = BigDecimal.ZERO;
        this.realizedProfitLoss = BigDecimal.ZERO;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
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
