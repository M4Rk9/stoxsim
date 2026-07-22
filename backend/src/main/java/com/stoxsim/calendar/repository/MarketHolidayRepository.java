package com.stoxsim.calendar.repository;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stoxsim.calendar.domain.MarketHoliday;
import com.stoxsim.instrument.domain.MarketExchange;

public interface MarketHolidayRepository extends JpaRepository<MarketHoliday, UUID> {

    boolean existsByExchangeAndHolidayDate(MarketExchange exchange, LocalDate holidayDate);
}
