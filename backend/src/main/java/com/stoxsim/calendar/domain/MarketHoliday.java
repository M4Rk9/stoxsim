package com.stoxsim.calendar.domain;

import java.time.LocalDate;
import java.util.UUID;

import com.stoxsim.instrument.domain.MarketExchange;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "market_holiday")
public class MarketHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MarketExchange exchange;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false, length = 160)
    private String description;

    protected MarketHoliday() {
    }

    public MarketExchange getExchange() {
        return exchange;
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public String getDescription() {
        return description;
    }
}
