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

## Provider configuration

```text
OPENAI_API_KEY=
OPENAI_MODEL=gpt-5-mini
FINWIZ_AI_ENABLED=true
FINWIZ_MAX_QUESTION_CHARACTERS=2000
FINWIZ_MAX_OUTPUT_TOKENS=900
```

The API key is optional. When it is absent, disabled or the provider request fails, Finwiz returns a deterministic educational fallback instead of making the feature unavailable.

OpenAI requests use the Responses API with `store=false`. Keys remain backend-only and must never be included in browser code, logs or source control.

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
