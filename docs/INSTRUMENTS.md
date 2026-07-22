# Instruments and Market-Data Boundary

## Instrument identity

StoxSim stores the provider's stable instrument key in addition to the visible exchange symbol. Market-data requests must use the provider key; a symbol alone is not a durable identifier.

## India synchronization

The Upstox complete BOD instrument file is downloaded every weekday at 07:30 Asia/Kolkata.

Supported segments:

- NSE cash equities and ETFs
- BSE cash equities and ETFs
- NSE indices
- BSE indices

Derivatives are deliberately ignored for the MVP.

Each synchronization uses a new batch ID. Instruments are marked inactive only after the complete file has been parsed and every accepted batch has been persisted.

## Search API

```http
GET /api/v1/instruments/search?marketRegion=INDIA&q=Reliance
Authorization: Bearer <access-token>
```

```http
GET /api/v1/instruments/INDIA/NSE/RELIANCE
Authorization: Bearer <access-token>
```

## Provider boundary

The core application depends on `MarketDataProvider`, `Quote`, `Candle` and `InstrumentKey`. Upstox SDK models and future US-provider models must remain inside their adapter packages.
