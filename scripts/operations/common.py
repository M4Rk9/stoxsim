"""Guards and aggregate evidence for manually dispatched operational drills."""
import json
import os
import re
import shutil
import signal
import subprocess
import time
from pathlib import Path


class DrillError(RuntimeError):
    pass


def command(args, **kwargs):
    try:
        return subprocess.run(args, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              timeout=kwargs.pop('timeout', 120), **kwargs).stdout
    except (subprocess.SubprocessError, OSError) as error:
        # PostgreSQL errors can contain restored values; never echo raw subprocess logs.
        raise DrillError('Operational command failed: ' + str(args[0])) from None


def sha(value):
    if not re.fullmatch(r'[0-9a-f]{40}', value or ''):
        raise DrillError('A full immutable 40-character candidate SHA is required')
    return value


def host_profile(expected_cpu, expected_memory_gib, actual_cpu=None, actual_memory=None):
    cpu = os.cpu_count() if actual_cpu is None else actual_cpu
    memory = int(Path('/proc/meminfo').read_text().splitlines()[0].split()[1]) * 1024 if actual_memory is None else actual_memory
    expected_cpu, expected_memory_gib = int(expected_cpu), float(expected_memory_gib)
    if expected_cpu < 1 or expected_memory_gib < 1 or cpu != expected_cpu or not .85 <= memory / (expected_memory_gib * 1024**3) <= 1.05:
        raise DrillError('Validation host CPU/RAM does not match the declared production profile')
    return {'cpu_count': cpu, 'memory_bytes': memory, 'declared_cpu': expected_cpu, 'declared_memory_gib': expected_memory_gib}


def guard():
    if os.environ.get('GITHUB_REF') != 'refs/heads/main' or os.environ.get('RUNNER_ENVIRONMENT') != 'self-hosted':
        raise DrillError('Operational drills require main on the dedicated self-hosted validation runner')
    marker = Path('/etc/stoxsim/m7-isolated-host')
    if not marker.is_file() or marker.is_symlink() or marker.stat().st_uid != 0 or marker.stat().st_mode & 0o022 or marker.read_text().strip() != 'ISOLATED-STOXSIM-VALIDATION':
        raise DrillError('The isolated validation host marker is missing')
    # Refuse any existing workload, including stopped production containers.
    if command(['docker', 'ps', '-aq']).strip():
        raise DrillError('Validation requires a dedicated Docker host with no existing containers')
    if shutil.disk_usage('/').free < 10 * 1024**3:
        raise DrillError('Validation needs at least 10 GiB free disk')
    candidate = sha(os.environ.get('CANDIDATE_SHA'))
    command(['git', 'merge-base', '--is-ancestor', candidate, 'HEAD'])
    return candidate


def evidence(name, report):
    directory = Path('operational-evidence')
    directory.mkdir(mode=0o700, exist_ok=True)
    report['recorded_at_utc'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
    report['run_url'] = 'https://github.com/' + os.environ.get('GITHUB_REPOSITORY', '') + '/actions/runs/' + os.environ.get('GITHUB_RUN_ID', '')
    (directory / (name + '.json')).write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


def interrupted(signum, frame):
    raise DrillError('Operational drill interrupted; cleaning isolated resources')


signal.signal(signal.SIGTERM, interrupted)
signal.signal(signal.SIGINT, interrupted)
