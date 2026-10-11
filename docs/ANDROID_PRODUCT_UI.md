# Android product navigation — 0.4.0

The owner's reference screens guide the layout, while StoxSim keeps its own logo,
light/dark colours and simulator identity. This is native Compose.

| Area | Behaviour |
| --- | --- |
| Bottom navigation | Stocks, Finwiz AI, Scenario Lab |
| Stocks tabs | Explore, Holdings, Orders, Watchlist |
| Top search | Symbol/company search for instruments supported in the selected India/US market; keyboard Search or magnifier submits |
| Stocks Explore | Compact index cards, recently viewed symbols, real market movers, investments summary and tools |
| Top-right account | Profile avatar opens account hub |
| Account hub | India/US market, standard/sandbox account, light/dark appearance, wallet, subscriptions, profile/security, competitions/campus and reports |
| Wallet | Account-scoped available/reserved virtual cash, starting capital and ledger; no real deposits/withdrawals |
| Subscriptions | Existing plan, dates, entitlements, plan catalogue and eligible additional sandbox; Play purchasing remains pending |
| Finwiz Learn | Eight topic cards, each with three bundled lessons, optional local read marker and explicit Ask Finwiz action |
| Finwiz Chats | New chat/topic/experience/optional symbol, user/assistant bubbles, suggested questions, send state, errors, reopen/delete conversations |

Lesson topic IDs match `frontend/app/finwiz/FinwizReactor.tsx` and the existing
`FinwizRequest.Topic` contract. Reading a lesson does not call AI or award server
XP. Local read status is keyed by the signed-in email on this device. Introductory
text is authored for StoxSim, with investor education links for further reading:

- SEC financial-statement guide: https://www.sec.gov/investor/pubs/begfinstmtguide.htm
- Investor.gov order types: https://www.investor.gov/introduction-investing/general-resources/news-alerts/alerts-bulletins/investor-bulletins-14
- Investor.gov diversification: https://www.investor.gov/introduction-investing/getting-started/asset-allocation

AI requests use the existing endpoint and server account/service limits. The latest
question stays intact; up to four recent user/assistant messages are included when
they fit the 2,000-character request contract. Older context may be omitted. Errors
are not sent as conversation context. Nothing is submitted by opening a lesson,
selecting a suggestion, reopening a conversation or changing tabs.

Chats are memory-only, bounded to ten conversations and twenty question/answer
pairs per conversation. They disappear after sign-out or process death; no server
history endpoint or persistent chat database is invented. Chats retain their market
and cannot be reopened from a different selected market. Requests are not replayed
automatically after a network/authentication failure. Recent stock history is also
session-only and contains viewed instrument identities, without invented prices.

## Acceptance

Android CI compiles debug/release/test APKs, runs unit tests and Android 15 device
tests, and captures Stocks, Finwiz modules/lesson/chat, Account and Wallet in light
and dark appearance using synthetic data. See `android-device-reports` artifacts.
No production AI requests or orders are made by tests.

On the owner's device:

1. Check version 0.4.0 and all three bottom tabs. Open Holdings, Orders and Watchlist.
2. Search a symbol using keyboard Search and the magnifier. Switch IND/US in Account;
   verify currency/results/wallet change and stale results clear.
3. Read a Finwiz module, mark it read, open its chat and explicitly send a question.
   Ask a follow-up, reopen/delete a chat and switch markets. Observe request errors
   honestly and verify no background request happens merely from navigation.
4. Check appearance, a small screen, keyboard, rotation, Back and large fonts.
5. Compare virtual wallet/ledger and plan benefits with the website. Purchases and
   real-money wallet operations are not part of this change.
