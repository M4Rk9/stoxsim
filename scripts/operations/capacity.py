#!/usr/bin/env python3
"""A bounded application workload on a dedicated host with declared VPS resources."""
import concurrent.futures
import json
import ipaddress
import math
import os
import secrets
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path
from common import DrillError, command, evidence, guard, host_profile


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect)


API_ORIGIN = None
WEB_ORIGIN = None


def request(path, token=None, body=None, web=False):
    if API_ORIGIN is None or WEB_ORIGIN is None:
        raise DrillError('Requests require the freshly created validation containers')
    url = WEB_ORIGIN + '/' if web else API_ORIGIN + '/api/v1/' + path
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(url, data=json.dumps(body).encode() if body is not None else None, headers=headers)
    start = time.monotonic()
    try:
        with OPENER.open(req, timeout=5) as response:
            return response.status, response.read(), (time.monotonic()-start)*1000
    except urllib.error.HTTPError as error:
        return error.code, b'', (time.monotonic()-start)*1000
    except OSError:
        return 0, b'', (time.monotonic()-start)*1000


def summarize(rows):
    result = {}
    for surface in sorted({r[0] for r in rows}):
        subset = [r for r in rows if r[0] == surface]
        latencies = sorted(r[2] for r in subset)
        result[surface] = {'requests': len(subset), 'errors': sum(r[1] != 200 for r in subset), 'p95_ms': latencies[math.ceil(.95*len(latencies))-1]}
    return result


