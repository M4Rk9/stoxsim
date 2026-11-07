package com.stoxsim.derivative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import com.stoxsim.derivative.DerivativeBook.Action;
import com.stoxsim.derivative.DerivativeCatalog.Contract;
import com.stoxsim.derivative.DerivativeCatalog.Kind;
import com.stoxsim.order.service.TradingValidationException;

class DerivativeBookTest {
    private static final BigDecimal CASH = new BigDecimal("1000000");

    @Test
    void longFuturesReserveAndReleaseMarginProportionally() {
        var contract = contract(Kind.FUT);
        var opened = DerivativeBook.trade(contract, null, Action.OPEN_LONG, 2, price("24000"), CASH);
        assertThat(opened.cashChange()).isEqualByComparingTo("-624000");
        assertThat(opened.position().reservedMargin()).isEqualByComparingTo("624000");
        var closed = DerivativeBook.trade(contract, opened.position(), Action.CLOSE, 1, price("24100"), CASH);
        assertThat(closed.position().lots()).isEqualTo(1);
        assertThat(closed.position().reservedMargin()).isEqualByComparingTo("312000");
        assertThat(closed.realizedPnl()).isEqualByComparingTo("6500");
        assertThat(closed.cashChange()).isEqualByComparingTo("318500");
    }

    @Test
    void shortFuturesLoseWhenTheSettlementLevelRises() {
        var contract = contract(Kind.FUT);
        var open = DerivativeBook.trade(contract, null, Action.OPEN_SHORT, 1, price("24000"), CASH);
        var settled = DerivativeBook.trade(contract, open.position(), Action.EXPIRY, 1, price("24100"), CASH);
        assertThat(settled.position()).isNull();
        assertThat(settled.cashChange()).isEqualByComparingTo("305500");
        assertThat(settled.realizedPnl()).isEqualByComparingTo("-6500");
    }

    @Test
    void longOptionsDebitPremiumAndSettleAtIntrinsicValueOrZero() {
        var call = contract(Kind.CE);
        var opened = DerivativeBook.trade(call, null, Action.OPEN_LONG, 2, price("80"), CASH);
        assertThat(opened.cashChange()).isEqualByComparingTo("-10400");
        var close = DerivativeBook.trade(call, opened.position(), Action.CLOSE, 1, price("90"), CASH);
        assertThat(close.cashChange()).isEqualByComparingTo("5850");
        assertThat(close.realizedPnl()).isEqualByComparingTo("650");
        assertThat(DerivativeBook.expiryPrice(call, price("24100"))).isEqualByComparingTo("100");
        assertThat(DerivativeBook.expiryPrice(contract(Kind.PE), price("23900"))).isEqualByComparingTo("100");
        var expired = DerivativeBook.trade(call, close.position(), Action.EXPIRY, 1,
            DerivativeBook.expiryPrice(call, price("23900")), CASH);
        assertThat(expired.cashChange()).isEqualByComparingTo("0");
        assertThat(expired.realizedPnl()).isEqualByComparingTo("-5200");
    }

    @Test
    void rejectsUnsupportedWritingInsufficientFundsAndOversells() {
        var put = contract(Kind.PE);
        assertThatThrownBy(() -> DerivativeBook.trade(put, null, Action.OPEN_SHORT, 1, price("80"), CASH))
            .isInstanceOf(TradingValidationException.class);
        assertThatThrownBy(() -> DerivativeBook.trade(put, null, Action.OPEN_LONG, 1, price("80"), price("5199")))
            .isInstanceOf(TradingValidationException.class);
        assertThatThrownBy(() -> DerivativeBook.trade(contract(Kind.FUT), null, Action.OPEN_LONG, 1, price("24000"), price("311999")))
            .isInstanceOf(TradingValidationException.class);
        var opened = DerivativeBook.trade(put, null, Action.OPEN_LONG, 1, price("80"), CASH);
        assertThatThrownBy(() -> DerivativeBook.trade(put, opened.position(), Action.CLOSE, 2, price("90"), CASH))
            .isInstanceOf(TradingValidationException.class);
    }

    @Test
    void parsesOnlySupportedIndexContractsUsingProviderLotTickAndExpiry() {
        String fixture = """
            {"segment":"NSE_FO","exchange":"NSE","underlying_type":"INDEX","underlying_key":"NSE_INDEX|Nifty 50",
             "instrument_key":"NSE_FO|50978","trading_symbol":"NIFTY 27000 CE 29 DEC 26","instrument_type":"CE",
             "lot_size":65,"tick_size":5,"expiry":1798568999000,"strike_price":27000}
            """;
        var mapper = new ObjectMapper();
        var enabled = new DerivativeCatalog(null, true);
        Contract parsed = enabled.parse(mapper.readTree(fixture)).orElseThrow();
        assertThat(parsed.expiry()).isEqualTo(LocalDate.of(2026, 12, 29));
        assertThat(parsed.lotSize()).isEqualTo(65);
        assertThat(parsed.tickSize()).isEqualByComparingTo("0.05");
        assertThat(parsed.strike()).isEqualByComparingTo("27000");
        assertThat(enabled.parse(mapper.readTree(fixture.replace("INDEX\"", "EQUITY\"")))).isEmpty();
        assertThat(new DerivativeCatalog(null, false).parse(mapper.readTree(fixture))).isEmpty();
    }

    private Contract contract(Kind kind) {
        return new Contract(UUID.randomUUID(), "NSE_FO|123", "NSE_INDEX|Nifty 50", "Fixture", kind,
            LocalDate.of(2026, 12, 29), 65, price("0.05"), kind == Kind.FUT ? null : price("24000"));
    }
    private BigDecimal price(String value) { return new BigDecimal(value); }
}
