"""Rebuild PPT graphics from saved local evidence; synthetic cases are explicit."""
from pathlib import Path
import json, hashlib, zipfile
import numpy as np
import pandas as pd
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch

OUT=Path(__file__).resolve().parent
ROOT=OUT.parents[1]
SRC=ROOT/'artifacts/qa-1.5/iovnbd'
plt.rcParams.update({'font.family':'DejaVu Sans','font.size':12,'axes.spines.top':False,'axes.spines.right':False,'axes.titleweight':'bold','svg.fonttype':'none'})
COL=['#75849a','#de7837','#2379bc','#009c86']
sources=[]
def read(p):
    sources.append(p)
    return json.loads(p.read_text())
def csv(p):
    sources.append(p)
    return pd.read_csv(p)
def save(fig,name,footer):
    fig.text(.045,.025,footer,fontsize=9,color='#526073')
    fig.subplots_adjust(left=.09,right=.97,bottom=.18,top=.84,wspace=.28)
    for ext in ['png','svg','pdf']: fig.savefig(OUT/f'{name}.{ext}',dpi=220,facecolor='white')
    plt.close(fig)
def axes(title,n=1):
    fig,ax=plt.subplots(1,n,figsize=(13.33,7.5))
    fig.suptitle(title,x=.045,ha='left',y=.965,fontsize=23,fontweight='bold',color='#102a43')
    return fig,ax
base=read(ROOT/'artifacts/ml/training-report.json')
refined=read(ROOT/'artifacts/ml/refinement/report.json')
summary=csv(SRC/'drift-summary.csv')
allrows=[]; windows=[]
for name in ['S-S1','S-S2','S-S3a']:
    d=csv(SRC/f'{name}-positions.csv')
    for start in [60,180,300,420,540]:
        w=d[(d.seconds>=start)&(d.seconds<start+30)&d.outage].copy()
        scale=111320*np.cos(np.deg2rad(w.reference_lat.iloc[0]))
        rx=(w.reference_lon-w.reference_lon.iloc[0]).to_numpy()*scale
        ry=(w.reference_lat-w.reference_lat.iloc[0]).to_numpy()*111320
        ex=(w.estimated_lon-w.estimated_lon.iloc[0]).to_numpy()*scale
        ey=(w.estimated_lat-w.estimated_lat.iloc[0]).to_numpy()*111320
        er=np.hypot(ex-rx,ey-ry)
        absolute=np.hypot((w.estimated_lon-w.reference_lon)*scale,(w.estimated_lat-w.reference_lat)*111320)
        delta_lat=d.reference_lat.diff().loc[w.index].to_numpy()
        delta_lon=d.reference_lon.diff().loc[w.index].to_numpy()
        dist=np.hypot(delta_lat*111320,delta_lon*111320*np.cos(np.deg2rad(w.reference_lat))).sum()
        endpoint=np.hypot((ey-ry)[-1],(ex-rx)[-1]*np.cos(np.deg2rad(w.reference_lat.iloc[-1]))/np.cos(np.deg2rad(w.reference_lat.iloc[0])))
        row={'recording':name,'start_s':float(w.seconds.iloc[0]),'samples':len(w),'ate_rmse_m':float(np.sqrt(np.mean(absolute**2))), 'start_aligned_rmse_m':float(np.sqrt(np.mean(er**2))),'relative_endpoint_m':float(endpoint),'reference_distance_m':float(dist),'drift_percent':float(100*endpoint/max(1,dist)),'ai_used_fraction':float((w.ai_weight>0).mean())}
        allrows.append(row)
        windows.append((name,start,w,rx,ry,ex,ey,er))
