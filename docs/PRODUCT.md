# StoxSim Product Definition

## Product statement

StoxSim is a multi-market paper-trading platform that helps people learn how Indian and United States stock markets work without risking real money.

## MVP users

- Students and first-time investors
- Learners comparing Indian and US market mechanics
- Investors testing decisions before committing capital

## Core user journey

1. Create an account.
2. Receive an India account with ₹5,00,000 and a US account with $10,000.
3. Select a market.
4. Search stocks and ETFs.
5. Inspect quotes, charts and market status.
6. Submit a simulated market or limit order.
7. Track holdings, cash, orders and performance.
8. Sell holdings and inspect the resulting trade and ledger entries.

New learners first receive a three-part educational introduction, followed by a
persisted two-step search-to-order walkthrough. The server, rather than the
browser, records completion of the first accepted paper order.

## MVP scope

- Email-based registration and login
- Separate India and US virtual accounts
- NSE cash equities and ETFs
- NASDAQ and NYSE equities and ETFs
- Market and limit orders
- Delivery/long-only positions
- Watchlists
- Holdings, trades, orders and cash ledger
- Realized and unrealized profit/loss
- Explainable StoxScore portfolio-structure analytics
- Educational FinWiz feedback after an executed paper trade
- Cash and position allocation views
- Fee-adjusted realized and unrealized performance attribution
- Market session enforcement
- Explicit LIVE, DELAYED, CLOSED and STALE labels
- Responsive web application
- Resumable guided onboarding and first-trade walkthrough

## Out of scope

- Real-money orders
- Derivatives and options
- Short selling, leverage and margin
- Fractional shares
- Social trading and leaderboards
- AI-generated investment advice
- Currency conversion between accounts

## Product rules

- INR and USD accounts never share balances or holdings.
- PostgreSQL is the source of truth for financial state.
- Redis is used only for transient market data and caching.
- Financial calculations use decimal arithmetic.
- The backend enforces sessions, quote freshness and authorization.
- Provider credentials never reach the browser.
- StoxSim never calls a broker's real-order API.
