"""One-shot training orchestration, durable state and adaptive polling."""
import hashlib
import json
from pathlib import Path
import shutil
import time
import numpy as np
from phone_learning import build_dataset, train
from .cloud import Storage, MissingObject
from .common import HOME, ROOT, read_json, save_json, sha
from .evaluate import data_blockers, predictions, compare_speed
from .ingest import download_all, verify_cache
from .replay import replay, source_hash


def baseline(home, storage):
    try:
        cloud = json.loads(storage.get('model-releases', 'approved.json', 1024**2))
    except MissingObject:
        cloud = {'models': []}
    models = cloud.get('models')
    if not isinstance(models, list) or len({m['name'] for m in models}) != len(models):
        raise ValueError('Invalid deployed model manifest')
    published = next((m for m in models if m['name'] == 'speed'), None)
    if published:
        from .release import version_tuple
        version_tuple(published['version'])
        if published.get('approved') is not True or published.get('validated') is not True or published.get('path') != 'speed/' + published['version'] + '/speed.tflite' or not 8 <= published['sizeBytes'] <= 50*1024**2:
            raise ValueError('Invalid current speed release')
        blob = storage.get('model-releases', published['path'], published['sizeBytes'])
        if len(blob) != published['sizeBytes'] or hashlib.sha256(blob).hexdigest() != published['sha256']:
            raise ValueError('Current cloud model checksum mismatch')
        from .common import atomic_bytes
        path = home / 'current-cloud-speed.tflite'; atomic_bytes(path, blob)
        return path, published, published
    directory = ROOT / 'android/app/src/main/assets/models'
    metadata = next(m for m in read_json(directory / 'manifest.json')['models'] if m['name'] == 'speed')
    if sha(directory / 'speed.tflite') != metadata['sha256']:
        raise ValueError('Bundled baseline does not match its manifest')
    return directory / 'speed.tflite', metadata, None


def unique_logs(paths, identities=None):
    identities = {} if identities is None else identities
    accepted = []; duplicate = []; hashes = set()
    for path in sorted(paths):
        verify_cache(path.parent, read_json(path.parent / 'complete.json'))
        digest = hashlib.sha256()
        with path.open('rb') as f:
            f.read(200)  # Exclude the header: copied records with a new UUID are still duplicates.
            while block := f.read(1024**2):
                digest.update(block)
        value = digest.hexdigest()
        canonical = identities.setdefault(value, path.stem)
        if value in hashes or canonical != path.stem:
            duplicate.append(path.stem)
        else:
            hashes.add(value); accepted.append(path)
    return accepted, duplicate


def check(home=HOME, storage=None):
    report = download_all(storage or Storage(home), home / 'trips')
    report['checked_at'] = time.time()
    save_json(home / 'download-report.json', report)
    return report


def interval_hours(state):
    # Task Scheduler wakes the process hourly; the process skips network/training
    # until due. The computer itself is never woken by our task.
    failures = int(state.get('plateau_count', 0))
    return 6 if failures < 3 else 24 if failures < 6 else 72


def record_outcome(state, blockers):
    state['plateau_count'] = int(state.get('plateau_count', 0)) + 1 if blockers else 0


