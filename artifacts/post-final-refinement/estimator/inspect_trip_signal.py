"""Approximate acceleration-filter replay using causal, 10 Hz saved live diagnostics.

This isolates the clamp-order effect. It is NOT full navigation replay or outage
position ground truth: gravity/forward/bias were only logged at about 10 Hz, not
at every IMU event. No parameters are fitted and no device constants are copied.
"""
from pathlib import Path
import csv,json,sys,argparse
import numpy as np
root=Path.cwd();sys.path.insert(0,str(root/'ml'));from read_drlog import load
p=argparse.ArgumentParser();p.add_argument('--session',default='60595d3e-6fee-4335-ab4e-4e523715e43e');args=p.parse_args()
folder=root/'artifacts/final-two-trips/phone'/args.session
meta,raw=load(folder/(args.session+'.drlog'))
with (folder/(args.session+'.fusion.csv')).open(encoding='utf8') as f: rows=list(csv.DictReader(f))
ts=np.array([int(r['t_ns']) for r in rows],dtype=np.int64);rt=raw['t_ns'].astype(np.int64)
index=np.searchsorted(ts,rt,side='right')-1;valid=index>=0;index=np.maximum(index,0)
def diagnostic(names):return np.array([[float(r[n]) for n in names] for r in rows])[index]
gravity=diagnostic(['ins_gravity_x','ins_gravity_y','ins_gravity_z'])
forward=diagnostic(['ins_forward_x','ins_forward_y','ins_forward_z'])
bias=diagnostic(['ins_accel_bias_mps2'])[:,0]
a=np.column_stack([raw[n] for n in ['ax','ay','az']])-gravity
w=np.column_stack([raw[n] for n in ['gx','gy','gz']])
linear=np.sum(a*forward,axis=1)-bias
shock=(np.linalg.norm(a,axis=1)>12)|(np.linalg.norm(w,axis=1)>2)
dt=np.diff(rt,prepend=rt[0])*1e-9
valid&=(rt-ts[index]<250_000_000)&(dt>0)&(dt<=.25)
old_target=np.where(shock,0,np.clip(linear,-8,5));new_target=np.where(shock,0,linear)
est=np.array([r['nav_estimated']=='true' for r in rows])
boundaries=np.flatnonzero(np.r_[True,est[1:]!=est[:-1],True])
result={'session':args.session,'phone_model':meta['phone_model'],
 'method':'Approximate scalar acceleration replay; last available 10 Hz gravity/forward/bias held causally between traces; fixed existing tau=.18 and bounds -8,+5; no fitting.',
 'limitations':['Not a full live-engine replay or position accuracy measurement.','Actual Android gravity events, compass events and exact stop detector state between traces are unavailable.','Restoring a lost acceleration impulse does not prove correct speed: mounting/gravity/bias errors can remain.'],
 'outages':[]}
for start,end in zip(boundaries[:-1],boundaries[1:]):
 if not est[start]:continue
 ix=np.flatnonzero(valid&(index>=start)&(index<end))
 if not len(ix):continue
 first=ix[0];last=ix[-1];duration=float(np.sum(dt[ix]));old=float(rows[start]['ins_accel_mps2']);new=old
 old_v=new_v=float(rows[start]['nav_speed_mps']);old_impulse=new_impulse=0.
 for k in ix:
  alpha=1-np.exp(-dt[k]/.18)
  old+=(old_target[k]-old)*alpha;new+=(new_target[k]-new)*alpha
  old_applied=old;new_applied=float(np.clip(new,-8,5))
  if rows[index[k]]['nav_stationary']=='true':old=new=old_applied=new_applied=old_v=new_v=0.
  old_impulse+=old_applied*dt[k];new_impulse+=new_applied*dt[k]
  old_v=float(np.clip(old_v+old_applied*dt[k],0,80));new_v=float(np.clip(new_v+new_applied*dt[k],0,80))
 chosen=~shock[ix]
 result['outages'].append({'start_s':float((rt[first]-rt[0])*1e-9),'duration_s':duration,'raw_samples':len(ix),
  'shock_percent':float(np.mean(shock[ix])*100),'nonshock_linear_percentiles_mps2':np.percentile(linear[ix][chosen],[0,5,50,95,100]).tolist(),
  'positive_clipped_percent':float(np.mean((linear[ix]>5)&chosen)*100),'negative_clipped_percent':float(np.mean((linear[ix]<-8)&chosen)*100),
  'raw_clamp_impulse_error_mps':float(np.sum((old_target[ix]-new_target[ix])*dt[ix])),
  'old_filtered_impulse_mps':old_impulse,'new_filtered_impulse_mps':new_impulse,
  'initial_recorded_speed_mps':float(rows[start]['nav_speed_mps']),'last_recorded_speed_mps':float(rows[end-1]['nav_speed_mps']),
  'approx_old_final_speed_mps':old_v,'approx_new_final_speed_mps':new_v,
  'next_gps_speed_mps':float(rows[end]['gps_speed_mps']) if end<len(rows) else None})
out=root/'artifacts/post-final-refinement/estimator'/('signal-'+args.session+'.json')
out.write_text(json.dumps(result,indent=2),encoding='utf8');print(json.dumps(result,indent=2))
