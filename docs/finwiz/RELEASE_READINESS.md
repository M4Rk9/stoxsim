# Finwiz release readiness

F1 was merged in PR #206. The owner reported a successful production deploy after provider preflight failures. Per-provider live status remains unverified.

## F2 candidate

- Confirm candidate CI for backend, web, deployment and Android. Run controlled provider probes only with authorized credentials.
- Validate account-specific model entitlements, commercial terms, region and privacy eligibility, free-only controls and configured admission values. A provider may return one good response without offering sustainable capacity.
- Benchmark with sanitized realistic prompts in each tier before publishing quotas. F1 per-user ceilings of 10/30/60 remain application defaults, subject to provider and pool availability. In particular, low token or request ceilings can exclude long prompts or leave a lower-priority tier temporarily without capacity.
- For future billing activation, use new Razorpay plan IDs and Play base plans or price cohorts as described in billing documentation. Verify production-price and historical-renewal behavior in approved account environments.
- Reconcile pricing samples, model use and approved budget settings before paid activation.
- During rollback, leave V140–V142 in place and preserve usage and billing history. Once new-priced live agreements exist, use a rollback build that understands their agreement amounts and Play base plans.

F3 is the next milestone after F2 validation and review.
