"""Explicit human approval and immutable binary-first release publication."""
import hashlib
import json
from pathlib import Path
import re
import time
import uuid
from .cloud import MissingObject
from .common import read_json, save_json, sha


def version_tuple(value):
    if not isinstance(value, str) or not re.fullmatch(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)', value):
        raise ValueError('Use a version such as 1.2.0')
    return tuple(map(int, value.split('.')))


def validate_candidate(root):
    report = read_json(root / 'report.json')
    if not report or report.get('blockers') != [] or report.get('replay', {}).get('blockers') != []:
        raise ValueError('Candidate has not passed all desktop checks')
    if sha(root / 'speed-candidate.tflite') != report['candidate_sha256'] or sha(root / 'baseline.tflite') != report['baseline_sha256']:
        raise ValueError('Candidate or baseline changed after evaluation')
    from .replay import source_hash
    if report.get('source_sha256') != source_hash():
        raise ValueError('Navigation/training source changed; evaluate a new candidate')
    return report


def validate_evidence(root):
    report = validate_candidate(root)
    phone = read_json(root / 'phone-test.json')
    if not phone or phone.get('passed') is not True or phone.get('candidate_sha256') != report['candidate_sha256'] or phone.get('report_sha256') != sha(root / 'report.json'):
        raise ValueError('Missing or stale physical-phone test evidence')
    field = phone.get('field_test', {})
    if any(field.get(k) is not True for k in ('passed', 'stops', 'speed', 'gps_loss', 'map_matching')) or not field.get('notes', '').strip():
        raise ValueError('Controlled phone/navigation field-test review is required')
    evidence = phone.get('evidence', {})
    if not evidence:
        raise ValueError('Attach the matching field trip and screen recording before approval')
    for name, digest in evidence.items():
        if Path(name).name != name or not (root / 'evidence' / name).is_file() or sha(root / 'evidence' / name) != digest:
            raise ValueError('Phone evidence changed after testing')
    if not any(n.endswith('.drlog') for n in evidence) or not any(n.endswith(('.mp4', '.webm', '.mkv')) for n in evidence):
        raise ValueError('Both a field trip (.drlog) and screen recording are required')
    trips = [n for n in evidence if n.endswith('.drlog')]
    for trip in trips:
        metadata = read_json(root / 'evidence' / Path(trip).with_suffix('.training.json'))
        if not metadata or metadata.get('candidateTrialSha256') != report['candidate_sha256']:
            raise ValueError('Field recording was not captured with this candidate active')
    return report


def approve(root, version, reviewer):
    report = validate_evidence(root)
    if version_tuple(version) <= version_tuple(report['baseline_version']):
        raise ValueError('Release version must be newer than the baseline')
    if not reviewer.strip():
        raise ValueError('Reviewer name is required')
    approval = {'version': version, 'reviewer': reviewer.strip(), 'approved_at': time.time(),
                'candidate_sha256': report['candidate_sha256'], 'report_sha256': sha(root / 'report.json'),
                'phone_test_sha256': sha(root / 'phone-test.json')}
    save_json(root / 'approval.json', approval)
    return approval


def release_item(root):
    report = validate_evidence(root); approval = read_json(root / 'approval.json')
    if not approval or approval.get('candidate_sha256') != report['candidate_sha256'] or approval.get('report_sha256') != sha(root / 'report.json') or approval.get('phone_test_sha256') != sha(root / 'phone-test.json'):
        raise ValueError('Explicit approval is missing or no longer matches the evidence')
    version = approval['version']; version_tuple(version)
    return {'schemaVersion': 1, 'name': 'speed', 'version': version, 'approved': True, 'validated': True,
            'minAppVersionCode': 12, 'featureContract': 'idr-iovnbd-v1', 'dtype': 'float32',
            'inputShape': [1, 300, 6], 'outputShape': [1, 1], 'path': f'speed/{version}/speed.tflite',
            'sizeBytes': (root / 'speed-candidate.tflite').stat().st_size, 'sha256': report['candidate_sha256']}


def publish_objects(storage, item, blob, expected_baseline=None):
    # Atomic create (x-upsert:false) serializes all publishers using this tool.
    # Never auto-steal a lock: a crashed publisher must be reviewed before recovery.
    lock = json.dumps({'owner':str(uuid.uuid4()), 'created_at':time.time()}).encode()
    try:
        storage.put('model-releases', '_publisher-lock.json', lock, upsert=False)
    except (RuntimeError, ValueError):
        raise RuntimeError('Publication lock unavailable. Another publisher may be running; inspect _publisher-lock.json before recovery.') from None
    try:
        return _publish_locked(storage, item, blob, expected_baseline)
    finally:
        if storage.get('model-releases', '_publisher-lock.json', 16384) == lock:
            storage.delete('model-releases', '_publisher-lock.json')


def _publish_locked(storage, item, blob, expected_baseline):
    if len(blob) != item['sizeBytes'] or hashlib.sha256(blob).hexdigest() != item['sha256']:
        raise ValueError('Release binary does not match manifest')
    def current():
        try:
            return storage.get('model-releases', 'approved.json', 1024**2)
        except MissingObject:
            return None
    previous = current()
    manifest = json.loads(previous) if previous is not None else {'models': []}
    models = manifest.get('models')
    if not isinstance(models, list) or len({m['name'] for m in models}) != len(models):
        raise ValueError('Existing release manifest is invalid')
    for old in models:
        if old['name'] == item['name'] and version_tuple(old['version']) >= version_tuple(item['version']):
            if old == item and storage.get('model-releases', item['path'], item['sizeBytes']) == blob:
                return manifest  # Recovery after a successful publish but interrupted local save.
            raise ValueError('Cloud release is already this version or newer; choose a new version')
    deployed = next((m for m in models if m['name'] == 'speed'), None)
    if deployed != expected_baseline:
        raise ValueError('The deployed baseline changed after evaluation; train and test a new candidate')
    try:
        existing = storage.get('model-releases', item['path'], 50 * 1024**2)
    except MissingObject:
        existing = None
    if existing is not None and existing != blob:
        raise ValueError('Version already contains a different binary; never overwrite a release')
    if existing is None:
        storage.put('model-releases', item['path'], blob, upsert=False)
    if storage.get('model-releases', item['path'], len(blob)) != blob:
        raise ValueError('Remote binary verification failed; approval manifest was not published')
    if current() != previous:
        raise ValueError('Another publisher changed approved.json; retry after reviewing the change')
    manifest['models'] = [m for m in models if m['name'] != item['name']] + [item]
    encoded = json.dumps(manifest, indent=2, allow_nan=False).encode()
    storage.put('model-releases', 'approved.json', encoded, upsert=previous is not None)
    if current() != encoded:
        raise ValueError('Manifest read-back failed; inspect cloud state before retrying')
    return manifest
