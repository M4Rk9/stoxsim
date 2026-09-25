#!/usr/bin/env python3
"""Exercise the real Docker drill paths using CI-built images and synthetic backups only."""
import hashlib
import json
import os
import shutil
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch
import common
import capacity
import recovery

if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
    raise SystemExit('This synthetic harness is only for GitHub-hosted CI')

candidate = common.command(['git','rev-parse','HEAD']).decode().strip()
os.environ['CANDIDATE_SHA'] = candidate
real_command = common.command
def fixture_evidence(name, report):
    report['scope'] = 'CI synthetic integration exercise; mocked S3/local images; not production recovery or VPS-equivalence evidence'
    common.evidence(name, report)
for service, image in [('backend','api'),('frontend','web')]:
    image_id=real_command(['docker','compose','images','-q',service]).decode().strip()
    real_command(['docker','tag',image_id,'ghcr.io/m4rk9/stoxsim-'+image+':'+candidate])

# The existing learner stack supplies a synthetic dump. No S3 account is contacted.
with tempfile.TemporaryDirectory(prefix='ci-operational-fixtures-') as private:
    archive=Path(private)/'source.dump'
    archive.write_bytes(real_command(['docker','compose','exec','-T','postgres','pg_dump','-U','stoxsim','-d','stoxsim','-Fc','--no-owner'],timeout=120))
    name='stoxsim-production-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'.dump'
    os.environ['BACKUP_S3_PREFIX']='s3://synthetic-ci-fixtures/production'
    os.environ['BACKUP_NAME']=name
    digest=hashlib.sha256(archive.read_bytes()).hexdigest()
    def fake_s3(args,**kwargs):
        if args[:3]==['aws','s3api','head-object']:
            return json.dumps({'ServerSideEncryption':'AES256','VersionId':'synthetic-fixture-version','ContentLength':archive.stat().st_size,'LastModified':datetime.now(timezone.utc).isoformat()}).encode()
        if args[:3]==['aws','s3api','get-object']:
            if args[args.index('--key')+1].endswith('.sha256'): Path(args[-1]).write_text(digest+'  '+name+'\n')
            else: shutil.copyfile(archive,args[-1])
            return b'{}'
        return real_command(args,**kwargs)
    with patch.object(recovery,'guard',return_value=candidate), patch.object(recovery,'command',side_effect=fake_s3), patch.object(recovery,'evidence',side_effect=fixture_evidence):
        recovery.main()
    recovery_report=Path('operational-evidence/recovery.json')
    data=json.loads(recovery_report.read_text());data['scope']='CI synthetic backup with mocked S3 metadata; not production recovery evidence';recovery_report.write_text(json.dumps(data,indent=2)+'\n')

# Release old CI services to avoid distorting the isolated application fixture.
real_command(['docker','compose','down','--volumes'],timeout=90)
os.environ['EXPECTED_VCPU']=str(os.cpu_count())
mem=int(Path('/proc/meminfo').read_text().splitlines()[0].split()[1])*1024
os.environ['EXPECTED_RAM_GIB']=str(mem/1024**3)
def local_images(args,**kwargs):
    if args[:2]==['docker','compose'] and args[-1]=='pull': return b''
    return real_command(args,**kwargs)
with patch.object(capacity,'guard',return_value=candidate), patch.object(capacity,'command',side_effect=local_images), patch.object(capacity,'evidence',side_effect=fixture_evidence):
    capacity.main()
capacity_report=Path('operational-evidence/capacity.json')
data=json.loads(capacity_report.read_text());data['scope']='CI synthetic integration exercise on GitHub hardware; not VPS equivalence evidence';capacity_report.write_text(json.dumps(data,indent=2)+'\n')
