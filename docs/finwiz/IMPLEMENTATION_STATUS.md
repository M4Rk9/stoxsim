# Finwiz reliability and monetisation programme
## F1 — provider gateway (candidate, not complete)
Base main b8e9897; existing redesign deployed. Implemented Groq, Gemini, Cloudflare Workers AI and OpenRouter adapters, fixed preferred order, opt-in flags, provider attempt counters, bounded one attempt per provider, local cooldown/Retry-After, concurrency semaphores, request leases, idempotent success settlement and aggregate reported token counters. Existing server-selected depth and fallback contract retained. Updated local/staging/production compose, optional protected deployment configuration and staging smoke; Gemini preflight still required in production deployment.
Tests added for four-provider chain, cooldown, refusal, disabled providers, successful allowance and DB concurrency. Fresh CI and live provider acceptance pending. No real credentials or paid calls used.
Remaining F1 gates: token-rate admission beyond attempt limits; validate provider timeout behaviour incl. slow bodies; confirm client retry/idempotency across independent HTTP submissions; review live provider model eligibility, account caps, commercial/privacy terms and production preflight; inspect CI and fix any failures.
## Later milestones (not started)
- F2: ₹199 Plus / ₹499 Pro, tier capacity and spending.
- F3: knowledge base, cache, context and financial validation.
- F4: referrals, ₹100000 INR and $1000 USD virtual capital ledger.
- F5: monitoring, billing, Google Play candidate.
- F6: post-publication advertising only; each qualifying ad earns same virtual rewards. No ad SDK before launch.
