#!/usr/bin/env bash
# Exercise actual production/staging Caddy routes against an intentionally unsafe upstream.
set -Eeuo pipefail
ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TMP_DIR=$(mktemp -d)
EDGE_CONTAINER="stoxsim-edge-security-${RANDOM}"
UPSTREAM_PID=""
cleanup() {
  docker rm -f "$EDGE_CONTAINER" >/dev/null 2>&1 || true
  [[ -z "$UPSTREAM_PID" ]] || kill "$UPSTREAM_PID" 2>/dev/null || true
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT
cat > "$TMP_DIR/upstream.py" <<'PY'
from http.server import BaseHTTPRequestHandler, HTTPServer
class Handler(BaseHTTPRequestHandler):
 def do_GET(self):
  self.send_response(200)
  for k,v in {'X-Frame-Options':'ALLOWALL', 'X-Content-Type-Options':'unsafe', 'Cache-Control':'public,max-age=999', 'X-Powered-By':'fixture'}.items():self.send_header(k,v)
  if self.path != '/no-csp':self.send_header('Content-Security-Policy', "default-src 'self'; script-src 'self' 'nonce-ci-fixture'; script-src-attr 'none'; frame-ancestors 'none'")
  self.end_headers();self.wfile.write(b'UPSTREAM')
 def log_message(self,*args):pass
HTTPServer(('127.0.0.1',18090),Handler).serve_forever()
PY
python3 "$TMP_DIR/upstream.py" & UPSTREAM_PID=$!
for environment in production staging; do
  ENVIRONMENT="$environment" TMP_DIR="$TMP_DIR" ROOT_DIR="$ROOT_DIR" python3 - <<'PY'
import os,pathlib
kind=os.environ['ENVIRONMENT'];root=pathlib.Path(os.environ['ROOT_DIR']);target=pathlib.Path(os.environ['TMP_DIR'])
s=(root/f'deploy/{kind}/Caddyfile').read_text().replace('email {$ACME_EMAIL}','email audit@example.test\n    auto_https off')
for name,port in [('WEB',18080),('API',18081),('WWW',18082)]:s=s.replace('{$'+kind.upper()+'_'+name+'_DOMAIN} {',f'http://127.0.0.1:{port} {{')
s=s.replace('frontend:3000','127.0.0.1:18090').replace('backend:8080','127.0.0.1:18090')
(target/'Caddyfile').write_text(s)
PY
  docker run --detach --name "$EDGE_CONTAINER" --network host \
    -e PRODUCTION_API_DOMAIN=api.audit.test -e PRODUCTION_WEB_DOMAIN=web.audit.test \
    -e STAGING_API_DOMAIN=api.audit.test -e STAGING_WEB_DOMAIN=web.audit.test \
    -v "$TMP_DIR/Caddyfile:/etc/caddy/Caddyfile:ro" caddy:2-alpine >/dev/null
  for attempt in {1..40}; do
    if curl --silent --fail http://127.0.0.1:18080/ >/dev/null; then break; fi
    sleep 0.25
  done
  python3 - <<'PY'
import urllib.request,urllib.error
for port in [18080,18081]:
 base=f'http://127.0.0.1:{port}'
 with urllib.request.urlopen(base) as r:
  assert r.headers['Strict-Transport-Security']=='max-age=31536000; includeSubDomains'
  assert r.headers['X-Frame-Options']=='DENY'
  assert r.headers['X-Content-Type-Options']=='nosniff'
  assert 'frame-ancestors \'none\'' in r.headers['Content-Security-Policy']
  assert '*' not in r.headers['Content-Security-Policy'].split(';')[0]
  if port==18080:assert "script-src 'self' 'nonce-ci-fixture'" in r.headers['Content-Security-Policy']
 with urllib.request.urlopen(base+'/no-csp') as r:assert r.headers['Content-Security-Policy']=="default-src 'none'; frame-ancestors 'none'"
  assert r.headers.get('Server') is None
 for path in ['/.env','/.env.production','/.git/HEAD','/.git/config','/nested/.env','/nested/.git/config','/%2egit/config','/%2eenv','/.aws/credentials']:
  try:urllib.request.urlopen(base+path);raise AssertionError(path+' reached upstream')
  except urllib.error.HTTPError as e:assert e.code==404,(path,e.code)
 with urllib.request.urlopen(base+'/.well-known/example') as r:assert r.status==200
with urllib.request.urlopen('http://127.0.0.1:18081/api/v1/auth/example') as r:assert r.headers['Cache-Control']=='no-store'
PY
  if [[ "$environment" == production ]]; then
    headers=$(curl --silent --show-error --dump-header - --output /dev/null http://127.0.0.1:18082/)
    grep -Fq 'Strict-Transport-Security: max-age=31536000' <<<"$headers"
    grep -Fq 'Location: https://web.audit.test/' <<<"$headers"
  fi
  docker rm -f "$EDGE_CONTAINER" >/dev/null
  echo "$environment edge: upstream header overrides and sensitive paths denied"
done
