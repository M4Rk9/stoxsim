package com.stoxsim.order.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.instrument.domain.TradableInstrument;
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
@Table(name = "paper_order")
public class PaperOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private VirtualAccount account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private TradableInstrument instrument;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 16)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 16)
    private ProductType productType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private OrderValidity validity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "limit_price", precision = 19, scale = 4)
    private BigDecimal limitPrice;

    @Column(name = "reserved_cash", nullable = false, precision = 19, scale = 4)
    private BigDecimal reservedCash;

    @Column(name = "execution_price", precision = 19, scale = 4)
    private BigDecimal executionPrice;

    @Column(name = "executed_value", precision = 19, scale = 4)
    private BigDecimal executedValue;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "submitted_for_date", nullable = false)
    private LocalDate submittedForDate;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    protected PaperOrder() {
    }

    public PaperOrder(
        VirtualAccount account,
        TradableInstrument instrument,
        String idempotencyKey,
        OrderSide side,
        OrderType orderType,
        long quantity,
        BigDecimal limitPrice,
        BigDecimal reservedCash,
        LocalDate submittedForDate
    ) {
        this.account = account;
        this.instrument = instrument;
        this.idempotencyKey = idempotencyKey;
        this.side = side;
        this.orderType = orderType;
        this.productType = ProductType.DELIVERY;
        this.validity = OrderValidity.DAY;
        this.status = OrderStatus.OPEN;
        this.quantity = quantity;
        this.limitPrice = moneyOrNull(limitPrice);
        this.reservedCash = money(reservedCash);
        this.submittedForDate = submittedForDate;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void changeTerms(long newQuantity, BigDecimal newLimitPrice, BigDecimal newReservedCash) {
        requireOpen();
        if (orderType != OrderType.LIMIT) {
            throw new TradingValidationException("Only open limit orders can be modified");
        }
        this.quantity = newQuantity;
        this.limitPrice = moneyOrNull(newLimitPrice);
        this.reservedCash = money(newReservedCash);
        this.updatedAt = Instant.now();
    }

    public void markExecuted(BigDecimal price, BigDecimal value, Instant timestamp) {
        requireOpen();
        this.status = OrderStatus.EXECUTED;
        this.executionPrice = money(price);
        this.executedValue = money(value);
        this.executedAt = timestamp;
        this.updatedAt = timestamp;
    }

    public void markCancelled() {
        requireOpen();
        status = OrderStatus.CANCELLED;
        updatedAt = Instant.now();
    }

    public void markExpired() {
        requireOpen();
        status = OrderStatus.EXPIRED;
        updatedAt = Instant.now();
    }

    public void markRejected(String reason) {
        requireOpen();
        status = OrderStatus.REJECTED;
        rejectionReason = reason;
        updatedAt = Instant.now();
    }

    public boolean isOpen() {
        return status == OrderStatus.OPEN;
    }

    private void requireOpen() {
        if (!isOpen()) {
            throw new TradingValidationException("Order is no longer open");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal moneyOrNull(BigDecimal value) {
        return value == null ? null : money(value);
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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public OrderSide getSide() {
        return side;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public ProductType getProductType() {
        return productType;
    }

    public OrderValidity getValidity() {
        return validity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public long getQuantity() {
        return quantity;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public BigDecimal getReservedCash() {
        return reservedCash;
    }

    public BigDecimal getExecutionPrice() {
        return executionPrice;
    }

    public BigDecimal getExecutedValue() {
        return executedValue;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public LocalDate getSubmittedForDate() {
        return submittedForDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}
