# Virtual NSE index derivatives

Futures & Options lives under Markets and uses a separate ₹5,00,000 virtual wallet. The initial catalog accepts Upstox NSE index contracts for Nifty 50 and Nifty Bank. Equity derivatives and option writing are outside this model.

- Catalog import reuses the Upstox JSON master. Contract identity includes the provider key and expiry. The stored lot, strike, and tick of a contract cannot change while reimporting that identity.
- Orders are immediate educational market orders. The server requires a regular verified NSE session, an unexpired contract, a matching quote received within 15 seconds, and an exchange last trade within 60 seconds. A buy uses best ask and a sell uses best bid when present, otherwise the verified last trade. Prices are rounded to the nearest tick. Missing quotes never produce virtual prices.
- Long options pay full premium. Long and short futures reserve a flat 20% of opening notional. These are teaching rules, with no fees, daily cash mark-to-market, or forced liquidation. At close the wallet releases margin proportionately and books realized P&L. Futures losses can exceed reserved margin and leave negative virtual cash. New positions require sufficient available cash.
- At expiry, the scheduler uses the verified daily close of the exact underlying index on the contract expiry date once that date has ended in IST. Futures settle at the index level, calls at max(index close − strike, 0), and puts at max(strike − index close, 0). Missing verified settlement data leaves the position pending. The job retries and records one fill as the position is removed under the wallet lock.
- Cash, positions, and fill history are independent of cash equities and competitions. Equity and unrealized P&L are unavailable when a required mark is stale. Account export includes virtual derivatives and account deletion cascades to the wallet data.

## Rollout

The `STOXSIM_INDEX_DERIVATIVES_ENABLED` switch defaults to false. Before activating it, confirm the written provider scope for NSE derivatives and underlying settlement data. In isolated staging, run a complete master sync and confirm quote identity, depth, last-trade timestamps, verified calendars and expiry values on representative active contracts. Match historical index closes to exchange settlement levels for both indices. Then validate opens, partial closes, retries, stale quotes, and expiry against the real provider. The full-market quote endpoint must be available to the actual Upstox credentials. A fixture or public catalog alone does not verify that entitlement.

- [Upstox instrument file](https://upstox.com/developer/api-documentation/instruments/)
- [Upstox full market quote](https://upstox.com/developer/api-documentation/get-full-market-quote/)
- [NSE equity derivative specifications](https://www.nseindia.com/static/products-services/equity-derivatives-contract-specifications)
- [NSE settlement price rules](https://www.nseindia.com/static/products-services/equity-derivatives-settlement-price)
