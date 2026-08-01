# Finwiz AI

Finwiz AI is StoxSim's beginner-focused market education assistant. It explains financial statements, stock fundamentals, valuation, cash flow, technical indicators, market conditions and portfolio concepts without issuing trading recommendations.

## Product boundary

Finwiz must not provide:

- buy, sell or hold instructions
- target prices
- guaranteed-return claims
- personalized investment advice
- fabricated company data

Company-specific explanations are grounded in StoxSim's verified quote, candle and fundamentals services. Missing or partial data remains explicitly unavailable.

## Endpoint

Authenticated users call:

```http
POST /api/v1/finwiz/ask
Authorization: Bearer <access-token>
Content-Type: application/json
```

Example:

```json
{
  "question": "Explain the cash-flow quality and valuation risks.",
  "topic": "CASH_FLOW",
  "experienceLevel": "BEGINNER",
  "marketRegion": "INDIA",
  "exchange": "NSE",
  "symbol": "RELIANCE"
}
```

The stock fields are optional. General lessons work without market context.

## Gemini provider configuration

```text
GEMINI_API_KEY=
GEMINI_MODEL=gemini-2.5-flash
GEMINI_BASE_URL=https://generativelanguage.googleapis.com
FINWIZ_AI_ENABLED=true
FINWIZ_MAX_QUESTION_CHARACTERS=2000
FINWIZ_MAX_OUTPUT_TOKENS=900
FINWIZ_THINKING_BUDGET=0
```

Staging requires `GEMINI_API_KEY`. The key is synchronized into the backend host's protected `.env` file and is never exposed to the browser. The backend authenticates with the `x-goog-api-key` header and calls Gemini's `generateContent` endpoint.

Gemini 2.5 Flash uses dynamic thinking when no thinking budget is supplied. Finwiz defaults `FINWIZ_THINKING_BUDGET` to `0` because its explanations are bounded educational tasks; this prevents internal thinking tokens from exhausting the configured output budget before visible answer text is produced. The deployment workflow verifies the key, model and text response before changing the staging server.

Local development can omit the key. When Gemini is disabled, unconfigured or temporarily unavailable, Finwiz returns a deterministic educational fallback instead of making the feature unavailable.

Legacy `OPENAI_API_KEY`, `OPENAI_MODEL` and `OPENAI_BASE_URL` entries are removed from the staging `.env` during provider synchronization and are not read by the application.

## Deployment diagnostics

The staging workflow performs two provider checks:

1. A direct Gemini preflight validates `GEMINI_API_KEY`, `GEMINI_MODEL` and the configured thinking budget before deployment.
2. The authenticated StoxSim smoke test calls `/api/v1/finwiz/ask` and requires `provider: GEMINI`.

When Gemini returns an HTTP error, the backend logs its status code and a bounded provider error message without logging the API key. When Gemini returns no visible text, the backend logs the candidate finish reason and prompt block reason. The smoke step prints the returned provider and model and points to the backend diagnostics step.

## Grounding

When a stock is supplied, the backend builds a compact context from:

- current or latest verified quote
- previous close, OHLC and volume
- daily adjusted candles
- 20-session return
- 20-day and 50-day moving averages
- 14-period RSI
- available company profile
- available ratios and sector comparisons
- available quarterly financial metrics

Technical indicators are labelled as descriptions of historical behaviour, not predictions.

## Theme system

The frontend stores the selected theme in `localStorage` under `stoxsim-theme`. An inline layout script applies the saved or operating-system theme before React hydration to avoid a light-theme flash. The user can switch between light and dark mode from every page.

## Production follow-ups

Before unrestricted public launch, add:

- per-user and per-IP rate limits
- usage and cost budgets
- response latency/error metrics
- abuse monitoring and input moderation
- prompt and output regression evaluations
- admin controls to disable the provider without disabling the deterministic lessons
- user-visible data timestamps and provider status
- separate analytics events for opened, asked, answered, fallback and failed states
