# Private strategy journal contract

`/journal` reuses `/api/v1/reflections`. Optional `plan` has exchange, symbol,
horizon, target, invalidation and ISO calendar `reviewOn`. The pair exchange/symbol
must be complete or both empty; these are user-authored research labels, not a
provider claim. Plans do not place stops/targets or submit orders. Dates produce
an in-app due list in the viewer's local calendar; there is no email/push scheduler.

Optional `completeReview=true` requires nonblank reflection and records the
first server completion timestamp. Ordinary saves never imply review completion.
Targets, assumptions and outcomes remain editable private notes, not immutable
point-in-time evidence or an automated performance attribution model.

Legacy payloads omitting `plan` preserve those fields. Additive V133 is deployed
before new clients; old clients continue to work. The migration does not rewrite
accounts, orders, balances or prior activity. Backups and normal migration checks
are still required; rollback the app, not an already-applied Flyway migration.

`POST /reflections/{id}/order` with `{orderId}` links an existing owned order.
It checks both owners, exact symbol/exchange when specified, and rejects replacing
a different already-linked order. Repeating the same link is safe. It creates no
order. Web's post-submission link failure tells the learner the order succeeded
and offers manual linking; it never resubmits the order. One note links one order
in M1; multiple fills/round-trip attribution are not claimed. After submission,
the web strategy intent is removed from the URL so later orders cannot reuse it.

Responses include the owned order's actual status, side, quantity, symbol,
exchange, currency, execution price and execution time. Pending/rejected orders
have no fabricated execution price. Execution is explicitly distinguished from
closed-position profit. Review asks whether the thesis and assumptions held.

List is bounded at 50, with unreviewed/due notes first. Storage remains capped at
1000/user. New fields are included in account export and existing deletion cascade.
No notes are persisted in browser localStorage or native saved state. Explicit AI
consent/tier limits remain in the existing assistance endpoint, which sends only
its existing bounded text excerpts.

## URL intent and security scanning

The `strategy` query parameter is untrusted navigation input, not a permission.
After order submission, `linkSubmittedOrderToStrategy` consumes the intent and
returns early for absent or malformed IDs before making a link request. Invalid
IDs show manual recovery; a failed link never repeats the order. A UUID shape
check does not prove ownership: the endpoint always uses the authenticated JWT
subject and checks strategy and order ownership in `ReflectionService`.

CodeQL alert #14 (`js/user-controlled-bypass`) flagged the prior positive URL
condition enclosing `authorizedRequest`. Explicit early-rejection guards separate
input validation from the request; no query exclusion or suppression is added.
HTTP integration coverage rejects anonymous requests, foreign strategies, foreign
orders and malformed IDs; browser coverage includes missing/invalid intent,
404/503 recovery, and a second order without reusing the consumed intent.
A successful analysis workflow is not a clean findings check: inspect the separate
CodeQL code-scanning result on the current PR commit before declaring it clear.
