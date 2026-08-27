# Learning progression

StoxSim learning progression is a versioned, educational reward system. It is
designed to help learners discover product concepts and return for reflection;
it does not score investment skill or reward simulated profit, capital, trade
size, trade volume, or leaderboard position.

## API

- `GET /api/v1/progression` reconciles authoritative simulator milestones and
  returns the current challenges, missions, level, streak and achievements.
- `POST /api/v1/progression/check-in` records at most one learning check-in per
  local date and returns the updated progression state.

Both endpoints require the existing bearer authentication. The check-in uses the
timezone selected for weekly reports, or `Asia/Kolkata` when no preference
exists. Moving to an earlier local date never awards another check-in.

## Version 1 missions

| Challenge | Mission | XP | Authoritative source |
| --- | --- | ---: | --- |
| Foundations | Complete the learning introduction | 50 | Persisted onboarding state |
| Foundations | Build the first watchlist | 40 | Watchlist-item count |
| Foundations | Place the first paper order | 50 | Backend first-order milestone |
| Practice | Complete the first simulated trade | 75 | Trade ledger |
| Practice | Explore both market accounts | 100 | At least one trade in each isolated account |
| Practice | Build a three-position portfolio | 125 | Active holding count |
| Consistency | Check in for three learning days | 100 | Consecutive server-dated check-ins |

The catalog identifier is `learning-progression-v1`. Mission definitions are
server-owned and clients cannot submit XP amounts or mark arbitrary missions as
complete.

## Integrity and concurrency

- `learner_progression` is locked per user while reconciliation runs.
- `(user_id, mission_code)` is unique, so a mission cannot award XP twice.
- `(user_id, achievement_code)` is unique, so unlocks are idempotent.
- Existing users receive a progression row in migration `V105`; their completed
  simulator milestones reconcile the first time they open the learning path.
- New users are initialized lazily with a PostgreSQL `ON CONFLICT DO NOTHING`
  boundary before the row lock is taken.
- Streaks do not award repeated daily XP. Only the single three-day mission has
  an XP award.

## Privacy and deletion

The account export includes progression, mission completions and achievements.
All three tables reference the account with `ON DELETE CASCADE`. Metrics contain
only mission or achievement codes and aggregate outcomes; they do not contain
email addresses or portfolio contents.

## Product boundary

XP and achievements must remain separate from competitive portfolio ranking.
The next leaderboard batch must rank only comparable standard portfolios and
must never convert progression XP into financial performance or an advantage in
a competition.
