"""Strict implementation of docs/cloud-trip-format.md; no partial trip is visible."""
import hashlib
import json
from pathlib import Path
import re
import tempfile
import uuid
from .common import sha
from .cloud import MissingObject

SUFFIXES = ('.drlog', '.quality.csv', '.training.json', '.summary.json', '.fusion.csv', '.sensors.csv', '.gnss.csv')
MAX_TRIP = 2 * 1024**3


def identity(value):
    if not isinstance(value, str) or str(uuid.UUID(value)) != value:
        raise ValueError('Invalid canonical UUID')
    return value


def validate_manifest(value, sid):
    identity(sid)
    if not isinstance(value, dict) or value.get('schemaVersion') != 1 or value.get('source') != 'device' or value.get('sessionId') != sid:
        raise ValueError('Completion manifest identity/schema mismatch')
    files = value.get('files')
    if not isinstance(files, list) or not 4 <= len(files) <= 7:
        raise ValueError('Incomplete or oversized file list')
    seen = set(); total = 0
    for file in files:
        name = file.get('originalName')
        if name not in {sid + s for s in SUFFIXES} or name in seen:
            raise ValueError('Unexpected or duplicate file name')
        seen.add(name)
        size = file.get('sizeBytes')
        if type(size) is not int or not 0 < size <= MAX_TRIP or not re.fullmatch('[0-9a-f]{64}', str(file.get('sha256'))):
            raise ValueError('Invalid file size or hash')
        chunks = file.get('chunks')
        if not isinstance(chunks, list) or not 1 <= len(chunks) <= 10000:
            raise ValueError('Invalid chunk list')
        length = 0
        for i, chunk in enumerate(chunks):
            n = chunk.get('sizeBytes')
            if chunk.get('objectName') != name + f'.part{i:05d}' or type(n) is not int or not 0 < n <= 8 * 1024**2 or not re.fullmatch('[0-9a-f]{64}', str(chunk.get('sha256'))):
                raise ValueError('Invalid chunk name, order, size or checksum')
            length += n
        if length != size:
            raise ValueError('Chunk lengths do not equal full file length')
        total += size
    if total > MAX_TRIP or not {sid + s for s in SUFFIXES[:4]} <= seen:
        raise ValueError('Missing required files or trip too large')
    if value.get('rawSidecarsSchemaVersion') is not None:
        if value['rawSidecarsSchemaVersion'] != 1 or not {sid+'.sensors.csv',sid+'.gnss.csv'} <= seen:
            raise ValueError('Missing or unsupported raw sidecars')
    return files


def verify_cache(path, manifest):
    for file in validate_manifest(manifest, path.name):
        target = path / file['originalName']
        if target.is_symlink() or not target.is_file() or target.stat().st_size != file['sizeBytes'] or sha(target) != file['sha256']:
            raise ValueError('Local trip checksum mismatch: ' + path.name)
    provenance=json.loads((path/(path.name+'.training.json')).read_bytes())
    if not isinstance(provenance,dict):raise ValueError('Invalid training metadata')
    declared=provenance.get('rawSidecarsSchemaVersion')
    if declared is not None:
        names={f['originalName'] for f in manifest['files']}
        if declared!=1 or not {path.name+'.sensors.csv',path.name+'.gnss.csv'} <= names:
            raise ValueError('Training metadata requires missing or unsupported raw sidecars')


def download_trip(storage, uid, sid, root):
    identity(uid); identity(sid)
    prefix = f'{uid}/{sid}'
    raw = storage.get('journeys', prefix + '/complete.json', 1024**2)
    manifest = json.loads(raw)
    files = validate_manifest(manifest, sid)
    parent = Path(root) / uid; parent.mkdir(parents=True, exist_ok=True)
    target = parent / sid
    if target.exists():
        if target.is_symlink() or json.loads((target / 'complete.json').read_bytes()) != manifest:
            raise ValueError('Completed trip changed; refusing to overwrite it: ' + sid)
        verify_cache(target, manifest)
        return target
    with tempfile.TemporaryDirectory(prefix='.incoming-', dir=parent) as temporary:
        staging = Path(temporary) / sid; staging.mkdir()
        for file in files:
            with (staging / file['originalName']).open('wb') as out:
                for part in file['chunks']:
                    blob = storage.get('journeys', prefix + '/' + part['objectName'], part['sizeBytes'])
                    if len(blob) != part['sizeBytes'] or hashlib.sha256(blob).hexdigest() != part['sha256']:
                        raise ValueError('Chunk checksum mismatch: ' + part['objectName'])
                    out.write(blob)
        verify_cache(staging, manifest)
        (staging / 'complete.json').write_bytes(raw)
        staging.rename(target)
    return target


def download_all(storage, root):
    storage.ensure_login(); uid = identity(storage.user)
    report = {'user_id': uid, 'verified': [], 'incomplete': [], 'errors': []}
    for item in storage.list('journeys', uid):
        sid = item.get('name', '')
        try:
            identity(sid)
        except (ValueError, AttributeError):
            continue
        try:
            path = download_trip(storage, uid, sid, root)
            report['verified'].append(str(path))
        except MissingObject:
            report['incomplete'].append(sid)
        except (ValueError, OSError, RuntimeError, TypeError, KeyError) as error:
            report['errors'].append({'session': sid, 'reason': str(error)})
    return report
