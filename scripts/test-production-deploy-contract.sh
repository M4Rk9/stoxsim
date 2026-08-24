#!/usr/bin/env bash
set -Eeuo pipefail

WORKFLOW=.github/workflows/production-deploy.yml
DEPLOY_SCRIPT=deploy/production/deploy.sh
ROLLBACK_SCRIPT=deploy/production/rollback.sh

grep -Fq 'ref: ${{ inputs.image_tag }}' "$WORKFLOW"
grep -Fq 'Verify immutable production bundle revision' "$WORKFLOW"
grep -Fq -- '--force-recreate alertmanager blackbox-exporter prometheus grafana' "$DEPLOY_SCRIPT"
grep -Fq '.previous-deployment-bundle.tgz' "$WORKFLOW"
grep -Fq 'restore_previous_bundle' "$DEPLOY_SCRIPT"
grep -Fq '.previous-deployment-bundle.tgz' "$ROLLBACK_SCRIPT"
grep -Fq 'capture_bundle' "$ROLLBACK_SCRIPT"

echo "Production deployment contract checks passed"
