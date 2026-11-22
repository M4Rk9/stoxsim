# Native research and F&O parity

Extends product issue #190 and the backend/web subset merged in #196. No migration, provider permission, billing or feature-flag changes.

## Delivered

Markets links to native Compose F&O. Discovery offers Nifty/Bank Nifty, expiry, 25-strike pages, local symbol/strike/type filters, beginner explanations and advanced identifiers/bid/ask/volume. Stale or unavailable quotes disable opening tickets. The separate INR wallet, positions, pending settlement and recent fills remain visible. Positions close through a reviewed ticket.

Single-order and 2–4-leg opening-basket previews use the existing authenticated backend endpoints; calculations are not duplicated in Android. Review requires acknowledgement, expires after 30 seconds using a monotonic clock, and is invalidated by edits/refresh. Exact identifiers, lot sizes, quantity, premium/margin, cash, loss bounds, breakevens and combined expiry payoff are shown with an accessible value list. No option writing, cross-index/calendar baskets, margin offsets or invented Greeks/OI.

The ViewModel retains drafts during rotation. It freezes an exact reviewed submission body and request key before transmission, never automatically retries, and allows explicit same-key retry after an uncertain response even if the original preview expired. Users are warned that a retry can execute at current quotes if the first attempt never executed. Editing stays blocked until resolution or explicit discard after checking fills. Discard is not cancellation. Drafts/keys are not persisted across process death or sign-out: check fill history before submitting again after reopening.

Stock detail adds provider-labelled headlines, publication/check times and HTTPS publisher links. Disabled/empty/outage states do not hide stock charts or virtual order actions. No article bodies, caching, analytics payloads or automatic AI ingestion added. News activation remains subject to the existing backend flags/entitlements.

## Validation

Unit coverage checks composition/expiry gates, frozen retry bodies and safe publisher links. Compose instrumentation covers acknowledgement, expired/changed review, disabled trading/news, and light/dark screenshots with labelled synthetic fixtures. Existing Android transport tests cover no write replay and authenticated identity. Build, lint, release shrinking and emulator results are recorded on the PR. Physical-device and real-provider acceptance are separate.

## Owner acceptance after release

Use the matching backend including V135. On a physical Android device: open Markets → F&O; inspect exact contract/quote age; review and place a small virtual order; verify positions/fills; close with review; add two supported legs and inspect payoff before basket submission. Background longer than 30 seconds and verify review expires. Rotate during draft/review. Test lost-response retry using controlled staging tooling, not repeated production taps. Confirm disabled news and licensed enabled news states separately. Never interpret synthetic screenshots or CI as live-provider validation.
