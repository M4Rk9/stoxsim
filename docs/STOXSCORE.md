# StoxScore portfolio structure model

StoxScore is a transparent educational indicator of portfolio breadth and
concentration. It is not a forecast, a security rating or investment advice.
The score uses only the learner's current paper-portfolio valuation and does
not require a new market-data provider.

## Version 1 formula

The API identifies this model as `stoxscore-portfolio-v1`. Only positive-value
holdings participate in the calculation. Position weights are based on each
holding's share of total invested market value.

| Component | Weight | Component score |
| --- | ---: | --- |
| Portfolio breadth | 35% | Holding count divided by the learning target of 8, capped at 100. |
| Weight balance | 45% | Effective holdings, `1 / sum(position weight squared)`, mapped from 1 to 8 and capped at 100. |
| Largest-position concentration | 20% | 100 at or below a 20% largest weight, 0 at or above 80%, and linear between those points. |

Each component is rounded to a whole number before the weighted total is
calculated. The final score is rounded and constrained to 0–100.

| Score | Educational structure band |
| ---: | --- |
| 0–39 | Concentrated |
| 40–59 | Developing |
| 60–79 | Diversified |
| 80–100 | Broadly diversified |

The bands describe portfolio structure only. They do not imply suitability,
expected performance or a recommendation to trade.

## Data quality

- An empty portfolio is not scored.
- Pricing coverage is the percentage of invested value with a price that is
  not marked `UNAVAILABLE`.
- Coverage below 80% returns `LIMITED_DATA` with low confidence.
- An unavailable aggregate valuation also returns `LIMITED_DATA` with low
  confidence, even when holding-level coverage is complete.
- Partial coverage or stale aggregate valuation returns medium confidence.
- Complete, non-stale valuation returns high confidence.
- Where a holding price is unavailable, the underlying portfolio valuation
  may use its existing fallback; the response explicitly reports that limit.

## API

Authenticated clients request:

`GET /api/v1/portfolio/analytics?marketRegion=INDIA`

The response includes the formula version, score and band, component scores,
pricing coverage, concentration measures, largest positions, observations,
valuation time and the educational-use disclaimer. The selected market account
remains isolated; India and United States holdings are never mixed.

## Model changes

Any change to component definitions, weights, thresholds or rounding requires
a new formula version and updated tests. Historical scores must retain the
version that produced them when score snapshots are introduced.
