package com.stoxsim.order.service;

import com.stoxsim.market.data.InstrumentKey;

public record OrderClosedEvent(InstrumentKey instrument) {
}
