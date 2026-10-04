"""Real IO-VNBD phone replay fixtures. Vehicle GNSS is evaluation-only, never a filter input."""
import json,hashlib
from pathlib import Path
import numpy as np
import pandas as pd
ROOT=Path(__file__).resolve().parents[1]; DATA=ROOT/'data/iovnbd'; OUT=ROOT/'data/fusion-replay'
OUT.mkdir(exist_ok=True)
manifest=json.loads((DATA/'manifest.json').read_text());entries={x['path']:x for x in manifest['files']}
audit=[]
for rel in [p for p in entries if 'Driver A' in p and Path(p).name.startswith('S-')][:3]:
    vrel=str(Path(rel).with_name('V-'+Path(rel).name[2:])).replace('\\','/')
    for p in (rel,vrel): assert hashlib.sha256((DATA/p).read_bytes()).hexdigest()==entries[p]['sha256']
    s=pd.read_csv(DATA/rel,encoding='latin1');v=pd.read_csv(DATA/vrel,encoding='latin1')
    clock=s.iloc[:,8].astype(str).str.extract(r'(\d{2}):(\d{2}):(\d{2})[:_\.](\d+)').astype(float)
    st=(clock[0]*3600+clock[1]*60+clock[2]+clock[3]/1000).to_numpy();st=st-st[0]
    vt=pd.to_numeric(v.iloc[:,1],errors='coerce').to_numpy();vt=vt-vt[0]
    order=np.argsort(vt);vt=vt[order];v=v.iloc[order];ix=np.searchsorted(vt,st).clip(0,len(v)-1)
    left=(ix-1).clip(0);ix=np.where(abs(vt[left]-st)<abs(vt[ix]-st),left,ix)
    numeric=lambda a:pd.to_numeric(a,errors='coerce').to_numpy()
    values=np.column_stack([st,s.iloc[:,[9,10,11,15,16,17]].apply(pd.to_numeric,errors='coerce'),
        numeric(s.iloc[:,0]),numeric(s.iloc[:,1]),numeric(s.iloc[:,4]),numeric(s.iloc[:,3]),numeric(s.iloc[:,5]),
        numeric(v.iloc[ix,2]),numeric(v.iloc[ix,3]),s.iloc[:,12:15].apply(pd.to_numeric,errors='coerce')])
    mask=(st<=600)&(abs(vt[ix]-st)<=.061)&np.isfinite(values).all(1)
    name=Path(rel).stem+'.csv';np.savetxt(OUT/name,values[mask],delimiter=',',fmt='%.9f',
        header='seconds,ax,ay,az,gx,gy,gz,phone_lat,phone_lon,accuracy,speed,bearing,reference_lat,reference_lon,gravity_x,gravity_y,gravity_z',comments='')
    changed=np.flatnonzero(np.r_[False,np.any(np.diff(values[:,7:9],axis=0)!=0,axis=1)])
    interval=np.diff(st[changed]);coords=values[changed,7:9]
    derived=np.hypot(np.diff(coords[:,0])*111320,np.diff(coords[:,1])*111320*np.cos(np.deg2rad(coords[1:,0])))/interval
    measured=values[changed[1:],10];moving=(measured>3)&(derived>3)&(interval<20)
    ratio=float(np.median(derived[moving]/measured[moving]))
    assert .65<ratio<1.4, 'Recheck phone speed units for '+rel
    audit.append({'source':rel,'fixture':name,'rows':int(mask.sum()),'source_sha256':entries[rel]['sha256'],
        'median_coordinate_update_seconds':float(np.median(interval)),
        'coordinate_speed_to_raw_speed_ratio':ratio,'phone_speed_unit_inference':'m/s despite Kmh header; ratio from coordinate changes supports m/s'})
(OUT/'manifest.json').write_text(json.dumps({'revision':manifest['revision'],'recordings':audit,
    'limitations':['Gyro columns kept in dataset order; axis mapping to Android requires device calibration.',
    '10 Hz IMU is sample-held to 100 Hz for the live filter. This adds no signal detail.',
    'Repeated coordinate/speed/bearing tuples are not new fixes. Missing speed/course accuracies assumed 0.5 m/s and 10 degrees for replay only.',
    'Phone speed is inferred as m/s from coordinate displacement, despite Kmh header. See per-recording audit ratios.',
    'Vehicle GNSS reference is not surveyed ground truth. Outages are deliberately injected, not actual tunnel labels.']},indent=2))
print(json.dumps(audit,indent=2))