def main():
    global API_ORIGIN, WEB_ORIGIN
    candidate = guard()
    profile = host_profile(os.environ.get('EXPECTED_VCPU', ''), os.environ.get('EXPECTED_RAM_GIB', ''))
    project = 'stoxsim-validation-' + secrets.token_hex(8)
    os.environ['DRILL_PASSWORD'], os.environ['DRILL_JWT'] = secrets.token_hex(32), secrets.token_hex(48)
    compose = ['docker', 'compose', '-p', project, '-f', 'deploy/validation/compose.yml']
    report = {'candidate_sha': candidate, 'host_profile': profile, 'passed': False, 'stages': [], 'scope': 'Synthetic cash-only Pro accounts with 365 history rows; no live feeds, orders, mail, payment provider or TLS; not a maximum-user or MAU estimate'}
    stop = threading.Event()
    resources, sampler_errors = [], []
    sampler = None
    try:
        command(compose + ['pull'], timeout=600)
        command(compose + ['up', '-d', '--wait', '--wait-timeout', '300'], timeout=360)
        ids = command(compose + ['ps', '-q']).decode().split()
        def origin(service, port):
            container = command(compose + ['ps', '-q', service]).decode().strip()
            networks = json.loads(command(['docker','inspect','--format','{{json .NetworkSettings.Networks}}',container]))
            address = networks[project+'_isolated']['IPAddress']
            if not ipaddress.ip_address(address).is_private:
                raise DrillError('Expected a private address on the validation network')
            return 'http://' + address + ':' + str(port)
        API_ORIGIN, WEB_ORIGIN = origin('backend',8080), origin('frontend',3000)
        report['images'] = []
        for container in ids:
            # Record immutable local image IDs, without inspecting environment variables.
            report['images'].append(command(['docker', 'inspect', '--format', '{{.Config.Image}} {{.Image}}', container]).decode().strip())
        def sample():
            while not stop.is_set():
                try:
                    output = command(['docker', 'stats', '--no-stream', '--format', '{{json .}}', *ids], timeout=15).decode()
                    resources.append({'elapsed_seconds': round(time.monotonic()-began, 2), 'containers': [json.loads(line) for line in output.splitlines()]})
                except (DrillError, ValueError): sampler_errors.append(True)
                stop.wait(5)
        def sql(statement):
            return command(compose + ['exec', '-T', 'postgres', 'psql', '-X', '-U', 'stoxsim', '-d', 'stoxsim', '-At', '-v', 'ON_ERROR_STOP=1'], input=statement.encode())
        password = 'Fixture-' + secrets.token_hex(16)
        # Local synthetic rows only; registration and all provider integrations remain disabled.
        sql("CREATE EXTENSION IF NOT EXISTS pgcrypto; INSERT INTO app_user(email,password_hash,display_name,email_verified_at) SELECT 'm7-'||n||'@stoxsim.test', crypt('" + password + "',gen_salt('bf',10)), 'Synthetic capacity learner', now() FROM generate_series(1,12) n;")
        sql("INSERT INTO virtual_account(user_id,market_region,currency,available_cash,starting_capital,account_kind,sandbox_slot,account_label,active,leaderboard_eligible) SELECT id,'INDIA','INR',500000,500000,'STANDARD',0,'Synthetic India',true,true FROM app_user;")
        sql("INSERT INTO user_subscription(user_id,plan,subscription_status) SELECT id,'PRO','ACTIVE' FROM app_user; INSERT INTO portfolio_history(account_id,observed_day,observed_at,currency,equity,cash,trade_cash,capital,quality) SELECT a.id,current_date-n,now()-n*interval '1 day','INR',500000,500000,0,500000,'OBSERVED' FROM virtual_account a CROSS JOIN generate_series(0,364) n;")
        fixtures = []
        for index in range(1,13):
            status, raw, _ = request('auth/login', body={'email': 'm7-'+str(index)+'@stoxsim.test', 'password': password})
            if status != 200:
                report['setup_http_status'] = status
                raise DrillError('Synthetic fixture login failed with HTTP ' + str(status))
            token = json.loads(raw)['accessToken']
            status, raw, _ = request('accounts', token)
            if status != 200: raise DrillError('Synthetic account lookup failed')
            account = next(a['id'] for a in json.loads(raw) if a['marketRegion'] == 'INDIA' and a['accountKind'] == 'STANDARD')
            fixtures.append((token, account))
        began = time.monotonic()
        sampler = threading.Thread(target=sample); sampler.start()
        for workers in (4,8,12):
            def run(fixture):
                token, account = fixture
                paths = [('identity','auth/me',None,False), ('portfolio','accounts/'+account+'/portfolio',None,False), ('history','accounts/'+account+'/portfolio/history?days=365',None,False), ('scenario','accounts/'+account+'/scenarios',{'scenarioId':'recovery','version':1},False), ('web_home','',None,True)]
                until, rows, index = time.monotonic()+60, [], 0
                while time.monotonic() < until:
                    tick = time.monotonic()
                    label,path,body,web = paths[index % len(paths)]
                    status, _, latency = request(path,token,body,web)
                    rows.append((label,status,latency)); index += 1
                    time.sleep(max(0,1-(time.monotonic()-tick)))
                return rows
            with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
                rows = [r for result in executor.map(run,fixtures[:workers]) for r in result]
            surfaces = summarize(rows)
            passed = len(rows) >= workers*50 and len(surfaces) == 5 and all(s['errors'] == 0 and s['p95_ms'] < 2000 for s in surfaces.values())
            report['stages'].append({'concurrent_workers':workers,'duration_seconds':60,'target_requests_per_second':workers,'requests':len(rows),'surfaces':surfaces,'passed':passed})
            if not passed: break
        report['passed'] = len(report['stages']) == 3 and all(s['passed'] for s in report['stages'])
        report['largest_passing_tested_workers'] = max((s['concurrent_workers'] for s in report['stages'] if s['passed']),default=0)
    finally:
        stop.set()
        if sampler: sampler.join(timeout=20)
        report['resource_samples'] = len(resources)
        report['resource_capture_passed'] = len(resources) >= 3 and not sampler_errors and (sampler is None or not sampler.is_alive())
        try:
            command(compose + ['down','--volumes','--remove-orphans'],timeout=90)
            report['cleanup_passed'] = True
        except DrillError: report['cleanup_passed'] = False
        report['passed'] = report['passed'] and report['cleanup_passed'] and report['resource_capture_passed']
        evidence('capacity',report)
        Path('operational-evidence/resources.json').write_text(json.dumps(resources,indent=2)+'\n')
    if not report['passed']: raise DrillError('Capacity thresholds, resource capture or cleanup failed')


if __name__ == '__main__':
    try: main()
    except (DrillError, ValueError, KeyError) as error: raise SystemExit(str(error)) from None
