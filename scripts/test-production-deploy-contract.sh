#!/usr/bin/env bash
set -Eeuo pipefail

WORKFLOW=.github/workflows/production-deploy.yml
DEPLOY_SCRIPT=deploy/production/deploy.sh

grep -Fq 'ref: ${{ inputs.image_tag }}' "$WORKFLOW"
grep -Fq 'Verify immutable production bundle revision' "$WORKFLOW"
grep -Fq -- '--force-recreate alertmanager blackbox-exporter prometheus grafana' "$DEPLOY_SCRIPT"

echo "Production deployment contract checks passed"
