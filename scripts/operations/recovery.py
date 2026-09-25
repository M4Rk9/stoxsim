#!/usr/bin/env python3
"""Recover an encrypted offsite backup into a network-isolated PostgreSQL container."""
import hashlib
import json
import os
import re
import secrets
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path
from common import DrillError, command, evidence, guard

MAX_BACKUP = 512 * 1024**2
REQUIRED_TABLES = {'app_user', 'virtual_account', 'holding', 'paper_order', 'account_ledger', 'user_subscription', 'portfolio_history'}


def backup_location(prefix, name):
    if not re.fullmatch(r'stoxsim-production-\d{8}T\d{6}Z\.dump', name or ''):
        raise DrillError('Select an exact timestamped production backup filename')
    match = re.fullmatch(r's3://([a-z0-9][a-z0-9.-]{1,61}[a-z0-9])/([A-Za-z0-9_/-]*)', prefix or '')
    if not match or '..' in match[2].split('/'):
        raise DrillError('Configure a fixed S3 bucket/prefix for backup recovery')
    return match[1], match[2].rstrip('/') + '/' + name if match[2] else name


def check_metadata(metadata, now):
    if metadata.get('ServerSideEncryption') not in ('AES256', 'aws:kms', 'aws:kms:dsse'):
        raise DrillError('Backup object must have S3 server-side encryption')
    if not 0 < metadata.get('ContentLength', 0) <= MAX_BACKUP:
        raise DrillError('Backup exceeds the bounded 512 MiB recovery profile')
    age = (now - datetime.fromisoformat(metadata['LastModified'].replace('Z', '+00:00'))).total_seconds()
    if not 0 <= age <= 7 * 86400:
        raise DrillError('Select a backup created within the last seven days')
    return age


def check_checksum(content, name, digest):
    if not re.fullmatch(r'[a-fA-F0-9]{64} [ *]' + re.escape(name) + r'\n?', content) or content[:64].lower() != digest:
        raise DrillError('Backup checksum does not match the selected archive')


def main():
    candidate = guard()
    bucket, key = backup_location(os.environ.get('BACKUP_S3_PREFIX'), os.environ.get('BACKUP_NAME'))
    name = os.environ['BACKUP_NAME']
    meta = json.loads(command(['aws', 's3api', 'head-object', '--bucket', bucket, '--key', key]))
    age = check_metadata(meta, datetime.now(timezone.utc))
    if not meta.get('VersionId') or meta['VersionId'] == 'null':
        raise DrillError('Enable S3 versioning so recovery pins one immutable object version')
    identifier = 'stoxsim-recovery-' + secrets.token_hex(8)
    volume = identifier + '-data'
    report = {'candidate_sha': candidate, 'passed': False, 'scope': 'Encrypted offsite production backup restored in isolation; no production service touched', 'backup_age_seconds': int(age), 'backup_bytes': meta['ContentLength']}
    started = time.monotonic()
    created = False
    volume_created = False
    try:
        with tempfile.TemporaryDirectory(prefix='stoxsim-private-recovery-', dir=os.environ.get('RUNNER_TEMP')) as private:
            os.chmod(private, 0o700)
            archive, checksum = Path(private) / 'backup.dump', Path(private) / 'checksum'
            command(['aws', 's3api', 'get-object', '--bucket', bucket, '--key', key, '--version-id', meta['VersionId'], str(archive)], timeout=300)
            command(['aws', 's3api', 'get-object', '--bucket', bucket, '--key', key + '.sha256', str(checksum)], timeout=120)
            with archive.open('rb') as stream:
                digest = hashlib.file_digest(stream, 'sha256').hexdigest()
            check_checksum(checksum.read_text(), name, digest)
            report['backup_sha256'] = digest
            command(['docker', 'pull', 'postgres:17-alpine'], timeout=300)
            command(['docker', 'volume', 'create', '--label', 'stoxsim.m7.run='+os.environ['GITHUB_RUN_ID'], volume]); volume_created = True
            password = secrets.token_hex(32)
            envfile = Path(private) / 'postgres.env'
            envfile.write_text('POSTGRES_PASSWORD=' + password + '\nPOSTGRES_DB=recovered\n')
            envfile.chmod(0o600)
            command(['docker', 'run', '-d', '--name', identifier, '--label', 'stoxsim.m7.run='+os.environ['GITHUB_RUN_ID'], '--network', 'none', '--memory', '1g', '--cpus', '1', '--pids-limit', '256', '--env-file', str(envfile), '-v', volume + ':/var/lib/postgresql/data', 'postgres:17-alpine'])
            created = True
            for attempt in range(60):
                try:
                    command(['docker', 'exec', identifier, 'pg_isready', '-h', '127.0.0.1', '-U', 'postgres', '-d', 'recovered'])
                    break
                except DrillError:
                    if attempt == 59: raise
                    time.sleep(1)
            with archive.open('rb') as stream:
                command(['docker', 'exec', '-i', identifier, 'pg_restore', '-U', 'postgres', '-d', 'recovered', '--single-transaction', '--exit-on-error', '--no-owner', '--no-privileges'], stdin=stream, timeout=900)
            def sql(query, database='recovered'):
                return command(['docker', 'exec', '-i', identifier, 'psql', '-X', '-U', 'postgres', '-d', database, '-At', '-v', 'ON_ERROR_STOP=1'], input=query.encode()).decode().strip()
            tables = set(sql("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename;").splitlines())
            if not REQUIRED_TABLES <= tables:
                raise DrillError('Restored database lacks required application tables')
            if sql('SELECT count(*) FROM flyway_schema_history WHERE NOT success;') != '0':
                raise DrillError('Backup contains unsuccessful schema migrations')
            migration_paths = command(['git', 'ls-tree', '-r', '--name-only', candidate, 'backend/src/main/resources/db/migration']).decode().splitlines()
            expected = max(int(Path(p).name.split('__')[0][1:]) for p in migration_paths if re.match(r'V[0-9]+__', Path(p).name))
            if sql('SELECT max(version::integer) FROM flyway_schema_history;') != str(expected):
                raise DrillError('Backup schema does not match this reviewed candidate')
            if sql('SELECT count(*) FROM virtual_account WHERE available_cash<0 OR blocked_cash<0;') != '0':
                raise DrillError('Restored account balance invariant failed')
            if sql('SELECT count(*) FROM holding WHERE quantity<0 OR blocked_quantity<0 OR blocked_quantity>quantity;') != '0':
                raise DrillError('Restored holding invariant failed')
            # Read every restored table, without returning customer rows or counts in public logs.
            for table in tables:
                quoted = '"' + table.replace('"', '""') + '"'
                sql('SELECT count(*) FROM public.' + quoted + ';')
            report.update({'passed': True, 'schema_version': expected, 'public_tables_read': len(tables), 'recovery_elapsed_seconds': round(time.monotonic() - started, 2)})
    finally:
        cleanup = True
        if created:
            try: command(['docker', 'rm', '-f', identifier])
            except DrillError: cleanup = False
        if volume_created:
            try: command(['docker', 'volume', 'rm', volume])
            except DrillError: cleanup = False
        report['cleanup_passed'] = cleanup
        report['passed'] = report['passed'] and cleanup
        evidence('recovery', report)
        if not cleanup: raise DrillError('Recovery cleanup failed; isolate and dispose of the validation host')


if __name__ == '__main__':
    try: main()
    except (DrillError, ValueError, KeyError) as error:
        raise SystemExit(str(error)) from None
