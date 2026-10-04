"""User-scoped Supabase REST access. Refresh tokens are Windows DPAPI protected."""
import ctypes
from ctypes import wintypes
import getpass
import json
import os
from pathlib import Path
import time
from urllib.parse import quote
import requests
from .common import HOME, URL, PUBLIC_KEY, atomic_bytes


class MissingObject(FileNotFoundError):
    pass


def protect(data, decrypt=False):
    if os.name != 'nt':
        raise RuntimeError('Saved login requires Windows DPAPI.')
    class Blob(ctypes.Structure):
        _fields_ = [('size', wintypes.DWORD), ('data', ctypes.POINTER(ctypes.c_char))]
    buffer = ctypes.create_string_buffer(data)
    source = Blob(len(data), ctypes.cast(buffer, ctypes.POINTER(ctypes.c_char)))
    output = Blob()
    crypt = ctypes.windll.crypt32
    if decrypt:
        ok = crypt.CryptUnprotectData(ctypes.byref(source), None, None, None, None, 1, ctypes.byref(output))
    else:
        ok = crypt.CryptProtectData(ctypes.byref(source), 'OFF-ROUTE laptop login', None, None, None, 1, ctypes.byref(output))
    if not ok:
        raise RuntimeError('Windows could not unlock this login. Run Login as the same Windows user.')
    try:
        return ctypes.string_at(output.data, output.size)
    finally:
        ctypes.windll.kernel32.LocalFree(output.data)


class Storage:
    def __init__(self, home=HOME, publisher_key=None):
        self.home = Path(home)
        self.publisher_key = publisher_key
        self.token = None
        self.user = None
        self.expires = 0
        self.session = requests.Session()

    def request(self, method, path, *, data=None, limit=1024*1024, headers=None, missing=False):
        h = {'apikey': self.publisher_key or PUBLIC_KEY}
        if self.token:
            h['Authorization'] = 'Bearer ' + self.token
        elif self.publisher_key and not self.publisher_key.startswith('sb_secret_'):
            h['Authorization'] = 'Bearer ' + self.publisher_key
        h.update(headers or {})
        for attempt in range(3):
            try:
                with self.session.request(method, URL + path, data=data, headers=h,
                                          timeout=(15, 60), allow_redirects=False, stream=True) as r:
                    if (r.status_code == 429 or r.status_code >= 500) and attempt < 2:
                        time.sleep(2 ** attempt); continue
                    if not 200 <= r.status_code < 300:
                        # Storage sometimes wraps a missing object in HTTP 400; do not
                        # treat permission/network failures as absent manifests.
                        error = r.raw.read(16384)
                        try:
                            detail = json.loads(error)
                        except ValueError:
                            detail = {}
                        if missing and (r.status_code == 404 or
                                        str(detail.get('statusCode')) == '404'):
                            raise MissingObject(path)
                        raise RuntimeError(f'Supabase request failed (HTTP {r.status_code}). '
                                           'Check login, connection and storage permissions.')
                    parts = []; count = 0
                    for block in r.iter_content(65536):
                        count += len(block)
                        if count > limit:
                            raise ValueError('Cloud response exceeds its size limit')
                        parts.append(block)
                    return b''.join(parts)
            except requests.RequestException:
                if attempt == 2:
                    raise RuntimeError('Cannot reach Supabase; check your internet connection.') from None
                time.sleep(2 ** attempt)

    def auth(self, grant, body):
        raw = self.request('POST', '/auth/v1/token?grant_type=' + grant,
                           data=json.dumps(body).encode(), headers={'Content-Type': 'application/json'})
        value = json.loads(raw)
        self.token = value['access_token']; self.user = value['user']['id']
        self.expires = time.time() + value.get('expires_in', 3600) - 90
        atomic_bytes(self.home / 'login.dpapi', protect(json.dumps({
            'refresh_token': value['refresh_token'], 'user_id': self.user, 'url': URL}).encode()))

    def login(self):
        email = input('Email used to sign in inside OFF-ROUTE: ').strip()
        password = getpass.getpass('App account password (hidden): ')
        self.auth('password', {'email': email, 'password': password})
        print('Login saved securely for this Windows account.')

    def ensure_login(self):
        if self.publisher_key or (self.token and time.time() < self.expires):
            return
        path = self.home / 'login.dpapi'
        if not path.exists():
            raise RuntimeError('No saved app login. Open the launcher and choose Login first.')
        saved = json.loads(protect(path.read_bytes(), decrypt=True))
        if saved['url'] != URL:
            raise RuntimeError('Saved login belongs to a different project.')
        self.auth('refresh_token', {'refresh_token': saved['refresh_token']})
        if self.user != saved['user_id']:
            raise RuntimeError('Cloud account identity changed; run Login again.')

    def get(self, bucket, path, limit):
        self.ensure_login()
        return self.request('GET', '/storage/v1/object/authenticated/' + quote(bucket, safe='') + '/' +
                            quote(path, safe='/'), limit=limit, missing=True)

    def list(self, bucket, prefix):
        self.ensure_login(); offset = 0
        while True:
            body = {'prefix': prefix, 'limit': 100, 'offset': offset,
                    'sortBy': {'column': 'name', 'order': 'asc'}}
            page = json.loads(self.request('POST', '/storage/v1/object/list/' + quote(bucket, safe=''),
                                          data=json.dumps(body).encode(), headers={'Content-Type': 'application/json'}))
            if not isinstance(page, list):
                raise ValueError('Invalid cloud listing')
            yield from page
            if len(page) < 100:
                break
            offset += len(page)

    def put(self, bucket, path, value, upsert=False):
        if not self.publisher_key:
            raise RuntimeError('Publishing requires a separate administrator key, entered interactively.')
        self.request('POST', '/storage/v1/object/' + quote(bucket, safe='') + '/' + quote(path, safe='/'),
                     data=value, headers={'Content-Type': 'application/json' if path.endswith('.json') else
                                          'application/octet-stream', 'x-upsert': str(upsert).lower()})

    def delete(self, bucket, path):
        if not self.publisher_key:
            raise RuntimeError('Publisher key required')
        self.request('DELETE', '/storage/v1/object/' + quote(bucket, safe=''),
                     data=json.dumps({'prefixes': [path]}).encode(), headers={'Content-Type':'application/json'})
