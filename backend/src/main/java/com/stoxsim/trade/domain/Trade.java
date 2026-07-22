package com.stoxsim.trade.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.charge.ChargeBreakdown;
import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.order.domain.OrderSide;
import com.stoxsim.order.domain.PaperOrder;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "trade")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private PaperOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private VirtualAccount account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private TradableInstrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private OrderSide side;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "gross_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossValue;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal charges;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal brokerage;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal stt;

    @Column(name = "exchange_charges", nullable = false, precision = 19, scale = 4)
    private BigDecimal exchangeCharges;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal gst;

    @Column(name = "sebi_charges", nullable = false, precision = 19, scale = 4)
    private BigDecimal sebiCharges;

    @Column(name = "stamp_duty", nullable = false, precision = 19, scale = 4)
    private BigDecimal stampDuty;

    @Column(name = "dp_charges", nullable = false, precision = 19, scale = 4)
    private BigDecimal dpCharges;

    @Column(name = "net_cash_effect", nullable = false, precision = 19, scale = 4)
    private BigDecimal netCashEffect;

    @Column(name = "charge_schedule_version", nullable = false, length = 48)
    private String chargeScheduleVersion;

    @Column(name = "charges_simulated", nullable = false)
    private boolean chargesSimulated;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    protected Trade() {
    }

    public Trade(
        PaperOrder order,
        BigDecimal price,
        BigDecimal grossValue,
        ChargeBreakdown breakdown,
        BigDecimal netCashEffect,
        Instant executedAt
    ) {
        this.order = order;
        this.account = order.getAccount();
        this.instrument = order.getInstrument();
        this.side = order.getSide();
        this.quantity = order.getQuantity();
        this.price = money(price);
        this.grossValue = money(grossValue);
        this.charges = money(breakdown.totalCharges());
        this.brokerage = money(breakdown.brokerage());
        this.stt = money(breakdown.stt());
        this.exchangeCharges = money(breakdown.exchangeCharges());
        this.gst = money(breakdown.gst());
        this.sebiCharges = money(breakdown.sebiCharges());
        this.stampDuty = money(breakdown.stampDuty());
        this.dpCharges = money(breakdown.dpCharges());
        this.netCashEffect = money(netCashEffect);
        this.chargeScheduleVersion = breakdown.scheduleVersion();
        this.chargesSimulated = breakdown.simulated();
        this.executedAt = executedAt;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    public UUID getId() {
        return id;
    }

    public PaperOrder getOrder() {
        return order;
    }

    public VirtualAccount getAccount() {
        return account;
    }

    public TradableInstrument getInstrument() {
        return instrument;
    }

    public OrderSide getSide() {
        return side;
    }

    public long getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getGrossValue() {
        return grossValue;
    }

    public BigDecimal getCharges() {
        return charges;
    }

    public BigDecimal getBrokerage() {
        return brokerage;
    }

    public BigDecimal getStt() {
        return stt;
    }

    public BigDecimal getExchangeCharges() {
        return exchangeCharges;
    }

    public BigDecimal getGst() {
        return gst;
    }

    public BigDecimal getSebiCharges() {
        return sebiCharges;
    }

    public BigDecimal getStampDuty() {
        return stampDuty;
    }

    public BigDecimal getDpCharges() {
        return dpCharges;
    }

    public BigDecimal getNetCashEffect() {
        return netCashEffect;
    }

    public String getChargeScheduleVersion() {
        return chargeScheduleVersion;
    }

    public boolean isChargesSimulated() {
        return chargesSimulated;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}