def run(home=HOME, scheduled=False, storage=None):
    home.mkdir(parents=True, exist_ok=True)
    state = read_json(home / 'state.json', {})
    if scheduled and time.time() < state.get('next_check', 0):
        return {'status': 'not_due', 'next_check': state['next_check']}
    pending = read_json(home / 'latest.json')
    if pending:
        report = read_json(home / 'candidates' / pending['id'] / 'report.json', {})
        if report.get('status') in ('awaiting_phone_test', 'approved'):
            return {'status': 'awaiting_review', 'candidate': pending['id'],
                    'message': 'Use Phone test / Approve / Publish or Discard in the launcher.'}
    storage = storage or Storage(home)
    downloaded = check(home, storage)
    if state.get('user_id') not in (None, downloaded['user_id']):
        raise ValueError('Login changed. Keep one account per laptop-agent workspace to prevent data mixing.')
    state['user_id'] = downloaded['user_id']
    state['next_check'] = time.time() + interval_hours(state)*3600
    save_json(home / 'state.json', state)
    paths, duplicate = unique_logs([Path(p) / (Path(p).name + '.drlog') for p in downloaded['verified']],
                                   state.setdefault('content_sessions', {}))
    save_json(home / 'state.json', state)
    data, quality = build_dataset(paths)
    quality['duplicate_content_sessions'] = duplicate
    # Test membership is permanent. Previously examined test sessions are never
    # recycled into train/validation or used to select a later candidate.
    keep = ~np.isin(data['session'], state.get('used_test_sessions', []))
    data = {key: value[keep] for key, value in data.items()}
    plateau = int(state.get('plateau_count', 0))
    blockers = data_blockers(data, state, min_new_trips=5 if plateau >= 3 else 3,
                             min_new_fixes=1200 if plateau >= 3 else 600)
    quality['training_blockers'] = blockers
    save_json(home / 'quality-report.json', quality)
    if blockers:
        result = {'status': 'waiting_for_data', 'blockers': blockers,
                  'verified_trips': len(paths), 'usable_fixes': len(data['speed']),
                  'next_check_hours': interval_hours(state)}
        save_json(home / 'status.json', result); return result
    # Bound peak allocation: existing phone_learning builds all accepted windows.
    # Large collections should be archived into a new, explicitly reviewed cohort.
    if len(data['w']) > 30000:
        raise ValueError('More than 30,000 windows; select a smaller reviewed cohort before training.')
    identifier = time.strftime('%Y%m%d-%H%M%S') + '-' + hashlib.sha256(str(time.time_ns()).encode()).hexdigest()[:6]
    output = home / 'candidates' / identifier; output.mkdir(parents=True)
    base, meta, cloud_baseline = baseline(home, storage); shutil.copy2(base, output / 'baseline.tflite')
    fresh_test = sorted(set(data['session'][data['split'] == 'test']))
    state['used_test_sessions'] = sorted(set(state.get('used_test_sessions', [])) | set(fresh_test))
    state['trained_sessions'] = sorted(set(state.get('trained_sessions', [])) | set(data['session']))
    # Reserve before any fitting/scoring, including failures; an interrupted test
    # cannot accidentally become a fresh holdout on the next scheduled run.
    save_json(home / 'state.json', state)
    save_json(home / 'latest.json', {'id': identifier})
    report = {'id': identifier, 'created_at': time.time(), 'status': 'failed', 'blockers': ['Training did not complete'],
              'baseline_sha256': sha(output / 'baseline.tflite'), 'baseline_version': meta['version'],
              'baseline_validated': meta.get('validated', False), 'source_sha256': source_hash(),
              'cloud_baseline': cloud_baseline,
              'splits': {s: sorted(set(data['session'][data['split'] == s])) for s in ('train','validation','test')},
              'training_files': {str(p.relative_to(home)): sha(p) for p in paths},
              'label_source': 'Fresh raw GNSS speed in DRLOG, filtered with phone_learning.py; never app predictions'}
    save_json(output / 'report.json', report)
    try:
        legacy = train(data, output / 'baseline.tflite', output)
        save_json(output / 'training-details.json', legacy)
        if legacy.get('status') == 'blocked':
            raise ValueError('; '.join(legacy['reasons']))
        base_prediction = predictions(output / 'baseline.tflite', data['w'])
        candidate_prediction = predictions(output / 'speed-candidate.tflite', data['w'])
        test = data['split'] == 'test'
        comparison = compare_speed(data['speed'][test], base_prediction[test], candidate_prediction[test])
        # TFLite is the object scored, not the unexported ridge coefficients.
        report['speed_comparison'] = comparison
        report['candidate_sha256'] = sha(output / 'speed-candidate.tflite')
        report['blockers'] = list(comparison['blockers'])
        np.savez_compressed(output / 'holdout-predictions.npz', speed=data['speed'][test],
                            candidate=candidate_prediction[test], baseline=base_prediction[test],
                            session=data['session'][test], t_ns=data['t_ns'][test])
        # Small inference fixture for the Android runtime, drawn only from held-out trips.
        indices = np.flatnonzero(test)
        indices = indices[np.linspace(0, len(indices)-1, min(64, len(indices)), dtype=int)]
        import struct
        with (output / 'phone-parity.bin').open('wb') as f:
            f.write(struct.pack('<I', len(indices)))
            for i in indices:
                f.write(data['w'][i].astype('<f4').tobytes())
                f.write(struct.pack('<f', candidate_prediction[i]))
        try:
            report['replay'] = replay(paths, data, base_prediction, candidate_prediction, output, meta.get('validated', False))
        except Exception as error:
            report['replay'] = {'blockers': ['Navigation replay failed: ' + str(error)]}
        report['blockers'].extend(report['replay']['blockers'])
        report['status'] = 'rejected' if report['blockers'] else 'awaiting_phone_test'
        record_outcome(state, report['blockers'])
    except Exception as error:
        report['blockers'] = ['Candidate evaluation failed: ' + str(error)]
        report['status'] = 'failed'
    finally:
        state['next_check'] = time.time() + interval_hours(state)*3600
        save_json(home / 'state.json', state)
        save_json(output / 'report.json', report)
        save_json(home / 'status.json', {'status': report['status'], 'candidate': identifier,
                                        'blockers': report['blockers'], 'report': str(output / 'report.json')})
    return read_json(home / 'status.json')
