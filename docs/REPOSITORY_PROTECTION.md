# Repository protection runbook

The files in this repository configure CI, CodeQL, dependency review, Dependabot, ownership, and contribution policy. The remaining controls are GitHub repository settings and require repository-owner access.

## 1. Protect the default branch

Open **Settings → Rules → Rulesets → New branch ruleset**.

- Name: `Protect main`
- Enforcement: `Active`
- Target: default branch
- Restrict deletions
- Block force pushes
- Require a pull request before merging
- Required approvals: `0` while the repository has one maintainer; change to `1` and require Code Owner review after adding a second trusted maintainer
- Dismiss stale approvals when new commits are pushed
- Require conversation resolution
- Require branches to be up to date before merging
- Require these status checks:
  - `backend`
  - `frontend`
  - `staging-bundle`
  - `browser-acceptance`
  - `dependency-review`
  - `Analyze (java-kotlin)`
  - `Analyze (javascript-typescript)`
- Do not add a bypass actor for routine maintainer work

GitHub only offers a check after it has run at least once. Merge the workflow PR first if a new check is not yet selectable, then return and add it.

## 2. Enable repository security

Open **Settings → Security → Advanced Security** and enable:

- Dependency graph
- Dependabot alerts
- Dependabot security updates
- Grouped security updates
- Secret scanning
- Push protection
- Validity checks for supported token types
- Private vulnerability reporting

Code scanning is supplied by `.github/workflows/codeql.yml`. Keep either this advanced workflow or GitHub default setup, not both.

## 3. Tighten merge and workflow settings

Under **Settings → General**:

- Allow squash merging
- Disable merge commits and rebase merging unless a specific history requirement appears
- Enable automatically deleting head branches

Under **Settings → Actions → General**:

- Set the default workflow token permission to read repository contents
- Allow workflows to request only the explicit permissions declared in their YAML
- Require approval for workflows from first-time external contributors

## 4. Verify

Open a test pull request and confirm that direct pushes, force pushes, deletion, and merge with a failing or stale check are blocked. Confirm that the Security tab shows CodeQL results, Dependabot alerts, and private reporting.
