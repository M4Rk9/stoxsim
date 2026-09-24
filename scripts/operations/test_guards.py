import unittest
from datetime import datetime, timezone, timedelta
from unittest.mock import patch
from common import DrillError, sha, host_profile, guard
from recovery import backup_location, check_checksum, check_metadata
from capacity import summarize


class Guards(unittest.TestCase):
    def test_candidate_and_location_are_bounded(self):
        self.assertEqual(sha('a'*40), 'a'*40)
        for value in ('main', 'a'*39, 'A'*40, ';touch /tmp/x'):
            with self.assertRaises(DrillError): sha(value)
        self.assertEqual(backup_location('s3://backups-bucket/production', 'stoxsim-production-20260924T120000Z.dump'), ('backups-bucket','production/stoxsim-production-20260924T120000Z.dump'))
        for name in ('../../dump', 'latest.dump', 'stoxsim-production-20260924T120000Z.dump;ls'):
            with self.assertRaises(DrillError): backup_location('s3://backups-bucket/production', name)
        with self.assertRaises(DrillError): backup_location('s3://backups-bucket/../other','stoxsim-production-20260924T120000Z.dump')

    def test_checksum_rejects_mismatch_or_sidecar_path(self):
        name='stoxsim-production-20260924T120000Z.dump'
        check_checksum('a'*64+'  '+name+'\n',name,'a'*64)
        for body in ('b'*64+'  '+name, 'a'*64+'  ../'+name, 'a'*64+'  '+name+'\nextra'):
            with self.assertRaises(DrillError): check_checksum(body,name,'a'*64)

    def test_encryption_age_and_size_are_required(self):
        now=datetime.now(timezone.utc)
        valid={'ServerSideEncryption':'aws:kms','ContentLength':100,'LastModified':(now-timedelta(hours=1)).isoformat()}
        self.assertEqual(check_metadata(valid,now),3600)
        for override in ({'ServerSideEncryption':None},{'ContentLength':0},{'ContentLength':600*1024**2},{'LastModified':(now-timedelta(days=8)).isoformat()},{'LastModified':(now+timedelta(hours=1)).isoformat()}):
            with self.assertRaises(DrillError): check_metadata(valid|override,now)

    def test_host_resources_must_match_declared_profile(self):
        self.assertEqual(host_profile('4','8',4,8*1024**3)['cpu_count'],4)
        for cpu,ram in ((2,8),(4,4),(8,8)):
            with self.assertRaises(DrillError): host_profile('4','8',cpu,ram*1024**3)

    def test_dispatch_guard_rejects_branch_or_host(self):
        for values in ({'GITHUB_REF':'refs/heads/feature','RUNNER_ENVIRONMENT':'self-hosted'},{'GITHUB_REF':'refs/heads/main','RUNNER_ENVIRONMENT':'github-hosted'}):
            with patch.dict('os.environ',values), self.assertRaises(DrillError): guard()

    def test_latency_reports_count_errors_and_include_slow_failures(self):
        report=summarize([('history',200,10),('history',500,5000),('portfolio',200,20)])
        self.assertEqual(report['history'],{'requests':2,'errors':1,'p95_ms':5000})
        self.assertEqual(report['portfolio']['errors'],0)


if __name__ == '__main__': unittest.main()
