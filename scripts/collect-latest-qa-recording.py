"""Read the user's new in-app recording; never alter the phone or its existing APK."""
from pathlib import Path
import subprocess,hashlib,json,sys
import numpy as np
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'ml'))
from read_drlog import load
out=ROOT/'artifacts/qa-1.5/phone-recording';out.mkdir(exist_ok=True)
adb='C:/Users/surya/AppData/Local/Android/Sdk/platform-tools/adb.exe'
stem='233db1ff-340e-487c-8a9a-9811c39040d1';files=[]
for ext in ['drlog','fusion.csv']:
    name=stem+'.'+ext
    content=subprocess.check_output([adb,'-s','RZCX80NLSNN','exec-out','run-as','com.idr.navigator','cat','files/drives/'+name])
    (out/name).write_bytes(content);files.append({'file':name,'bytes':len(content),'sha256':hashlib.sha256(content).hexdigest()})
meta,data=load(out/(stem+'.drlog'));valid=np.isfinite(data['speed_mps'])
report={'metadata':meta,'files':files,'samples':len(data),'duration_seconds':float((data['t_ns'][-1]-data['t_ns'][0])/1e9),
        'median_sample_interval_ms':float(np.median(np.diff(data['t_ns']))/1e6),
        'gps_valid_fraction':float(valid.mean()),'speed_range_mps':[float(data['speed_mps'][valid].min()),float(data['speed_mps'][valid].max())] if valid.any() else None,
        'limitation':'Recorded by the previous installed app. No independent trajectory or stationary/motion annotation; not a validation pass for 1.5.'}
(out/'manifest.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
