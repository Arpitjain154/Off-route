"""Diagnostic only: compare recorded sensor axes with the independent reference's turn rate."""
from pathlib import Path
import numpy as np,pandas as pd,json
root=Path(__file__).resolve().parents[1];out=root/'artifacts/qa-1.5/iovnbd';report=[]
for name in ['S-S1','S-S2','S-S3a']:
    d=pd.read_csv(out/f'{name}.csv');t=d.seconds.to_numpy();dt=np.diff(t)
    north=np.diff(d.reference_lat)*111320;east=np.diff(d.reference_lon)*111320*np.cos(np.deg2rad(d.reference_lat.to_numpy()[1:]));speed=np.hypot(east,north)/dt
    course=np.unwrap(np.arctan2(east,north));yaw=np.r_[np.nan,np.diff(course)/dt[1:]]
    smooth=lambda x:pd.Series(x).rolling(21,center=True,min_periods=20).mean().to_numpy()
    target=smooth(yaw);mask=np.isfinite(target)&(speed>4)&(abs(target)<1)
    axis={}
    for key in ['gx','gy','gz']:
        signal=smooth(d[key].to_numpy()[1:]);valid=mask&np.isfinite(signal)
        axis[key]={'turn_rate_correlation':float(np.corrcoef(target[valid],signal[valid])[0,1]),'std':float(np.std(signal[valid]))}
    report.append({'recording':name,'diagnostic_only':True,'axis':axis})
(out/'axis-audit.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
