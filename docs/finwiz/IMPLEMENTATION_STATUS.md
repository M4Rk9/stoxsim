# Finwiz reliability and monetisation programme
## F1 — provider gateway (implementation candidate)
Base main b8e9897. PR #206.
- Four adapters (Groq, Gemini, Cloudflare Workers AI, pinned OpenRouter free upstream), fixed preferred order, opt-in flags, one bounded attempt per provider, cooldown/Retry-After, concurrency semaphores and durable attempt limits. Existing server-selected depth and fallback contract retained.
- V137 request leases, successful allowance settlement and aggregate reported token counters. Earlier CI 46039840263, CodeQL 46039840474 and dependency review 46039840258 passed on 2eabf54.
- V138 durable rolling 60-second request and estimated-token admission plus daily token estimates. Conservative UTF-8-byte estimate, completion allowance and reasoning headroom; not actual provider tokenization, neuron accounting or billing guarantee.
- Full-body network timeouts with JDK async cancellation, bounded 256 KiB response body and shared dispatch deadline; DB and market-context latency are outside the network deadline. Mock slow-body failover regression.
- V139 optional user-scoped client request UUID; web and Android generate UUID once per submission; duplicates return 409 rather than storing private responses for replay. Existing clients without IDs retain compatibility. Retention approximately 24–48h; duplicate protection does not cover different UUIDs.
- Production preflight checks enabled providers independently, one probe each, requires at least one complete answer, no mandatory Gemini secret. New provider flags remain off by default. No real credentials, paid inference or deployment used.
- Fresh CI on the complete candidate and real account/model/terms/cap verification remain release gates. Rate caps are application admission estimates; actual provider-side limits and spending caps must be checked before activation. Per-process cooldown and concurrency are not distributed.
## Later milestones (not started)
- F2: ₹199 Plus / ₹499 Pro, tier capacity and spending.
- F3: knowledge base, cache, context and financial validation.
- F4: referrals, ₹100000 INR and $1000 USD virtual capital ledger.
- F5: monitoring, billing, Google Play candidate.
- F6: post-publication advertising only; each qualifying ad earns same virtual rewards. No ad SDK before launch.
