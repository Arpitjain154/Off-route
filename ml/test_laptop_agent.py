"""Offline contract tests: fixtures prove integrity/gates, never navigation accuracy."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SID = '11111111-1111-4111-8111-111111111111'
UID = '22222222-2222-4222-8222-222222222222'


class MemoryStorage:
    def __init__(self):
        self.objects = {}
        self.writes = []
    def get(self, bucket, path, limit):
        value = self.objects.get((bucket, path))
        if value is None:
            from laptop_agent.cloud import MissingObject
            raise MissingObject(path)
        if len(value) > limit:
            raise ValueError('Response too large')
        return value
    def put(self, bucket, path, value, upsert=False):
        if not upsert and (bucket, path) in self.objects:
            raise ValueError('Already exists')
        self.writes.append(path)
        self.objects[bucket, path] = value
    def delete(self, bucket, path):
        del self.objects[bucket, path]
    def list(self, bucket, prefix):
        names = {p[len(prefix.rstrip('/') + '/'):].split('/')[0]
                 for b, p in self.objects if b == bucket and p.startswith(prefix.rstrip('/') + '/')}
        return [{'name': n} for n in sorted(names)]


def package(storage):
    files = []
    for suffix in ('.drlog', '.quality.csv', '.training.json', '.summary.json'):
        name = SID + suffix
        blob = (suffix * 8).encode()
        if suffix == '.training.json':
            blob = json.dumps(dict(schemaVersion=1, source='device', sessionId=SID)).encode()
        chunks = []
        for i, data in enumerate((blob[:9], blob[9:])):
            obj = name + f'.part{i:05d}'
            storage.objects['journeys', f'{UID}/{SID}/{obj}'] = data
            chunks.append(dict(objectName=obj, sizeBytes=len(data), sha256=hashlib.sha256(data).hexdigest()))
        files.append(dict(originalName=name, sizeBytes=len(blob), sha256=hashlib.sha256(blob).hexdigest(), chunks=chunks))
    manifest = dict(schemaVersion=1, source='device', sessionId=SID, files=files)
    storage.objects['journeys', f'{UID}/{SID}/complete.json'] = json.dumps(manifest).encode()
    return manifest


class DownloadTests(unittest.TestCase):
    def module(self):
        self.assertIsNotNone(importlib.util.find_spec('laptop_agent'), 'Laptop agent is missing')
        from laptop_agent import ingest
        return ingest
    def test_download_requires_marker_and_reconstructs_in_order(self):
        m = self.module(); store = MemoryStorage(); manifest = package(store)
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            path = m.download_trip(store, UID, SID, root)
            self.assertEqual((path / (SID + '.drlog')).read_bytes(), b'.drlog' * 8)
            self.assertEqual(m.download_trip(store, UID, SID, root), path)
            del store.objects['journeys', f'{UID}/{SID}/complete.json']
            from laptop_agent.cloud import MissingObject
            with self.assertRaises(MissingObject):
                m.download_trip(store, UID, SID, root)
    def test_corrupt_chunk_never_commits(self):
        m = self.module(); store = MemoryStorage(); manifest = package(store)
        obj = manifest['files'][0]['chunks'][0]['objectName']
        store.objects['journeys', f'{UID}/{SID}/{obj}'] = b'corrupted'
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):
                m.download_trip(store, UID, SID, Path(tmp))
            self.assertFalse((Path(tmp) / UID / SID).exists())
    def test_manifest_rejects_escape_duplicate_reorder_and_wrong_identity(self):
        m = self.module(); store = MemoryStorage(); original = package(store)
        mutations = [lambda x: x['files'][0].update(originalName='../escape'),
                     lambda x: x['files'].append(x['files'][0]),
                     lambda x: x['files'][0]['chunks'].reverse(),
                     lambda x: x.update(sessionId=UID),
                     lambda x: x['files'][0].update(sizeBytes=-1),
                     lambda x: x['files'][0]['chunks'][0].update(sizeBytes=9 * 1024 * 1024)]
        for mutate in mutations:
            value = copy.deepcopy(original); mutate(value)
            with self.subTest(value=value), self.assertRaises(ValueError):
                m.validate_manifest(value, SID)
    def test_cache_tampering_and_changed_manifest_fail_closed(self):
        m = self.module(); store = MemoryStorage(); manifest = package(store)
        with tempfile.TemporaryDirectory() as tmp:
            path = m.download_trip(store, UID, SID, Path(tmp))
            (path / (SID + '.drlog')).write_bytes(b'bad')
            with self.assertRaises(ValueError):
                m.download_trip(store, UID, SID, Path(tmp))


class EvaluationTests(unittest.TestCase):
    def module(self):
        self.assertIsNotNone(importlib.util.find_spec('laptop_agent'), 'Laptop agent is missing')
        from laptop_agent import evaluate
        return evaluate
    def test_stop_regression_blocks_even_when_overall_error_improves(self):
        import numpy as np
        m = self.module(); y = np.r_[np.zeros(40), np.full(160, 10.)]
        baseline = np.r_[np.zeros(40), np.full(160, 14.)]
        candidate = np.r_[np.ones(40), np.full(160, 10.)]
        result = m.compare_speed(y, baseline, candidate)
        self.assertTrue(any('stopped' in r for r in result['blockers']))
    def test_missing_stop_or_nonfinite_data_blocks(self):
        import numpy as np
        m = self.module()
        self.assertTrue(m.compare_speed(np.full(200, 10.), np.full(200, 12.), np.full(200, 10.))['blockers'])
        self.assertTrue(m.compare_speed(np.zeros(200), np.zeros(200), np.full(200, np.nan))['blockers'])
    def test_fresh_test_trips_and_new_data_are_required(self):
        m = self.module()
        import numpy as np
        data = {'session': np.repeat(['a','b','c','d','e'], 200),
                'split': np.repeat(['train','train','train','validation','test'], 200),
                'speed': np.tile(np.r_[np.zeros(40), np.linspace(1, 15, 160)], 5)}
        state = {'trained_sessions': ['a','b','c','d','e'], 'used_test_sessions': ['e']}
        self.assertTrue(m.data_blockers(data, state, min_new_trips=3, min_new_fixes=600))
        self.assertFalse(m.data_blockers(data, {}, min_new_trips=3, min_new_fixes=600))

    def test_drift_regression_and_inactive_candidate_block(self):
        m = self.module()
        self.assertTrue(hasattr(m, 'compare_navigation'), 'Navigation regression gate missing')
        rows = [dict(baseline_error=2., candidate_error=3., baseline_available=1,
                     candidate_available=1, candidate_ai=1, hidden=1) for _ in range(60)]
        self.assertTrue(m.compare_navigation(rows)['blockers'])
        for r in rows:
            r['candidate_error'] = 1.; r['candidate_ai'] = 0
        self.assertTrue(m.compare_navigation(rows)['blockers'])
        for r in rows:
            r['candidate_ai'] = 1
        self.assertFalse(m.compare_navigation(rows)['blockers'])


class ReleaseTests(unittest.TestCase):
    def module(self):
        from laptop_agent import common
        self.assertTrue((Path(common.__file__).parent / 'release.py').exists(), 'Release workflow missing')
        from laptop_agent import release
        return release

    def candidate(self, root):
        from laptop_agent.common import save_json, sha
        (root / 'speed-candidate.tflite').write_bytes(b'candidate-test-binary')
        (root / 'baseline.tflite').write_bytes(b'baseline-test-binary')
        save_json(root / 'report.json', {'blockers': [], 'candidate_sha256': sha(root / 'speed-candidate.tflite'),
                  'baseline_sha256': sha(root / 'baseline.tflite'), 'baseline_version': '1.1.0',
                  'source_sha256': __import__('laptop_agent.replay', fromlist=['source_hash']).source_hash(),
                  'replay': {'blockers': []}})
        save_json(root / 'phone-test.json', {'passed': True, 'candidate_sha256': sha(root / 'speed-candidate.tflite'),
                  'report_sha256': sha(root / 'report.json'), 'device': 'physical-test-device',
                  'field_test': {'passed': True, 'stops': True, 'speed': True, 'gps_loss': True,
                                 'map_matching': True, 'notes': 'Observed controlled test'},
                  'evidence': {'field-recording.drlog': 'bad'}})
        return root

    def test_approval_requires_real_unchanged_evidence(self):
        m = self.module()
        with tempfile.TemporaryDirectory() as tmp:
            root = self.candidate(Path(tmp))
            with self.assertRaises(ValueError):
                m.validate_evidence(root)

    def test_binary_uploaded_verified_then_manifest_preserves_other_models(self):
        m = self.module(); store = MemoryStorage()
        other = {'name': 'motion', 'version': '1.1.0'}
        store.objects['model-releases', 'approved.json'] = json.dumps({'models': [other]}).encode()
        item = {'name': 'speed', 'version': '1.2.0', 'path': 'speed/1.2.0/speed.tflite',
                'sha256': hashlib.sha256(b'model').hexdigest(), 'sizeBytes': 5}
        m.publish_objects(store, item, b'model')
        self.assertEqual(store.writes[-2:], ['speed/1.2.0/speed.tflite', 'approved.json'])
        self.assertIn(other, json.loads(store.objects['model-releases', 'approved.json'])['models'])
        self.assertEqual(store.get('model-releases', item['path'], 50), b'model')

    def test_different_existing_version_is_never_overwritten(self):
        m = self.module(); store = MemoryStorage()
        store.objects['model-releases', 'speed/1.2.0/speed.tflite'] = b'existing'
        item = {'name': 'speed', 'version': '1.2.0', 'path': 'speed/1.2.0/speed.tflite',
                'sha256': hashlib.sha256(b'model').hexdigest(), 'sizeBytes': 5}
        with self.assertRaises(ValueError):
            m.publish_objects(store, item, b'model')
        self.assertNotIn('approved.json', store.writes)

    def test_changed_deployed_baseline_blocks_publication(self):
        m = self.module(); store = MemoryStorage()
        store.objects['model-releases', 'approved.json'] = json.dumps({'models': [{'name':'speed','version':'1.2.0'}]}).encode()
        item = {'name':'speed','version':'1.3.0','path':'speed/1.3.0/speed.tflite',
                'sha256':hashlib.sha256(b'model').hexdigest(),'sizeBytes':5}
        with self.assertRaises(ValueError):
            m.publish_objects(store, item, b'model', expected_baseline=None)
        self.assertNotIn(item['path'], store.writes)

    def test_second_publisher_cannot_enter_critical_section(self):
        m = self.module(); store = MemoryStorage()
        store.objects['model-releases', '_publisher-lock.json'] = b'busy'
        item = {'name':'speed','version':'1.2.0','path':'speed/1.2.0/speed.tflite',
                'sha256':hashlib.sha256(b'model').hexdigest(),'sizeBytes':5}
        with self.assertRaises((ValueError, RuntimeError)):
            m.publish_objects(store, item, b'model')
        self.assertNotIn('approved.json', store.writes)


class CohortTests(unittest.TestCase):
    def test_historical_duplicate_keeps_its_original_identity(self):
        from laptop_agent.pipeline import unique_logs
        from laptop_agent.common import save_json
        store=MemoryStorage();manifest=package(store)
        from laptop_agent.ingest import download_trip
        with tempfile.TemporaryDirectory() as tmp:
            folder=download_trip(store,UID,SID,Path(tmp))
            old=folder/(SID+'.drlog'); identities={}
            self.assertEqual(unique_logs([old],identities)[0],[old])
            clone='00000001-1111-4111-8111-111111111111'
            target=folder.parent/clone;target.mkdir()
            copied=copy.deepcopy(manifest);copied['sessionId']=clone
            for file in copied['files']:
                name=file['originalName'];new_name=name.replace(SID,clone)
                (target/new_name).write_bytes((folder/name).read_bytes())
                file['originalName']=new_name
                for chunk in file['chunks']:chunk['objectName']=chunk['objectName'].replace(SID,clone)
            save_json(target/'complete.json',copied)
            self.assertEqual(unique_logs([target/(clone+'.drlog'),old],identities)[0],[old])

    def test_backoff_counts_navigation_regressions(self):
        from laptop_agent import pipeline
        self.assertTrue(hasattr(pipeline, 'record_outcome'), 'Complete outcome backoff missing')
        state = {}
        for _ in range(3): pipeline.record_outcome(state, ['Navigation regression'])
        self.assertEqual(pipeline.interval_hours(state), 24)

    def test_deployed_cloud_baseline_is_used(self):
        from laptop_agent.pipeline import baseline
        store=MemoryStorage();blob=b'cloud-current-model'
        item=dict(name='speed',version='1.2.0',approved=True,validated=True,
                  path='speed/1.2.0/speed.tflite',sizeBytes=len(blob),sha256=hashlib.sha256(blob).hexdigest())
        store.objects['model-releases','approved.json']=json.dumps({'models':[item]}).encode()
        store.objects['model-releases',item['path']]=blob
        with tempfile.TemporaryDirectory() as tmp:
            path,meta,frozen=baseline(Path(tmp),store)
            self.assertEqual(path.read_bytes(),blob);self.assertEqual(frozen,item)


class RuntimeTests(unittest.TestCase):
    def test_trial_import_selects_recording_time_not_archive_order(self):
        import io
        import tarfile
        from unittest.mock import patch
        from laptop_agent import phone
        digest = 'a' * 64
        archive = io.BytesIO()
        with tarfile.open(fileobj=archive, mode='w') as tar:
            # The older trip is deliberately last in the archive.
            for sid, timestamp in [(SID, 2000), (UID, 1000)]:
                for suffix, blob in [('.drlog', b'fixture'), ('.summary.json', b'{}'),
                                     ('.training.json', json.dumps({'candidateTrialSha256': digest}).encode())]:
                    entry = tarfile.TarInfo('files/drives/' + sid + suffix)
                    entry.size, entry.mtime = len(blob), timestamp
                    tar.addfile(entry, io.BytesIO(blob))
        with tempfile.TemporaryDirectory() as tmp:
            def copy_archive(*args, **kwargs):
                kwargs['stdout'].write(archive.getvalue())
            with patch.object(phone, 'validate_candidate', return_value={'candidate_sha256': digest}), \
                 patch.object(phone, 'device', return_value=(['adb'], 'test-device')), \
                 patch.object(phone.subprocess, 'run', side_effect=copy_archive):
                result = phone.import_trial(Path(tmp))
            self.assertEqual(Path(result['path']).stem, SID)

    def test_dpapi_roundtrip(self):
        import os
        if os.name!='nt':self.skipTest('Windows-only credential storage')
        from laptop_agent.cloud import protect
        secret=b'test-only-not-a-real-token'
        encrypted=protect(secret)
        self.assertNotIn(secret,encrypted)
        self.assertEqual(protect(encrypted,decrypt=True),secret)

    def test_same_candidate_approval_and_changed_evidence(self):
        from laptop_agent import release
        from laptop_agent.common import save_json,sha
        with tempfile.TemporaryDirectory() as tmp:
            root=ReleaseTests().candidate(Path(tmp));directory=root/'evidence';directory.mkdir()
            files={SID+'.drlog':b'test-recording','screen.mp4':b'test-video',
                   SID+'.training.json':json.dumps({'candidateTrialSha256':sha(root/'speed-candidate.tflite')}).encode()}
            for name,blob in files.items():(directory/name).write_bytes(blob)
            phone=json.loads((root/'phone-test.json').read_text())
            phone['evidence']={name:sha(directory/name) for name in files}
            save_json(root/'phone-test.json',phone)
            release.approve(root,'1.2.0','Test reviewer')
            self.assertEqual(release.release_item(root)['version'],'1.2.0')
            (directory/'screen.mp4').write_bytes(b'changed')
            with self.assertRaises(ValueError):release.release_item(root)


if __name__ == '__main__':
    unittest.main()
