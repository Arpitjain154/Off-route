import json,struct,csv,math
from pathlib import Path
import numpy as np
out=Path('artifacts/physical-tests');reports=[]
for path in out.glob('*.drlog'):
 data=path.read_bytes();rows=np.array([struct.unpack_from('<q14fBfB',data,i) for i in range(200,len(data)-69,70)])
 trace=list(csv.DictReader(path.with_suffix('.fusion.csv').open()))
 t=rows[:,0];gpsspeed=rows[:,13];lat=rows[:,10];lon=rows[:,11];accuracy=rows[:,14]
 comparisons=[]
 for r in trace:
  i=min(len(t)-1,int(np.searchsorted(t,int(r['t_ns']))))
  if math.isfinite(gpsspeed[i]): comparisons.append((float(r['speed_mps']),gpsspeed[i],float(r['gps_age_s'])))
 a=np.array(comparisons)
 valid=np.isfinite(lat)&np.isfinite(lon)
 report=dict(drive=path.stem,duration_s=(t[-1]-t[0])/1e9,raw_gps_speed_range_mps=[float(np.nanmin(gpsspeed)),float(np.nanmax(gpsspeed))],
  speed_abs_difference_median_mps=float(np.median(abs(a[:,0]-a[:,1]))),speed_abs_difference_max_mps=float(np.max(abs(a[:,0]-a[:,1]))),
  first_gps=[float(lat[valid][0]),float(lon[valid][0])],last_gps=[float(lat[valid][-1]),float(lon[valid][-1])],
  moving_gps_but_fused_below_half=int(np.sum((a[:,1]>2)&(a[:,0]<a[:,1]/2))),
  missing_gps_trace_rows=sum(float(r['gps_age_s'])>2 or float(r['gps_age_s'])<0 for r in trace))
 reports.append(report)
(out/'analysis.json').write_text(json.dumps(reports,indent=2));print(json.dumps(reports,indent=2))
