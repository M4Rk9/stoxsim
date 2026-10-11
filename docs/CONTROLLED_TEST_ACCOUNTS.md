# Controlled test accounts and production health

Production deploy runs `scripts/production-smoke.sh` once after deploying a
candidate. While registration is open it creates a `Production Smoke` learner
under the monitored inbox's plus-address, tests authentication and the virtual
accounts, then deletes it. Verification mail remains in the inbox after deletion.
The five-minute Production uptime workflow is read-only and creates no learners.

## Inspect leftovers on the VPS

The deployment bundle includes a read-only audit, compatible with the database
both before and after V119:

```bash
cd ~/stoxsim-production
bash audit-test-accounts.sh
bash audit-test-accounts.sh --details
```

Use the actual deployment directory if it differs. The first command prints
counts only. The optional detail command prints up to 100 account identifiers,
emails and timestamps for private review. Do not upload that output publicly.
It includes matching names as review candidates; a name alone does not establish
that an account is controlled or disposable. No passwords/tokens are selected.
The transaction and session are read-only and have a ten-second statement timeout.

The default monitored inbox is `support.stoxsim@gmail.com`. If the production
smoke secret uses a different inbox, run with that base address explicitly:

```bash
PRODUCTION_SMOKE_EMAIL=your-monitored-inbox@example.com bash audit-test-accounts.sh
```

These counts only distinguish reserved/marked test identities. Accounts outside
those identities are not thereby proven to be genuine, engaged users. Actual
production results must be collected on the VPS; CI fixtures are not live evidence.

## Analytics classification

V119 adds the database-owned `app_user.is_test_account` marker. It backfills
the exact reserved identity combinations used by the production smoke, staging
smoke, authorization A/B, load and CI capacity scripts. Production aliases must
match an operator-controlled `controlled_test_mailbox` entry AND the expected
name and numeric suffix. The other scripts require their exact reserved name,
address prefix/suffix and `stoxsim.test` domain. Similar names, other inboxes and
arbitrary `.test` addresses remain ordinary learners.

A BEFORE INSERT trigger marks future reserved accounts in the registration
transaction. The marker survives profile/email changes; it is not accepted in
public registration/profile DTOs and gives no extra privileges or billing rights.
Spoofing a reserved unverified identity remains possible, as with public signup
generally; this is operational classification, not proof of ownership or anti-fraud.
Do not use it to grant access or automatically delete records.

Every owner signup/cohort/order query, DAU/WAU/MAU query, daily activity query,
activation/retention query and activity ingestion excludes marked test learners.
Old activity remains stored but contributes nothing to growth metrics. Account
export includes the marker. Administrators/deleted learners remain excluded too.
The underlying HTTP/server health metrics continue to include test traffic.

An operator can add another controlled production base mailbox and backfill
its reserved aliases with reviewed SQL:

```sql
BEGIN;
INSERT INTO controlled_test_mailbox(base_email)
VALUES ('your-monitored-inbox@example.com') ON CONFLICT DO NOTHING;
UPDATE app_user SET is_test_account = true
WHERE stoxsim_is_controlled_test_identity(email, display_name);
COMMIT;
```

For other acceptance accounts, review exact IDs and mark those IDs explicitly
through the operator database connection. Never mark/delete everyone with a
matching display name or domain. The migration and audit never delete accounts.
For leftover deletion, confirm the identity and retention/billing implications,
take a current backup, and use the existing account deletion flow where possible.
Lost random smoke passwords are not stored or recoverable from workflow logs.

## Checked cleanup

The exit handler now checks exact HTTP 204 deletion and subsequent HTTP 401
session rejection. It retries transient errors up to three times with request
timeouts, and recovers a login using the in-memory random credentials if the
registration response/token was lost or cleanup's session was rejected.
It preserves an earlier test failure; cleanup failure also fails an otherwise
passing workflow. Errors and GitHub step summaries identify the temporary UUID
when known and direct the operator to the private audit. They never print auth
response bodies, tokens, passwords or the generated plus-address.
An interrupted/killed runner can still miss cleanup: the marker protects metrics
and the VPS audit identifies accounts for review.

## Check backend health and zombie processes

```bash
curl --fail --silent --show-error --connect-timeout 5 --max-time 10 \
  https://api.stoxsim.com/actuator/health/readiness
cd ~/stoxsim-production
docker compose --env-file .env -f compose.yml ps
docker stats --no-stream
free -h
df -h
ps -eo pid,ppid,stat,etime,comm | awk 'NR == 1 || $3 ~ /^Z/'
```

Readiness should return `{"status":"UP"}`; check all container statuses and
available memory/disk as well. A UP readiness result alone does not measure
capacity or prove every feature works. Zombies have a `Z` process state and
consume process slots rather than significant RAM/CPU. Inspect a listed parent:

```bash
ps -p PARENT_PID -o pid,ppid,stat,etime,comm
docker compose --env-file .env -f compose.yml top backend
```

Replace `PARENT_PID` with the actual PPID. Do not try to kill a zombie or blindly
restart Docker/the VPS. Persistent zombies require identifying and fixing their
parent's child-process handling; coordinate a targeted restart only after that
parent and the affected service are known. Keep process/log details private.
