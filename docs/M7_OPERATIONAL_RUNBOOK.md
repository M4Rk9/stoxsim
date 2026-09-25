# M7 operational drills and completion

Two manual workflows complete the missing operational tooling:

- **Production backup recovery** — restores a selected encrypted offsite backup
  into a separate, network-isolated PostgreSQL container. Never runs the destructive
  `deploy/production/restore.sh` or contacts the production database.
- **VPS-equivalent load test** — uses the exact published API/web image SHA on an
  isolated validation machine with the declared production CPU/RAM. Tests 4, 8 and
  12 concurrent users for 60 seconds each, one request per second per user.

Both run only from `main`, share a concurrency lock, and use the `m7-validation`
GitHub environment. They are manual because a real backup and a separate host must
be supplied. Merging this PR does not buy a server, grant credentials, run a drill,
or sign off evidence that has not been produced.

## One-time validation host setup

Use a **separate disposable machine**, with the same vCPU count and RAM as production.
Do not install this runner or marker on the live StoxSim VPS. Prefer the same provider,
CPU class and disk class; CPU/RAM equality alone does not prove identical performance.
No host has been provisioned or charged for by this PR.

Requirements: Linux x64, Python >=3.11, Git, Docker Engine with Compose v2, AWS CLI v2,
at least 10 GiB free disk, outbound access to GitHub/GHCR/S3, and no other Docker
containers. Do not install production application, provider or SMTP credentials.
The load stack publishes no ports. The host-side driver uses only container addresses
resolved from the freshly created private validation network, without an arbitrary target URL.
Docker documents host access to internal-network container addresses in its [network reference](https://docs.docker.com/reference/cli/docker/network/create/#network-internal-mode---internal).

Create the root-owned isolation marker on that validation machine:

```bash
sudo install -d -m 755 /etc/stoxsim
printf '%s\n' ISOLATED-STOXSIM-VALIDATION | sudo tee /etc/stoxsim/m7-isolated-host >/dev/null
sudo chmod 644 /etc/stoxsim/m7-isolated-host
```

Register a GitHub Actions runner for this repository using GitHub's generated
installation commands. Add label `stoxsim-m7-isolated`, configure it as **ephemeral**
(`--ephemeral` during runner configuration), and register a fresh ephemeral runner
for each drill. A runner deregisters after one job; dispose of its disk/VM after
recovery, especially if cleanup fails or the job is forcibly terminated. Never use
this host for untrusted PR jobs. Restrict runner-group workflow access where available
and require review of all external-contributor workflow runs in this public repo.

Create GitHub environment **m7-validation**, restrict deployments to `main`, and
configure an owner reviewer before giving it backup credentials. Both workflows
checkout reviewed main tooling; the candidate input must be an ancestor of that
checkout. The selected image must already be published by Production candidate.

Environment variables:

| Variable | Value |
| --- | --- |
| `EXPECTED_VCPU` | Actual production vCPU count; obtain from `nproc` on production |
| `EXPECTED_RAM_GIB` | Provisioned production RAM in GiB; check against `/proc/meminfo` |
| `BACKUP_AWS_REGION` | Region containing the offsite S3 backup bucket |

Environment secrets (read-only backup role; never paste credentials into chat):

| Secret | Value |
| --- | --- |
| `BACKUP_S3_PREFIX` | Exact existing `s3://bucket/production-prefix` used by production backup uploads |
| `BACKUP_READ_ACCESS_KEY_ID` | Read-only AWS access key |
| `BACKUP_READ_SECRET_ACCESS_KEY` | Matching secret |
| `BACKUP_READ_SESSION_TOKEN` | Session token when using temporary credentials; otherwise omit |

The principal needs S3 GetObject/GetObjectVersion on that backup prefix and KMS
Decrypt if using SSE-KMS. It needs no PutObject/DeleteObject or database/SSH access.
Use S3 versioning and server-side encryption. The backup script's "encrypted" log
message alone does not prove encryption; this drill checks actual S3 metadata.
The recovery workflow pins the dump's object version and verifies its sidecar hash.

## Run Production backup recovery

1. Select a real timestamped dump already uploaded by the production backup process,
   created after the selected candidate's schema was deployed. It must be <=7 days
   old, <=512 MiB compressed, encrypted and in a versioned bucket. If backups are
   absent or exceed these bounds, resolve that explicitly; do not relabel a CI dump.
2. Open Actions → Production backup recovery → Run workflow → `main`.
3. Enter the complete deployed candidate SHA and the exact
   `stoxsim-production-YYYYMMDDTHHMMSSZ.dump` basename. No bucket paths are entered
   in the run form; the fixed secret prefix supplies that location.
4. Approve the protected environment after confirming the ephemeral runner is on
   the isolated host. Retain `production-backup-recovery-<run-id>`.

The test checks encryption/version metadata, age, size, checksum, complete transactional
restore, required tables, successful migration history matching the selected candidate,
nonnegative account cash and valid blocked quantities, and reads every public table.
It records aggregate outcome, checksum, schema, backup age and elapsed recovery time.
It does not compare against a changing live database or prove application/mail/provider
recovery. Backup age is not an RPO guarantee; this small drill's duration is not a
production outage RTO. Dumps, credentials, row contents and per-table customer counts
are never uploaded or printed. Raw restore errors are withheld because they can
contain customer values. Failed cleanup requires disposal of the isolated host.

## Run VPS-equivalent load test

1. Prepare a fresh ephemeral runner on the dedicated matching host, with no existing
   containers. Set the CPU/RAM variables to production's actual profile.
2. Open Actions → VPS-equivalent load test → Run workflow → `main`.
3. Enter the complete deployed/published candidate SHA, approve the environment,
   and retain `vps-capacity-<run-id>`.

The test refuses mismatched vCPU count or RAM outside 85–105% of declared RAM (allowing
OS-reserved memory), and records actual resources and pulled image IDs. It uses production
application memory caps and PostgreSQL tuning, but does not replicate Caddy/TLS or the
monitoring stack. The load driver is co-located with the application, and that overhead
is part of the reported operating point. Record provider/CPU/disk equivalence separately.

Twelve artificial Pro accounts with fixed cash and 365 synthetic history observations
exercise identity, portfolio valuation, historical calculations, Scenario Lab and the
web homepage. Registration is disabled in the test stack. Pro access is synthetic DB
fixture setup and does not grant benefits on production. Internal-only networking and
disabled providers/mail/billing prevent external side effects. No real backup is used
for load testing. The frontend is fetched over HTTP; client-side browser journeys are
covered separately by browser CI and owner acceptance.

Each stage requires >=50 requests per worker, zero HTTP errors, and p95 <2,000 ms for
each surface. It stops increasing load after a failed stage. At least three resource
samples and successful cleanup are required. The report names the largest **tested**
passing worker count, never an extrapolated maximum concurrent-user or MAU limit.
Live quote/trade traffic, populated holdings, external feeds, email, billing, TLS,
production monitoring contention and Internet latency remain outside this profile.

## Completion evidence

Verified before this PR (current deployed workflow revision
`847b5ea213a2167dad92f5a4baefca7ef28886e1`):

- [Production deployment](https://github.com/M4Rk9/stoxsim/actions/runs/35983761385) — success.
- [Production uptime](https://github.com/M4Rk9/stoxsim/actions/runs/35996186418) — success.
- [Security DAST #15](https://github.com/M4Rk9/stoxsim/actions/runs/36028693643) — success.
- Owner reports deployed features and Scenario Lab work; registration intentionally open.

To close M7, retain the two **real** operational drill run links/artifacts, record the
exact image SHA tested and the operator's hardware-equivalence assessment, resolve failed
checks, and complete `docs/RELEASE_CHECKLIST.md` monitoring/rollback/release sign-off.
Current CI also runs both new scripts using generated databases, local images and mocked
S3 metadata, labelled `synthetic-operational-evidence`; that is integration-test evidence,
not a substitute for the real encrypted backup or matching-host runs.

Artifacts expire after 14 days; retain the reviewed aggregate reports in the private
release record. A cancelled run or missing artifact is not a pass. No release tag is
created, registrations changed or production deployed automatically by these workflows.
