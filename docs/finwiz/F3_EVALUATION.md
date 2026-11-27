# Finwiz F3 evaluation

## Running the checks

Run the backend tests with Java 21, Maven and Docker. The integration tests use disposable PostgreSQL and Redis containers.

```sh
mvn -f backend/pom.xml -Dtest=FinwizF3EvaluationTest,FinwizTierIntegrationTest,FinwizServiceTest test
```

| Case | Expected evidence |
| --- | --- |
| Exact P/E definition | Versioned lesson with a source. No external request reservation. |
| Follow-up to a source-checked concept | The selected lesson or the public concept title provides context. Older Android history is removed before provider dispatch. |
| Price change | Decimal arithmetic, explicit denominator, scope and rounding. |
| Market and portfolio data | The quote route uses timestamped external data. Private paper-portfolio requests use account-scoped valuation and analytics. |
| Invalid output | Guard blocks obvious trading instructions, generated links, unsupported quantitative claims and conflicting quote currency. |
| Redis cache | A corrupted answer is replaced with the approved revision. The request still resolves from PostgreSQL if Redis rethrows a data-access error. |
| Tier isolation | BASIC, EXPANDED and FULL use separate rendered lessons and provider allowances. |
| Provider outage | Existing gateway and settlement tests cover cooldown, failover, timeout and non-charging fallbacks. |

## Baseline comparison and limits

F2 routed educational definitions through the configured providers. The candidate exercises a published P/E question and asserts zero external request reservations. Provider-dependent questions still use the existing admission and settlement logic. These tests establish route behavior and numerical correctness for their fixtures. A representative latency and accuracy benchmark against the deployed F2 build requires a separately approved isolated workload and provider access.

Review the seeded sources and rerun the suite when publishing a later content revision. Generated analysis remains an interpretation subject to conservative output checks. The checker cannot independently validate every statement or reliably separate each number from its metric.
