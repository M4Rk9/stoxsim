# F&O milestone 3, slice 1: discovery and risk review

Tracking: #190. This slice extends existing NSE index futures and long options; it does not add option writing, baskets or broker-equivalent margin.

## User journey
Select underlying and expiry, filter the loaded page by symbol/strike and contract type, select an opening or closing action, enter lots, request a server preview, acknowledge the simulation rules, then submit. Beginner view explains contracts; advanced view adds exact identifiers in the chain. Neither mode bypasses review. Search explicitly covers the loaded page only; pagination remains available independently of filters. Spot, Greeks and open interest are explicitly unavailable.

## Preview contract
Authenticated POST /api/v1/derivatives/preview accepts contractId, action and lots. No wallet, order, position or request-key row is created. Preview and execution share preparation: catalogue lookup, sessions/expiry, position ownership, capacity, fresh provider quote, bid/ask-or-last selection, tick rounding and DerivativeBook calculations. Preview rejects stale data, unsupported option writing, insufficient funds and invalid closes. The order transaction retains its wallet lock and idempotency checks. Execution always revalidates against current state. No database migration or credential/flag change.

The response includes exact contract, units, price, upfront premium/margin, cash change/remaining cash and close P&L. Opening-lot expiry breakeven is entry for futures, strike plus premium for calls, strike minus premium for puts (unreachable negative levels return unavailable). Long-option maximum loss is premium; long-future loss is full notional assuming a nonnegative index; short-future loss is unbounded. These are incremental order-lot calculations, not combined-position risk.

Web review expires after 30 seconds and resets on quantity/contract changes. Confirmation is a web interaction requirement, not a new server authorization token; the existing order API remains compatible with older clients. A preview is neither a quote lock nor an execution guarantee. An uncertain order retry retains the same idempotency key. No automatic order retry is introduced.

## Honest simulation limits
Fees are zero in the existing simulator, not claimed real brokerage/taxes. Bid/ask spread may affect fill; no additional slippage model. Futures reserve flat 20% margin, without daily cash MTM or forced liquidation. Cash can go negative on close. Expiry uses the verified underlying index close; missing data keeps settlement pending. No physical delivery or early exercise. Existing provider permissions and quote freshness rules remain unchanged. Baskets/payoff charts are the next slice.

## Validation
Integration coverage: read-only preview, premium/margin versus loss, call/put breakeven, executable side/tick rounding, close P&L, authentication and ownership, invalid lots/actions, stale quotes, sessions and insufficient funds. Existing fill/partial-close/idempotency/expiry tests protect financial behavior.
Browser coverage: discovery filtering, mandatory review/acknowledgement, quantity invalidation, expired review, retry idempotency and mobile overflow. Main desktop/mobile screenshots are synthetic fixtures attached to the browser report. CI results and final review evidence belong on the PR. Native F&O UI and physical Android-device testing are not part of this web slice.
