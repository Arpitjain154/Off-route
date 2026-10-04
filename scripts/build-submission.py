"""Create a shareable submission; include public datasets, exclude local user data."""
from pathlib import Path
import base64,hashlib,json,os,re,zipfile,zlib,time
from concurrent.futures import ThreadPoolExecutor

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'submission';OUT.mkdir(exist_ok=True)
APK=ROOT/'artifacts/offroute-1.6.0-eskf-debug.apk'
DEST=OUT/'Offroute-1.6.0-Submission.zip'
PREFIX='Offroute-1.6.0/'
EXCLUDE_DIRS={'.git','.gradle','build','node_modules','__pycache__','.idea','uploads','.signing','test-results'}
EXCLUDE_NAMES={'local.properties','keystore.properties','google-services.json','capacitor.config.json'}
files={}
def collect(folder):
    for base,dirs,names in os.walk(ROOT/folder):
        dirs[:]=[d for d in dirs if d not in EXCLUDE_DIRS]
        rel=Path(base).relative_to(ROOT).as_posix()
        if rel=='android/app/src/main/assets/public':dirs.clear();continue
        for name in names:
            p=Path(base)/name
            if p.is_symlink():raise RuntimeError('Unexpected symlink: '+str(p))
            if name in EXCLUDE_NAMES or (name.startswith('.env') and name!='.env.example'):continue
            if p.suffix.lower() in {'.log','.pyc','.dpapi','.jks','.keystore','.pem','.key','.tsbuildinfo','.drlog'}:continue
            files[p.relative_to(ROOT).as_posix()]=p
for folder in ['src','android','server','scripts','ml','supabase','deploy','edge','public','data/iovnbd','data/fusion-replay','artifacts/ml']:
    collect(folder)
for name in ['.env.example','.gitignore','.dockerignore','.nvmrc','.prettierrc.json','.prettierignore','README.md','package.json','package-lock.json','capacitor.config.ts','docker-compose.yml','index.html','tsconfig.json','vite.config.ts','START-LAPTOP-AGENT.cmd']:
    if (ROOT/name).is_file():files[name]=ROOT/name
for name in ['live-eskf-and-training.md','cloud-trip-format.md','laptop-training-agent.md','problem-statement-reference.md','solution-description-report.md','supabase-app-integration-design.md','supabase-setup.md','adaptive-learning.md']:
    files['docs/'+name]=ROOT/'docs'/name
files['data/io-vnbd/README_1.pdf']=ROOT/'data/io-vnbd/README_1.pdf'
files['data/io-vnbd/README_1.txt']=ROOT/'data/io-vnbd/README_1.txt'
files['app/Offroute-1.6.0.apk']=APK

