package com.stoxsim.derivative;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.stoxsim.derivative.DerivativeCatalog.Contract;
import com.stoxsim.derivative.DerivativeCatalog.Kind;
import com.stoxsim.order.service.TradingValidationException;

final class DerivativeBook {
    static final BigDecimal MARGIN_RATE = new BigDecimal("0.20");
    static final BigDecimal STARTING_CASH = new BigDecimal("500000.0000");

    enum Action { OPEN_LONG, OPEN_SHORT, CLOSE, EXPIRY }
    enum Direction { LONG, SHORT }

    record Position(int lots, Direction direction, BigDecimal averageEntry, BigDecimal reservedMargin) {}
    record Movement(Position position, BigDecimal cashChange, BigDecimal realizedPnl) {}

    static Movement trade(Contract contract, Position existing, Action action, int lots, BigDecimal price, BigDecimal cash) {
        if (lots < 1 || lots > 100 || price.signum() < 0 || (action != Action.EXPIRY && price.signum() == 0)) {
            throw new TradingValidationException("Invalid lots or fill price.");
        }
        if (action == Action.CLOSE || action == Action.EXPIRY) return close(contract, existing, lots, price);

        Direction direction = action == Action.OPEN_LONG ? Direction.LONG : Direction.SHORT;
        if (direction == Direction.SHORT && contract.kind() != Kind.FUT) {
            throw new TradingValidationException("Options support buying to open and selling to close.");
        }
        if (existing != null && existing.direction() != direction) {
            throw new TradingValidationException("Close the existing position before switching direction.");
        }
        int previousLots = existing == null ? 0 : existing.lots();
        int combinedLots = previousLots + lots;
        if (combinedLots > 100) throw new TradingValidationException("A position may contain up to 100 lots.");

        BigDecimal notional = value(contract, lots, price);
        BigDecimal margin = contract.kind() == Kind.FUT ? money(notional.multiply(MARGIN_RATE)) : BigDecimal.ZERO;
        BigDecimal debit = contract.kind() == Kind.FUT ? margin : notional;
        if (debit.signum() <= 0 || cash.compareTo(debit) < 0) {
            throw new TradingValidationException("Insufficient virtual funds for premium or futures margin.");
        }
        BigDecimal average = existing == null ? price : existing.averageEntry().multiply(BigDecimal.valueOf(previousLots))
            .add(price.multiply(BigDecimal.valueOf(lots))).divide(BigDecimal.valueOf(combinedLots), 8, RoundingMode.HALF_UP);
        BigDecimal reserved = existing == null ? margin : existing.reservedMargin().add(margin);
        return new Movement(new Position(combinedLots, direction, average, reserved), debit.negate(), BigDecimal.ZERO);
    }

    private static Movement close(Contract contract, Position existing, int lots, BigDecimal price) {
        if (existing == null || lots > existing.lots()) throw new TradingValidationException("Not enough open lots to close.");
        int remaining = existing.lots() - lots;
        BigDecimal released = remaining == 0 ? existing.reservedMargin()
            : existing.reservedMargin().multiply(BigDecimal.valueOf(lots))
                .divide(BigDecimal.valueOf(existing.lots()), 4, RoundingMode.HALF_UP);
        BigDecimal pnl = pnl(contract, existing, lots, price);
        BigDecimal cashChange = contract.kind() == Kind.FUT ? released.add(pnl) : value(contract, lots, price);
        Position position = remaining == 0 ? null
            : new Position(remaining, existing.direction(), existing.averageEntry(), existing.reservedMargin().subtract(released));
        return new Movement(position, money(cashChange), pnl);
    }

    static BigDecimal pnl(Contract contract, Position position, int lots, BigDecimal mark) {
        BigDecimal move = mark.subtract(position.averageEntry());
        return value(contract, lots, position.direction() == Direction.LONG ? move : move.negate());
    }

    static BigDecimal value(Contract contract, int lots, BigDecimal price) {
        return money(price.multiply(BigDecimal.valueOf((long) contract.lotSize() * lots)));
    }

    static BigDecimal expiryPrice(Contract contract, BigDecimal underlyingClose) {
        return switch (contract.kind()) {
            case FUT -> underlyingClose;
            case CE -> underlyingClose.subtract(contract.strike()).max(BigDecimal.ZERO);
            case PE -> contract.strike().subtract(underlyingClose).max(BigDecimal.ZERO);
        };
    }

    static BigDecimal money(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }
}
