# Finwiz returns a topic template instead of answering

The text `Finwiz learning mode` / `Beginner framework` identifies the old deterministic fallback, not a Gemini answer. It selected a paragraph by topic and ignored the actual question. Web displayed the same assistant identity as successful AI, and topic suggestions could loop back to the same paragraph.

The fix removes these substitute answers. Failures return a short availability notice and no follow-up suggestions, without leaking raw model context. The existing provider identifier remains for old-client compatibility. Web explicitly labels both newly received failures and saved legacy fallback replies. Successful Gemini answers still use database-selected tiers and existing limits; the prompt now explicitly prioritizes the question over the topic.

## Diagnose the deployed provider failure

A screenshot alone cannot distinguish these causes. On the VPS, inspect only the sanitized Finwiz log lines (run from the directory/configuration normally used to operate production):

```sh
docker compose logs --since=20m backend | grep -E 'Gemini request for Finwiz AI failed|Finwiz provider returned no complete answer|Finwiz AI unavailable|Finwiz usage budget unavailable'
```

These application messages contain only status codes, allowlisted finish reasons or exception class names. Do not paste .env files, API keys, full provider response bodies, tokens or user questions.

- `API_KEY_MISSING` / `DISABLED`: verify the deployed secret is populated / FINWIZ_AI_ENABLED is true. These explicit diagnostics are added by this fix.
- HTTP 400: check the configured model's supported request parameters, including thinking level.
- HTTP 401/403: check key validity, API/project restrictions and entitlement.
- HTTP 404: verify GEMINI_MODEL against models available to that project; a repository default is not proof of availability.
- HTTP 429: check provider quota/rate limits. Do not silently switch to a paid model or bypass local usage limits.
- HTTP 5xx or a transport exception: provider availability/connectivity/timeout needs investigation.
- `MAX_TOKENS`, safety or other finish reason: no complete answer was returned; partial/safety-stopped text is not displayed.
- Usage budget unavailable/reached: inspect the existing Finwiz response metrics (`budget_limited`) and database-backed request allowance. No automatic retry or refund of an ambiguous provider attempt is introduced.

After correcting configuration, submit the two reported questions separately and confirm the response provider is GEMINI and each answer addresses its question. A stock answer should explain ownership; an RSI answer should explain persistent momentum and why overbought/oversold is not a reversal guarantee. Then verify a deliberate provider-failure fixture reports unavailability. Mocked integration tests cannot certify live provider credentials or answer quality.

## F1 multi-provider diagnostics
See docs/finwiz/PROVIDER_SETUP.md. Filter sanitized log lines for 'Finwiz provider', 'Finwiz request settlement', 'Finwiz usage budget' and 'Finwiz AI unavailable'. The provider gateway uses separate durable admission counters; 429 triggers cooldown rather than repeating the same provider. Duplicate client UUID returns 409. Production preflight checks enabled providers independently. Never log provider bodies or keys. A successful CI run is not proof of live free quotas or answer quality.
