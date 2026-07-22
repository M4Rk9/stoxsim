# Simulated Indian Charges

StoxSim applies a versioned educational charge schedule to NSE delivery trades. Every response and persisted trade is explicitly marked `simulated`; the calculation is not a broker contract note or tax statement.

## Current schedule

`SIM-IN-DELIVERY-2026-03` is effective from 1 March 2026:

| Component | Simulated rule |
|---|---|
| Brokerage | ₹0 by default because brokerage is provider-specific |
| STT | 0.1% on delivery buys and sells |
| NSE transaction charge | 0.00307% on buys and sells |
| SEBI charge | ₹10 per crore, equivalent to 0.0001% |
| GST | 18% of brokerage, exchange and SEBI components |
| Stamp duty | 0.015% on delivery buys only |
| DP charge | ₹0 by default because depository and broker fees vary |

The prior `SIM-IN-DELIVERY-2024-10` schedule retains the 0.00297% NSE transaction rate so old trade dates remain reproducible.

## Settlement behavior

- Buy reservations include estimated charges.
- The final execution recalculates charges from the simulated fill price.
- Buy charges become part of the holding's weighted average cost.
- Sell charges reduce cash proceeds and realized profit/loss.
- The cash ledger records the net debit or credit.
- Trades store every component, the schedule version and the simulated flag.

## References

- [NSE overview of STT, SEBI fees and other levies](https://www.nseindia.com/static/invest/first-time-investor-sebi-turnover-fees-stt-other-levies)
- [Income Tax Department STT rates](https://www.incometaxindia.gov.in/w/section-98-55)
- [NSE stamp-duty table](https://www.nseindia.com/static/invest/first-time-investor-stamp-duty-charges-taxes)
- [Upstox published brokerage and exchange-charge schedule](https://upstox.com/brokerage-charges/)
- [CBIC stock-broker GST guidance](https://cbic-gst.gov.in/sectoral-faq.html)

Rates can change. A new effective date must be represented by a new schedule version rather than mutating an old version.