# Reject high-risk credential forms without printing their values.
for name,p in files.items():
    if p.suffix.lower() not in {'.js','.mjs','.cjs','.ts','.tsx','.java','.py','.json','.md','.txt','.yml','.yaml','.xml','.properties','.ps1','.example'} or p.stat().st_size>2_000_000:continue
    text=p.read_text(encoding='utf-8',errors='replace')
    if re.search(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|sb_secret_[A-Za-z0-9_-]{15,}|gh[pousr]_[A-Za-z0-9]{25,}|AKIA[0-9A-Z]{16}',text):raise RuntimeError('Credential pattern requires review: '+name)
    for token in re.findall(r'eyJ[A-Za-z0-9_-]+\.([A-Za-z0-9_-]+)\.[A-Za-z0-9_-]+',text):
        try:payload=json.loads(base64.urlsafe_b64decode(token+'='*(-len(token)%4)))
        except Exception:continue
        if payload.get('role') in {'service_role','authenticated'} or 'refresh_token' in payload:raise RuntimeError('Private JWT requires review: '+name)
print('Selected public source/data files; checking assets already inside APK',flush=True)
restorable=[]
with zipfile.ZipFile(APK) as apk:
    entries={x.filename:x for x in apk.infolist()}
    def compare_asset(pair):
        name,p=pair
        entry=None
        if name.startswith('public/'):entry='assets/'+name
        elif name.startswith('android/app/src/main/assets/maps/'):entry='assets/maps/'+name.split('assets/maps/',1)[1]
        if entry not in entries:return None
        data=p.read_bytes();info=entries[entry]
        if len(data)==info.file_size and zlib.crc32(data)==info.CRC:
            return {'path':name,'apkEntry':entry,'sizeBytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}
        return None
    with ThreadPoolExecutor(max_workers=12) as pool:
        for i,result in enumerate(pool.map(compare_asset,list(files.items()))):
            if result:restorable.append(result);del files[result['path']]
            if i and i%5000==0:print(f'Compared {i} asset/source entries',flush=True)
print(f'Restorable assets: {len(restorable)}; directly included files: {len(files)}',flush=True)

restore='''"""Restore source map assets from the bundled APK. Python 3, no dependencies."""
from pathlib import Path
import argparse,hashlib,json,zipfile
root=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--verify-only',action='store_true');args=p.parse_args()
manifest=json.loads((root/'SUBMISSION-MANIFEST.json').read_text())
with zipfile.ZipFile(root/'app/Offroute-1.6.0.apk') as apk:
    for item in manifest['assetsRestoredFromApk']:
        target=(root/item['path']).resolve()
        if not target.is_relative_to(root):raise RuntimeError('Unsafe destination')
        data=apk.read(item['apkEntry'])
        if len(data)!=item['sizeBytes'] or hashlib.sha256(data).hexdigest()!=item['sha256']:raise RuntimeError('Asset checksum mismatch: '+item['path'])
        if not args.verify_only:
            target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
print(('Verified ' if args.verify_only else 'Restored ')+str(len(manifest['assetsRestoredFromApk']))+' source assets.')
'''
readme='''# OFF-ROUTE 1.6.0 submission

Start here. This file describes the current submission; older project reports retain their historical dates and limitations.

## Install the app

Install `app/Offroute-1.6.0.apk` on Android 8.0 or later. It is the debug-signed version 1.6.0-offroute (version code 17), installed and exercised on a Samsung SM-S711B. Allow precise location, wait for an actual GPS fix, and mount the phone consistently for driving. Offline maps are bundled. Supabase login/configuration uses the recipient's own account; no private account credentials are included.

## Contents

- React/Capacitor UI, Java Android ESKF and sensor recorder, edge API, Express backend, Supabase migration, tests and scripts.
- Three trained TFLite models, weights, model cards, original/refined training reports, and preprocessed public training caches.
- All 144 actual synchronized categorized IO-VNBD CSVs (not Git LFS pointer stubs), their pinned revision/checksums, upstream documentation and public replay fixtures. Whole recordings were split between training, validation and evaluation.
- Offline display maps, routing graph and OSM source extracts stored once inside the APK. The restoration script reconstructs matching editable-project assets.

Personal phone IMU/GPS recordings, route histories, screenshots, account tokens/passwords, signing keys, local databases, Git history, old APKs, dependency installations, temporary files and build caches are excluded. Dependency lockfiles and installation instructions are included. This is a submission package, not a byte-for-byte backup of the development laptop.

## Rebuild

Extract the ZIP fully. In its `Offroute-1.6.0` directory, first run:

```powershell
python restore_assets.py
npm ci
npm run android:sync
```

Use Python 3.12 for the ML environment, Node.js 20+ for the web project, JDK 21, and Android SDK 36. Configure `android/local.properties` with your own Android SDK path, then:

```powershell
cd android
.\\gradlew.bat testDebugUnitTest assembleDebug
```

Dependency installation requires internet access. `python restore_assets.py --verify-only` checks all embedded source assets without writing them. Refer to `ml/README.md` for training and `docs/laptop-training-agent.md` for the laptop pipeline. Do not use `windows*.npz` as independent extra data: these caches are derived from the included CSV recordings.

## Current behavior and evidence

Live navigation uses the ESKF after mounting/alignment and sensor freshness requirements are met. AI speed observations can enter that filter after model validation and calibration. The bundled models are genuinely trained on IO-VNBD but remain `validated: false`; learned corrections are disabled pending physical validation. ESKF noise tuning and online bias estimation are distinct from neural-model training.

The final automated checks recorded 127 passing Android tests, two existing opt-in skips, 43 passing Python tests, a final 12-test Python integration check, 14 focused UI/state tests, and successful web/APK builds. A physical 30-second phone recording and Supabase upload/download were verified separately: all five sensor types and 30 GPS observations were recorded; the downloaded files produced 17 quality-accepted training windows. Only aggregate results are included, not private recordings. Legacy MongoDB and local-browser checks encountered environment timeouts.

Driving, lane-level and GPS-outage accuracy are not established. A short pipeline check is not a trained or field-validated release. See `docs/live-eskf-and-training.md` and `artifacts/ml/refinement/MODEL-CARD.md` for details and limitations.

## Public-data attribution

IO-VNBD: https://github.com/onyekpeu/IO-VNBD, pinned revision 118939602e3422d47b8ab0807b623751c3ac135b. Included upstream documentation and data manifest identify the data source; retain attribution and applicable dataset terms.

Offline maps: copyright OpenStreetMap contributors, ODbL 1.0. Map attribution is restored to `public/maps/ATTRIBUTION.md` and is embedded in the APK. Source: https://www.openstreetmap.org/copyright.

`SUBMISSION-MANIFEST.json` records file SHA-256 checksums and the source-asset restoration map.
'''
generated={'START-HERE.md':readme.encode(),'restore_assets.py':restore.encode()}
with zipfile.ZipFile(r'C:/Users/surya/Downloads/IO-VNBD-master (1).zip') as upstream:
    for name in upstream.namelist():
        parts=Path(name).parts
        if len(parts)==2 and Path(name).name.lower().startswith(('license','licence','readme')) and not name.endswith('/'):
            generated['data/iovnbd/upstream/'+Path(name).name]=upstream.read(name)

manifest={'schemaVersion':1,'appVersion':'1.6.0-offroute','personalRecordingsIncluded':False,'files':[],'assetsRestoredFromApk':restorable}
temp=DEST.with_suffix('.partial.zip')
with zipfile.ZipFile(temp,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9,allowZip64=True) as out:
    for i,(name,p) in enumerate(sorted(files.items())):
        h=hashlib.sha256()
        with p.open('rb') as source,out.open(PREFIX+name,'w',force_zip64=True) as target:
            while chunk:=source.read(1024*1024):h.update(chunk);target.write(chunk)
        manifest['files'].append({'path':name,'sizeBytes':p.stat().st_size,'sha256':h.hexdigest()})
        if i%80==0:print(f'Packed {i+1}/{len(files)} files',flush=True)
    for name,data in generated.items():
        out.writestr(PREFIX+name,data)
        manifest['files'].append({'path':name,'sizeBytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
    out.writestr(PREFIX+'SUBMISSION-MANIFEST.json',json.dumps(manifest,indent=2))
temp.replace(DEST)
print(json.dumps({'archive':str(DEST),'bytes':DEST.stat().st_size,'decimalMB':round(DEST.stat().st_size/1e6,2),'files':len(manifest['files']),'restorableAssets':len(restorable)}),flush=True)
