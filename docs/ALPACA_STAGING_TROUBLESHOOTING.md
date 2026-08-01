# Alpaca staging troubleshooting

The staging deployment validates the Alpaca credentials before changing the running application.

## Preflight checks

The deployment calls Alpaca directly and verifies:

1. The configured credentials can read the active SPY asset.
2. The selected market-data feed returns a usable SPY snapshot.

A failure at this stage indicates invalid credentials, an unsupported feed entitlement, or an upstream Alpaca outage. The existing staging deployment is not changed.

## Browser acceptance

The deployed browser journey waits for the asynchronous Alpaca instrument catalogue to contain AAPL before it validates the benchmark cards. This prevents the deployment from rolling back merely because the first full US catalogue synchronization is still running on a small staging server.

## Failure diagnostics

When a deployment reaches the new backend but US market verification fails, the workflow prints filtered backend log lines containing Alpaca, market-data, warning, exception, and error messages before rolling back.
