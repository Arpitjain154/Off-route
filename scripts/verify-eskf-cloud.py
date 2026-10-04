"""Download only the physical ESKF test trip; verify its phone/cloud bytes and ML inputs."""
import hashlib
import json
from pathlib import Path
import sys

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'ml'))
from laptop_agent.cloud import Storage
from laptop_agent.ingest import download_trip
from phone_learning import read_session

root=Path(__file__).resolve().parents[1]/'artifacts/eskf-phone'
sid=json.loads((root/'test-session.json').read_text())['sessionId']
storage=Storage()
storage.ensure_login()
target=download_trip(storage,storage.user,sid,root/'cloud')
manifest=json.loads((target/'complete.json').read_text())
checks=[]
for entry in manifest['files']:
    name=entry['originalName']
    phone=root/sid/name
    cloud=target/name
    identical=phone.read_bytes()==cloud.read_bytes()
    if name.endswith('.summary.json'):
        original=json.loads(phone.read_bytes())
        portable={key:original[key] for key in ('durationSeconds','distanceMeters','phoneModel','sizeBytes') if key in original}
        if json.loads(cloud.read_bytes())!=portable:raise RuntimeError('Portable summary mismatch')
        comparison='portable summary matches; device-local path intentionally excluded'
    else:
        if not identical:raise RuntimeError('Phone/cloud mismatch: '+name)
        comparison='byte-identical to phone'
    checks.append({'file':name,'bytes':cloud.stat().st_size,'sha256':hashlib.sha256(cloud.read_bytes()).hexdigest(),'comparison':comparison})
w,y,t,a=read_session(target/(sid+'.drlog'))
if not len(y):raise RuntimeError('Downloaded recording produced no training windows')
report={'sessionId':sid,'verified':True,'files':checks,'training_windows':len(y),'window_shape':list(w.shape),'audit':a}
(root/'cloud-round-trip-validation.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
