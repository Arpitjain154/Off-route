"""Read every submission entry and verify source assets embedded in its APK."""
from pathlib import Path
import hashlib,io,json,zipfile,sys

root=Path(__file__).resolve().parents[1]
path=Path(sys.argv[1]) if len(sys.argv)>1 else root/'submission/Offroute-1.6.0-Submission.zip'
prefix='Offroute-1.6.0/'
with zipfile.ZipFile(path) as archive:
    names=archive.namelist()
    if len(names)!=len(set(names)):raise RuntimeError('Duplicate archive entries')
    bad=archive.testzip()
    if bad:raise RuntimeError('ZIP integrity error: '+bad)
    manifest=json.loads(archive.read(prefix+'SUBMISSION-MANIFEST.json'))
    expected={prefix+v['path'] for v in manifest['files']}|{prefix+'SUBMISSION-MANIFEST.json'}
    if set(names)!=expected:raise RuntimeError('Manifest/file list mismatch')
    for entry in manifest['files']:
        name=entry['path'];parts=Path(name).parts
        if any(p in {'.git','.venv-ml','node_modules','recovery','uploads','cloud-queue'} for p in parts) or name.startswith(('artifacts/eskf-phone/','artifacts/laptop-agent/')):raise RuntimeError('Excluded folder included: '+name)
        if Path(name).suffix.lower() in {'.dpapi','.jks','.keystore','.drlog'}:raise RuntimeError('Private-data type included: '+name)
        if any(p.startswith('.env') and p!='.env.example' for p in parts):raise RuntimeError('Live environment included')
        content=archive.read(prefix+name)
        if len(content)!=entry['sizeBytes'] or hashlib.sha256(content).hexdigest()!=entry['sha256']:raise RuntimeError('File checksum mismatch: '+name)
    print('All archive file hashes and ZIP checks passed',flush=True)
    with zipfile.ZipFile(io.BytesIO(archive.read(prefix+'app/Offroute-1.6.0.apk'))) as apk:
        for entry in manifest['assetsRestoredFromApk']:
            content=apk.read(entry['apkEntry'])
            if len(content)!=entry['sizeBytes'] or hashlib.sha256(content).hexdigest()!=entry['sha256']:raise RuntimeError('Restored asset mismatch: '+entry['path'])
    data_manifest=json.loads(archive.read(prefix+'data/iovnbd/manifest.json'))
    for entry in data_manifest['files']:
        content=archive.read(prefix+'data/iovnbd/'+entry['path'])
        if hashlib.sha256(content).hexdigest()!=entry['sha256']:raise RuntimeError('Dataset provenance mismatch')
    report={'archive':path.name,'sizeBytes':path.stat().st_size,'sizeMB':round(path.stat().st_size/1e6,2),'zipIntegrity':'passed','fileChecksums':'passed','files':len(manifest['files']),'restorableAssetsVerified':len(manifest['assetsRestoredFromApk']),'publicDatasetCsvsVerified':len(data_manifest['files']),'personalRecordingsIncluded':False}
with path.open('rb') as source:report['sha256']=hashlib.file_digest(source,'sha256').hexdigest()
path.with_suffix('.verification.json').write_text(json.dumps(report,indent=2))
path.with_suffix('.sha256').write_text(report['sha256']+'  '+path.name+'\n')
print(json.dumps(report,indent=2))
