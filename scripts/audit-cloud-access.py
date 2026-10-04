"""Read-only Supabase exposure checks. Never prints tokens or recording content."""
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'ml'))
import requests
from laptop_agent.common import HOME, URL, PUBLIC_KEY
from laptop_agent.cloud import Storage

out = ROOT / 'artifacts/security-audit'
out.mkdir(parents=True, exist_ok=True)
download = json.loads((HOME / 'download-report.json').read_text())
uid = download['user_id']
trip = Path(download['verified'][0])
manifest = json.loads((trip / 'complete.json').read_text())
chunk = next(f for f in manifest['files'] if f['originalName'].endswith('.drlog'))['chunks'][0]['objectName']
prefix = uid + '/' + trip.name
results = []

def check(label, method, path, headers, payload=None):
    with requests.request(method, URL + path, headers=headers, json=payload,
                          timeout=(10, 20), allow_redirects=False, stream=True) as r:
        item = {'check': label, 'http_status': r.status_code}
        if 'list' in label or label in ('anonymous-buckets', 'anonymous-rest-schema', 'auth-settings'):
            raw = r.raw.read(131072, decode_content=True)
            try:
                body = json.loads(raw)
                if isinstance(body, list):
                    item['visible_entries'] = len(body)
                if label == 'anonymous-rest-schema' and isinstance(body, dict):
                    item['exposed_paths'] = sorted(body.get('paths', {}))
                if label == 'auth-settings' and isinstance(body, dict):
                    item['settings'] = {k: body.get(k) for k in ('disable_signup', 'mailer_autoconfirm', 'phone_autoconfirm')}
            except (ValueError, TypeError):
                pass
        results.append(item)

try:
    # Public key is shipped in every APK. Test both no credentials and key-only access.
    for name, headers in [('no-key', {}), ('publishable-key', {'apikey': PUBLIC_KEY})]:
        for obj, suffix in [('marker', 'complete.json'), ('recording', chunk)]:
            for route in ('public', 'authenticated'):
                check(name + '-' + route + '-' + obj, 'GET',
                      '/storage/v1/object/' + route + '/journeys/' + prefix + '/' + suffix, headers)
        for path in ('', uid, prefix):
            check(name + '-trip-list-' + ('root' if not path else 'user' if path == uid else 'session'),
                  'POST', '/storage/v1/object/list/journeys', headers,
                  {'prefix': path, 'limit': 10, 'offset': 0})
        check(name + '-model-list', 'POST', '/storage/v1/object/list/model-releases', headers,
              {'prefix': '', 'limit': 10, 'offset': 0})
    public = {'apikey': PUBLIC_KEY}
    check('anonymous-buckets', 'GET', '/storage/v1/bucket', public)
    check('anonymous-rest-schema', 'GET', '/rest/v1/', public)
    check('auth-settings', 'GET', '/auth/v1/settings', public)
    # Use the existing app login through its ordinary refresh flow, never export it.
    storage = Storage(); storage.ensure_login()
    signed = {'apikey': PUBLIC_KEY, 'Authorization': 'Bearer ' + storage.token}
    check('owner-marker', 'GET', '/storage/v1/object/authenticated/journeys/' + prefix + '/complete.json', signed)
    check('owner-recording', 'GET', '/storage/v1/object/authenticated/journeys/' + prefix + '/' + chunk, signed)
    check('owner-root-list', 'POST', '/storage/v1/object/list/journeys', signed,
          {'prefix': '', 'limit': 20, 'offset': 0})
    check('owner-model-list', 'POST', '/storage/v1/object/list/model-releases', signed,
          {'prefix': '', 'limit': 20, 'offset': 0})
except requests.RequestException as error:
    # Don't print requests exceptions: they may include URL or authentication context.
    results.append({'network_error': type(error).__name__})
except Exception as error:
    results.append({'error': type(error).__name__})
report = {'checked_at': datetime.now(timezone.utc).isoformat(), 'checks': results,
          'limitations': ['No second account used; cross-account isolation needs a separate test.',
                          'No uploads, deletes, policy changes, or new accounts.',
                          'Read checks do not prove past access history.']}
(out / 'cloud-access.json').write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
if any('network_error' in r or 'error' in r for r in results):
    sys.exit(1)