metrics=pd.DataFrame(allrows)
metrics.to_csv(OUT/'real_outage_metrics.csv',index=False)
assert np.max(np.abs(metrics.drift_percent.to_numpy()-summary.drift_percent.to_numpy()))<.02
passes=int((metrics.drift_percent<10).sum())
median=float(metrics.drift_percent.median())
# All windows, without selecting only the best example.
fig,ax=axes('Recorded-data replay: all 15 GNSS outages')
bars=ax.bar(np.arange(15),metrics.drift_percent,color=[COL[i//5+1] for i in range(15)])
ax.set_yscale('log'); ax.axhline(10,color='#9e3030',ls='--',label='10% target')
ax.set_ylim(4,1600); ax.set_ylabel('Relative endpoint drift (%) · logarithmic scale')
ax.set_xticks(np.arange(15),[f'{r.recording}\n{r.start_s:.0f}s' for r in metrics.itertuples()],fontsize=9)
for b,v in zip(bars,metrics.drift_percent): ax.text(b.get_x()+b.get_width()/2,v*1.13,f'{v:.1f}',ha='center',fontsize=10)
ax.set_title(f'{passes}/15 below 10%  |  Median {median:.1f}%  |  Injected 30-second outages',loc='left',fontsize=14)
ax.legend(); ax.grid(axis='y',alpha=.15)
save(fig,'01_real_outage_drift','Source: saved IO-VNBD replay CSVs; reused Driver A data; no map matching; AI speed corrections inactive.\nHistorical replay, not a benchmark of the October 4 live ESKF update. Vehicle GNSS is the reference, not surveyed truth.')
# Select median-ranked endpoint drift, rather than a visually flattering window.
eligible=metrics[metrics.reference_distance_m>=100]
idx=int((eligible.drift_percent-median).abs().idxmin())
name,start,w,rx,ry,ex,ey,er=windows[idx]; m=allrows[idx]
pd.DataFrame({'seconds':w.seconds.to_numpy(),'reference_east_m':rx,'reference_north_m':ry,'estimated_east_m':ex,'estimated_north_m':ey,'start_aligned_error_m':er}).to_csv(OUT/'real_example_trajectory.csv',index=False)
fig,axs=axes('Reference path vs estimated path',2)
axs[0].plot(rx,ry,lw=3,color=COL[3],label='Vehicle GNSS reference')
axs[0].plot(ex,ey,lw=3,color=COL[1],label='Saved inertial estimate')
axs[0].scatter([0],[0],c='#102a43',s=35,label='Aligned start')
axs[0].set_aspect('equal',adjustable='datalim'); axs[0].set_xlabel('East displacement (m)'); axs[0].set_ylabel('North displacement (m)');axs[0].legend(fontsize=10)
axs[1].plot(w.seconds-w.seconds.iloc[0],er,lw=3,color=COL[1]);axs[1].set_xlabel('Elapsed outage time (s)');axs[1].set_ylabel('Start-aligned position error (m)')
for ax in axs: ax.grid(alpha=.2)
axs[1].set_title(f'Endpoint {m["relative_endpoint_m"]:.1f} m · Drift {m["drift_percent"]:.1f}%\nAligned RMSE {m["start_aligned_rmse_m"]:.1f} m',fontsize=13,loc='left')
save(fig,'02_real_reference_vs_estimate',f'{name}, near {start}s; closest-to-median drift among windows with at least 100 m travel. Paths translated to start at zero.\nAbsolute ATE RMSE: {m["ate_rmse_m"]:.1f} m. Aligned RMSE removes initial offset; neither metric is surveyed ground-truth accuracy.')
# Speed numbers from model reports, not implied end-to-end ESKF outcomes.
speed=base['models']['speed']; refined_mae=refined['models']['speed']['regression_driver_a']['mae_mps']
sp=pd.DataFrame({'model':['Training-median baseline','Original speed model 1.0','Refined speed model 1.1'],'mae_mps':[speed['median_baseline_mae_mps'],speed['test_mae_mps'],refined_mae]})
sp['mae_kmh']=sp.mae_mps*3.6; sp.to_csv(OUT/'real_speed_model_metrics.csv',index=False)
fig,ax=axes('Speed prediction: saved IO-VNBD model results')
b=ax.barh(sp.model,sp.mae_mps,color=[COL[0],COL[2],COL[3]],height=.58);ax.invert_yaxis();ax.set_xlim(0,7.6);ax.set_xlabel('Mean absolute error (m/s) · lower is better')
for bar,val in zip(b,sp.mae_mps):ax.text(val+.12,bar.get_y()+bar.get_height()/2,f'{val:.2f} m/s  ({val*3.6:.2f} km/h)',va='center',fontsize=13)
ax.grid(axis='x',alpha=.15); fig.subplots_adjust(left=.27)
fig.text(.045,.89,f'Original model RMSE: {speed["test_rmse_mps"]:.2f} m/s  |  Refined RMSE not reported in the selected source',fontsize=13)
# shorter tick labels avoid margin conflict with common save layout
ax.set_yticks([0,1,2],['Median\nbaseline','Original\n1.0','Refined\n1.1'])
save(fig,'03_real_speed_model_comparison','Sources: artifacts/ml/training-report.json and artifacts/ml/refinement/report.json. CAN speed labels.\nDriver A: 30,539 windows; reused during refinement. These are speed-model scores, not active AI-fusion or outage-speed scores.')
fig,ax=axes('Training data: recording-level partitions')
counts=base['split_counts']; labels=['train','validation','test']; vals=[counts[k] for k in labels]
b=ax.bar(['Training','Validation','Driver A test / regression'],vals,color=[COL[2],COL[0],COL[3]],width=.55)
for bar,v in zip(b,vals):ax.text(bar.get_x()+bar.get_width()/2,v+1300,f'{v:,}',ha='center',fontsize=18)
ax.set_ylim(0,80000);ax.set_ylabel('Three-second windows');ax.grid(axis='y',alpha=.15)
save(fig,'04_real_dataset_split','Source: artifacts/ml/training-report.json and MODEL-CARD.md. 144 paired-subset CSV files; 72 paired recordings.\nOne-second hop; whole-recording partitions. 10 Hz IMU repeated to a 300 × 6 interface adds no signal detail.')
# Explicit hypothetical sensitivity model, not an implementation of named algorithms.
dt=.1;t=np.arange(0,120+dt/2,dt);v=np.full_like(t,60/3.6)
heading=.002*t+.08*np.sin(t/18)
rx=np.r_[0,np.cumsum(v[1:]*np.cos(heading[1:])*dt)]
ry=np.r_[0,np.cumsum(v[1:]*np.sin(heading[1:])*dt)]
scenario_names=['Last GNSS speed','ESKF surrogate','ESKF + AI surrogate','ESKF + AI + map surrogate']
series=[];synmetrics=[]
for j,label in enumerate(scenario_names):
    if j==0: vs=v+.8; hs=np.zeros_like(t)
    else:
        bias=[0,.028,.012,.012][j]; yaw=[0,.0009,.0004,.0004][j]
        vs=v+bias*t+.12*np.sin(t/7); hs=heading+yaw*t
    ex=np.r_[0,np.cumsum(vs[1:]*np.cos(hs[1:])*dt)];ey=np.r_[0,np.cumsum(vs[1:]*np.sin(hs[1:])*dt)]
    if j==3:
        # An oracle road-normal correction for illustration; this is not real map matching.
        dx=ex-rx;dy=ey-ry;cross=-dx*np.sin(heading)+dy*np.cos(heading)
        ex+=.8*cross*np.sin(heading);ey-=.8*cross*np.cos(heading)
        velx=np.gradient(ex,t);vely=np.gradient(ey,t);vs=np.hypot(velx,vely)
    err=np.hypot(ex-rx,ey-ry)
    series.append((label,ex,ey,vs,err))
    for dur in [30,60,120]:
        mask=t<=dur; se=vs[mask]-v[mask]
        synmetrics.append({'evidence':'SYNTHETIC SENSITIVITY EXAMPLE','configuration':label,'outage_s':dur,'position_rmse_m':float(np.sqrt(np.mean(err[mask]**2))),'endpoint_m':float(err[mask][-1]),'drift_percent':float(100*err[mask][-1]/(dur*60/3.6)),'speed_mae_mps':float(np.mean(abs(se))),'speed_rmse_mps':float(np.sqrt(np.mean(se**2)))})
    pd.DataFrame({'evidence':'SYNTHETIC','seconds':t,'reference_east_m':rx,'reference_north_m':ry,'reference_speed_mps':v,'estimated_east_m':ex,'estimated_north_m':ey,'estimated_speed_mps':vs,'error_m':err}).to_csv(OUT/f'synthetic_variant_{j+1}.csv',index=False)
syn=pd.DataFrame(synmetrics);syn.to_csv(OUT/'synthetic_test_matrix.csv',index=False)
fig,axs=axes('SYNTHETIC example: 4 variants × 3 outage durations',2)
for j,label in enumerate(scenario_names):
    s=syn[syn.configuration==label]
    axs[0].plot(s.outage_s,s.position_rmse_m,'o-',lw=2.5,color=COL[j],label=label)
    axs[1].plot(s.outage_s,s.speed_mae_mps,'o-',lw=2.5,color=COL[j],label=label)
for ax in axs:ax.set_xticks([30,60,120]);ax.set_xlabel('Outage duration (s)');ax.grid(alpha=.2)
axs[0].set_ylabel('Position RMSE (m)');axs[1].set_ylabel('Speed MAE (m/s)');axs[0].legend(fontsize=9)
save(fig,'05_SYNTHETIC_test_matrix','ILLUSTRATIVE ONLY. Constant reference speed 60 km/h; curved path; assumed speed/yaw errors and oracle road correction.\nNo ESKF, neural model or real map matcher is run here. Assumptions determine the ordering; these are not OFFROUTE results.')
fig,axs=axes('SYNTHETIC example: trajectory and speed error',2)
axs[0].plot(rx,ry,color='#102a43',lw=3,label='Synthetic reference')
for j,(label,ex,ey,vs,err) in enumerate(series):
    axs[0].plot(ex,ey,color=COL[j],lw=2,label=label)
    axs[1].plot(t,vs-v,color=COL[j],lw=2)
axs[0].set_xlabel('East (m)');axs[0].set_ylabel('North (m)');axs[0].set_aspect('equal',adjustable='datalim');axs[0].legend(fontsize=8)
axs[1].set_xlabel('Outage duration (s)');axs[1].set_ylabel('Signed speed error (m/s)')
for ax in axs:ax.grid(alpha=.2)
save(fig,'06_SYNTHETIC_paths_and_speed','ILLUSTRATIVE ONLY. Deterministic surrogate errors, not trained-model predictions. See README for the exact assumptions.\nA perfect initial speed on a straight, constant-speed path would give zero error for the last-speed baseline.')
# Architecture graphic based on current project documentation.
fig,ax=axes('AI measurements within the ESKF architecture'); ax.set_axis_off(); ax.set_xlim(0,12);ax.set_ylim(0,6)
def box(x,y,w,h,text,color='#e9f2fa'):
    ax.add_patch(FancyBboxPatch((x,y),w,h,boxstyle='round,pad=.08',facecolor=color,edgecolor='#718096'))
    ax.text(x+w/2,y+h/2,text,ha='center',va='center',fontsize=12)
def arrow(a,b):ax.annotate('',xy=b,xytext=a,arrowprops={'arrowstyle':'->','lw':2,'color':'#40566e'})
box(.1,4.4,2.6,1.1,'IMU / gravity\nPropagation + alignment')
box(.1,2.5,2.6,1.1,'GNSS health rules\nAccept / reject fixes')
box(.1,.4,2.6,1.1,'Neural speed model\nIMU windows')
box(3.6,.4,3,1.1,'Validation + calibration\nUncertain speed observation')
box(4.3,3,3,1.5,'15-error-state ESKF\nPosition · velocity · biases','#d9f1e9')
box(8.4,4.4,3.2,1.1,'Offline road observations\nCross-track constraint')
box(8.4,2.1,3.2,1.1,'Navigation output\nPosition + speed + uncertainty')
arrow((2.8,4.8),(4.2,4));arrow((2.8,3),(4.2,3.5));arrow((2.8,.95),(3.5,.95));arrow((5.1,1.6),(5.5,2.9));arrow((8.3,4.8),(7.4,4));arrow((7.4,3.4),(8.3,2.8))
save(fig,'07_slide4_fusion_architecture','Source: docs/live-eskf-and-training.md (October 4 update). Architecture capability, not proof of measured accuracy.\nBundled model remains validated=false; accepted AI corrections require validation and live calibration. Learned reliability/drift correction is future work.')
for p in [ROOT/'docs/live-eskf-and-training.md', ROOT/'artifacts/drift-fix/REPORT.md',SRC/'manifest.json']:sources.append(p)
manifest=[{'path':str(p.relative_to(ROOT)).replace('\\','/'),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in dict.fromkeys(sources)]
(OUT/'source_manifest.json').write_text(json.dumps(manifest,indent=2))
readme=f'''# OFFROUTE PPT evidence pack

Generated from local files on 5 October 2026. PNGs are 2932 × 1650 pixels; SVG/PDF copies are included. No original PPTX was found in the project-file inventory, so these are assets to insert into your existing deck.

## What to put on each slide

- **Slide 4:** `07_slide4_fusion_architecture.png`. Paste: “The live navigation architecture combines IMU propagation, accepted GNSS, physical constraints and road observations in a 15-error-state ESKF. A validated and calibrated neural speed model can supply uncertain velocity observations. The bundled model is not yet validated for active correction.”
- **Slide 4 health-monitor box:** “Monitor fix freshness, reported uncertainty and measurement consistency. Reject unreliable GNSS updates; continue inertial propagation only when alignment and sensor freshness permit. Reconcile on recovery.” Label any proposed C/N0 or RF-jamming detector **planned**. GNSS degradation does not identify its cause, and no RF-jamming experiment is provided here.
- **Slide 5:** Replace guaranteed lane accuracy, zero drift, jam-proof navigation or universal AI improvement with: “Offline navigation prototype with ESKF integration and on-device model inference. Drift and learned correction performance require further physical validation.” Learned reliability estimation, adaptive noise learned from data and learned position-drift correction should be presented as proposed extensions unless independently implemented and tested.
- **Slide 6 main evidence:** `02_real_reference_vs_estimate.png` and `01_real_outage_drift.png`. Paste: “Saved IO-VNBD regression replay: 15 injected 30-second outages across three Driver A recordings; {passes}/15 below 10% endpoint drift; median drift {median:.2f}%. AI speed correction inactive and no road matching. These historical replay results do not evaluate the October 4 live ESKF update.”
- **Slide 6 speed evidence / appendix:** `03_real_speed_model_comparison.png`. Paste: “Original speed model MAE {speed['test_mae_mps']:.2f} m/s and RMSE {speed['test_rmse_mps']:.2f} m/s; refined model MAE {refined_mae:.2f} m/s on reused Driver A regression data.” Keep model metrics separate from trajectory metrics.
- **Dataset appendix:** `04_real_dataset_split.png`.
- **Proposed evaluation appendix:** `05_SYNTHETIC_test_matrix.png`, `06_SYNTHETIC_paths_and_speed.png` and `synthetic_test_matrix.csv`. Keep the SYNTHETIC / ILLUSTRATIVE label visible. These complete the requested 4 × 3 layout, not the actual experiment.

## Real example table (closest to median drift among windows with at least 100 m travel)

Recording {name}; window near {start}s; {m['samples']} saved samples.

| Metric | Value |
|---|---:|
| Absolute position ATE RMSE, no alignment | {m['ate_rmse_m']:.2f} m |
| Start-aligned position RMSE | {m['start_aligned_rmse_m']:.2f} m |
| Relative endpoint error | {m['relative_endpoint_m']:.2f} m |
| Reference distance including outage-entry step | {m['reference_distance_m']:.2f} m |
| Relative endpoint drift | {m['drift_percent']:.2f}% |

## Definitions and provenance

Position ATE RMSE = sqrt(mean(||estimated position - reference position||²)), using a local planar projection (111320 m/degree latitude and cosine-scaled longitude). No rigid alignment is applied to the absolute metric. Start-aligned metrics independently translate both paths to their first outage point. Drift = 100 × start-aligned endpoint error / max(1 metre, reference path length). To match InertialRecordedEvaluationTest.java, distance includes the step from the preceding sample into the first outage sample; endpoint longitude uses the final reference latitude. MAE = mean absolute speed error; RMSE = sqrt(mean squared speed error). Convert m/s to km/h by multiplying by 3.6.

All 15 outage windows are used. Filters are seconds >= nominal start, seconds < start+30, outage=true, matching the existing reporting script. Rounded timestamp/coordinate export limits precision. Original data are real recordings; GNSS removal is injected in software. Vehicle GNSS is a reference receiver, not surveyed truth. Driver A has been reused. Missing receiver accuracy assumptions, source-axis ambiguity and repeated source fixes limit interpretation. The old QA prose says 0/15 and 77.5%; the current CSVs recompute to {passes}/15 and {median:.3f}%. The later drift-fix report documents the corrected gravity replay fixture and agrees on 1/15, but its 71.908% median is also stale; this pack recomputes the median from all 15 CSV rows. Do not treat this fixture change as a controlled algorithm improvement.

Model scores are read from saved training reports; training/inference was not rerun for this pack. The selected refined report has no overall speed RMSE, so none was invented. No measured four-configuration × 30/60/120-second × 60 km/h matched experiment was identified. The latest inspected laptop candidate report says training failed; no result from it is claimed.

## Synthetic data assumptions (fully reproducible, no random seed needed)

0.1-second timestep, 120 seconds, reference speed exactly 60/3.6 m/s; heading = 0.002t + 0.08 sin(t/18) radians. Position integrates reference velocity on the right endpoint. Last-speed surrogate holds initial heading and has an assumed +0.8 m/s speed error. ESKF surrogate assumes +0.028t + 0.12 sin(t/7) m/s speed error and +0.0009t rad yaw error. AI surrogate assumes +0.012t + 0.12 sin(t/7) m/s and +0.0004t rad yaw error. Map surrogate removes 80% of cross-track error using the exact synthetic reference normal; its speed is derived from corrected positions. This is an oracle constraint, not a real map matcher. Position and speed metrics include t=0 and the duration endpoint. The assumed errors predetermine apparent benefits. No ESKF implementation, learned reliability estimator, trained AI or actual road graph is evaluated. Use only as a proposed-test illustration.

## Rebuild

From the workspace: `.venv-ml/Scripts/python.exe artifacts/ppt-evidence-pack/build_assets.py` (numpy, pandas, matplotlib required). CSVs permit editing/replotting. source_manifest.json records SHA-256 hashes of the evidence used. Original sources are untouched. No cloud upload or publication was performed.
'''
(OUT/'README.md').write_text(readme,encoding='utf-8')
print(json.dumps({'real_windows':len(metrics),'passed':passes,'median_drift_percent':median,'example':m,'synthetic_rows':len(syn),'graphics':len(list(OUT.glob('*.png')))},indent=2))

