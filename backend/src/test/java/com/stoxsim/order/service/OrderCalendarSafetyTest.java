package com.stoxsim.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.calendar.service.IndiaMarketSessionService;
import com.stoxsim.calendar.repository.MarketHolidayRepository;
import com.stoxsim.instrument.domain.*;
import com.stoxsim.order.domain.*;
import com.stoxsim.order.repository.PaperOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class OrderCalendarSafetyTest {
    @Test void expiryRechecksTheSessionAndDoesNotReleaseCashBeforeUsClose() {
        var orders=mock(PaperOrderRepository.class);var accounts=mock(VirtualAccountRepository.class);
        var sessions=mock(IndiaMarketSessionService.class);var events=mock(ApplicationEventPublisher.class);
        var service=new OrderApplicationService(accounts,null,null,orders,null,sessions,null,null,null,events,null,null,null,null);
        var order=mock(PaperOrder.class);var account=mock(VirtualAccount.class);var instrument=mock(TradableInstrument.class);
        UUID id=UUID.randomUUID(),accountId=UUID.randomUUID();var day=LocalDate.of(2026,11,27);
        when(orders.findById(id)).thenReturn(Optional.of(order));when(orders.findByIdForUpdate(id)).thenReturn(Optional.of(order));
        when(order.getStatus()).thenReturn(OrderStatus.OPEN);when(order.isOpen()).thenReturn(true);
        when(order.getAccount()).thenReturn(account);when(account.getId()).thenReturn(accountId);
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(order.getInstrument()).thenReturn(instrument);when(instrument.getExchange()).thenReturn(MarketExchange.NASDAQ);
        when(instrument.getProvider()).thenReturn("ALPACA");when(instrument.getInstrumentKey()).thenReturn("AAPL");
        when(instrument.getMarketRegion()).thenReturn(com.stoxsim.market.domain.MarketRegion.UNITED_STATES);
        when(order.getSubmittedForDate()).thenReturn(day);when(order.getSide()).thenReturn(OrderSide.BUY);
        when(order.getReservedCash()).thenReturn(BigDecimal.TEN);
        service.expire(id);
        verify(account,never()).releaseReservedCash(any());verify(order,never()).markExpired();
        when(sessions.hasSessionEnded(MarketExchange.NASDAQ,day)).thenReturn(true);
        service.expire(id);
        verify(account).releaseReservedCash(BigDecimal.TEN);verify(order).markExpired();
    }
    @Test void previousDayOrderCannotFillWhileWaitingForExpiryScan() {
        var sessions=new IndiaMarketSessionService(Clock.fixed(Instant.parse("2026-10-21T14:00:00Z"),ZoneOffset.UTC),mock(MarketHolidayRepository.class));
        var prices=mock(ExecutionPriceCalculator.class);
        var service=new OrderSettlementService(null,null,null,null,null,prices,null,sessions,null,null);
        var order=mock(PaperOrder.class);var account=mock(VirtualAccount.class);var instrument=mock(TradableInstrument.class);
        when(order.isOpen()).thenReturn(true);when(account.isActive()).thenReturn(true);
        when(order.getInstrument()).thenReturn(instrument);when(instrument.getExchange()).thenReturn(MarketExchange.NASDAQ);
        when(order.getSubmittedForDate()).thenReturn(LocalDate.of(2026,10,19));
        assertThat(service.settleOpenOrder(order,account,null)).isFalse();verifyNoInteractions(prices);
    }
}
