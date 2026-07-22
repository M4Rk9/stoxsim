package com.stoxsim.portfolio.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.order.service.TradingValidationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "holding")
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private VirtualAccount account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private TradableInstrument instrument;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "blocked_quantity", nullable = false)
    private long blockedQuantity;

    @Column(name = "average_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal averagePrice;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Holding() {
    }

    public Holding(
        VirtualAccount account,
        TradableInstrument instrument,
        long quantity,
        BigDecimal price
    ) {
        this.account = account;
        this.instrument = instrument;
        this.quantity = quantity;
        this.blockedQuantity = 0;
        this.averagePrice = money(price);
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void buy(long addedQuantity, BigDecimal price) {
        if (addedQuantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        BigDecimal existingCost = averagePrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal addedCost = money(price).multiply(BigDecimal.valueOf(addedQuantity));
        long totalQuantity = Math.addExact(quantity, addedQuantity);
        averagePrice = existingCost.add(addedCost)
            .divide(BigDecimal.valueOf(totalQuantity), 4, RoundingMode.HALF_UP);
        quantity = totalQuantity;
        touch();
    }

    public void reserve(long requestedQuantity) {
        if (requestedQuantity <= 0 || availableQuantity() < requestedQuantity) {
            throw new TradingValidationException("Insufficient shares available to sell");
        }
        blockedQuantity = Math.addExact(blockedQuantity, requestedQuantity);
        touch();
    }

    public void release(long reservedQuantity) {
        if (reservedQuantity <= 0 || blockedQuantity < reservedQuantity) {
            throw new IllegalStateException("Blocked share invariant violated");
        }
        blockedQuantity -= reservedQuantity;
        touch();
    }

    public BigDecimal sellReserved(long soldQuantity, BigDecimal price) {
        if (soldQuantity <= 0 || blockedQuantity < soldQuantity || quantity < soldQuantity) {
            throw new IllegalStateException("Blocked share invariant violated");
        }
        blockedQuantity -= soldQuantity;
        quantity -= soldQuantity;
        BigDecimal realized = money(price)
            .subtract(averagePrice)
            .multiply(BigDecimal.valueOf(soldQuantity))
            .setScale(4, RoundingMode.HALF_UP);
        if (quantity == 0) {
            averagePrice = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        touch();
        return realized;
    }

    public long availableQuantity() {
        return quantity - blockedQuantity;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public VirtualAccount getAccount() {
        return account;
    }

    public TradableInstrument getInstrument() {
        return instrument;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getBlockedQuantity() {
        return blockedQuantity;
    }

    public BigDecimal getAveragePrice() {
        return averagePrice;
    }
}
