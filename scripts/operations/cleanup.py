#!/usr/bin/env python3
"""Always-run cleanup restricted to resources labelled with this workflow run."""
import os
import re
import shutil
from pathlib import Path
from common import DrillError, command

run = os.environ.get('GITHUB_RUN_ID', '')
if not re.fullmatch(r'[0-9]+', run): raise SystemExit('Missing workflow run identity')
failed = False
for kind, listing, removal in [('containers', ['docker','ps','-aq'], ['docker','rm','-f']), ('volumes',['docker','volume','ls','-q'],['docker','volume','rm'])]:
    try:
        ids = command(listing + ['--filter','label=stoxsim.m7.run='+run]).decode().split()
        if ids: command(removal + ids)
    except DrillError: failed = True
root = Path(os.environ['RUNNER_TEMP']).resolve()
for folder in root.glob('stoxsim-private-recovery-*'):
    if folder.is_dir() and not folder.is_symlink():
        try: shutil.rmtree(folder)
        except OSError: failed = True
if failed: raise SystemExit('Cleanup failed; quarantine and dispose of the validation host')
