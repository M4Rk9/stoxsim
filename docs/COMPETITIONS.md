# Seasonal competitions and private leagues

StoxSim competitions are optional educational comparisons between equal
standard portfolios. They do not measure investing skill, predict future
performance or provide investment advice.

## Eligibility and isolation

- The only eligible account is the learner's standard India portfolio with a
  configured starting capital of exactly ₹5,00,000.
- Future Plus ₹25 lakh and Pro ₹1 crore sandboxes must use different accounts
  and cannot enter this competition model.
- XP, level, achievements, trade size and absolute simulated profit are never
  inputs to ranking.
- Enrollment is explicit. A learner who does not opt in does not appear in the
  global standings.

## Season and scoring model

The API creates one UTC calendar-quarter season lazily. A learner's current
standard account value is captured as their immutable entry baseline. Ranking
uses:

```text
entry return % = (latest account value - entry baseline) / entry baseline × 100
```

The scoring identifier is `standard-india-entry-return-v1`. Each standing shows
the join time, last valuation time and price-data status so learners can
understand freshness and the limitations of comparing entries made at different
times. Ties share a rank and are ordered by earlier enrollment for stable
display.

Only the requesting learner's valuation is refreshed when they view a board.
This prevents a leaderboard read from fanning out into market-data calls for
every member. Other rows retain their last disclosed valuation time. A held
portfolio cannot enroll while its prices are unavailable, and an unavailable
refresh does not overwrite its last usable score. A cash-only account may enter
when markets are closed or provider status is unavailable because no quote is
required to value it.

## Private leagues

- A league belongs to the current quarterly season and supports at most 25
  members.
- A learner can own at most five leagues per season.
- Creating or joining a league also enrolls the same eligible standard account
  in the season.
- League existence and standings are available only to members. Unauthorized
  resource lookups return `404`.
- Owners can rotate an invite or delete a league. Members can leave; owners must
  delete instead.
- Invite codes contain 144 bits of randomness, are displayed only in the create
  or rotate response, and are stored only as SHA-256 hashes. Join attempts use a
  bounded rate-limit policy.

## API

- `GET /api/v1/competitions/current`
- `POST /api/v1/competitions/current/enroll`
- `GET /api/v1/leagues`
- `POST /api/v1/leagues`
- `POST /api/v1/leagues/join`
- `GET /api/v1/leagues/{leagueId}`
- `POST /api/v1/leagues/{leagueId}/invite/rotate`
- `POST /api/v1/leagues/{leagueId}/leave`
- `DELETE /api/v1/leagues/{leagueId}`

All routes require the existing bearer authentication. The global board is
limited to 50 rows and a private board to its 25-member capacity.

## Privacy and deletion

Opt-in copy tells the learner which profile and competition fields become
visible. Global standings expose display name, entry-relative return, join time,
valuation time and data status. Private standings expose the same fields only to
members. Raw account value and the immutable entry baseline are returned only
to the learner who owns them.

Account export includes competition entries, owned-league metadata and
memberships; it deliberately excludes invite-code hashes. Entries and
memberships are deleted by account cascade. Deleting an owner account deletes
the leagues they own and their memberships.

No new market-data provider, secret or paid infrastructure is required.
