"""Report all replay windows, without converting numeric stability into an accuracy pass."""
from pathlib import Path
import json
import numpy as np
import pandas as pd
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'artifacts/qa-1.5/iovnbd'
summary=pd.read_csv(OUT/'drift-summary.csv')
summary['passes_10_percent']=summary.drift_percent<10
report={'criterion':'Endpoint relative drift <10% of reference distance in every replay window',
        'accuracy_pass':bool(summary.passes_10_percent.all()),'passed_windows':int(summary.passes_10_percent.sum()),
        'total_windows':len(summary),'median_drift_percent':float(summary.drift_percent.median()),
        'maximum_drift_percent':float(summary.drift_percent.max()),'windows':summary.to_dict(orient='records'),
        'limitations':json.loads((OUT/'manifest.json').read_text())['limitations']+[
            'No UK road graph loaded: these replays evaluate the inertial estimator, not map-constrained performance.',
            'Relative endpoint error removes the initial absolute GNSS offset. Reference is vehicle GNSS, not surveyed truth.',
            'Sensor uncertainty is a process-model estimate, not empirically calibrated coverage.',
            'Dataset replay and emulator simulation do not establish real Samsung tunnel performance.']}
(OUT/'accuracy-gate.json').write_text(json.dumps(report,indent=2))

fig,axes=plt.subplots(3,5,figsize=(18,11),layout='constrained')
for row_index,name in enumerate(['S-S1','S-S2','S-S3a']):
    data=pd.read_csv(OUT/f'{name}-positions.csv')
    for col,start in enumerate([60,180,300,420,540]):
        d=data[(data.seconds>=start)&(data.seconds<start+30)&data.outage]
        ax=axes[row_index,col]
        scale=111320*np.cos(np.deg2rad(d.reference_lat.iloc[0]))
        refx=(d.reference_lon-d.reference_lon.iloc[0])*scale;refy=(d.reference_lat-d.reference_lat.iloc[0])*111320
        estx=(d.estimated_lon-d.estimated_lon.iloc[0])*scale;esty=(d.estimated_lat-d.estimated_lat.iloc[0])*111320
        ax.plot(refx,refy,color='#167b3e',label='Vehicle GNSS reference');ax.plot(estx,esty,color='#d45028',label='Unaided estimate')
        ax.scatter([0],[0],color='black',s=12);ax.grid(alpha=.25);ax.set_aspect('equal',adjustable='datalim')
        result=summary[(summary.recording==name+'.csv')&(abs(summary.outage_start_s-start)<1)].iloc[0]
        ax.set_title(f'{name} · {start}–{start+30}s\nDrift {result.drift_percent:.1f}% · {result.relative_endpoint_drift_m:.1f}m',fontsize=10)
        ax.set_xlabel('East displacement (m)',fontsize=8);ax.set_ylabel('North displacement (m)',fontsize=8)
handles,labels=axes[0,0].get_legend_handles_labels();fig.legend(handles,labels,loc='outside lower center',ncol=2)
fig.suptitle('IO-VNBD: all 15 simulated outages — accuracy gate FAILED\nPaths aligned at outage start; no road matching; reused Driver A regression recordings',fontsize=15)
fig.savefig(OUT/'outage-trajectories.png',dpi=150);fig.savefig(OUT/'outage-trajectories.svg');plt.close(fig)

candidate_path=ROOT/'artifacts/qa-1.5/speed-candidate/report.json'
candidate=json.loads(candidate_path.read_text())
decision={'promoted':False,'reason':'Candidate worsens overall validation MAE and moving-speed error.',
          'shipped_validation_mae_mps':5.1087,'candidate_validation_mae_mps':candidate['validation']['mae_mps'],
          'candidate_sha256':candidate['sha256'],'models_in_apk':'Existing bundled models retained; no device-validation claim.'}
(candidate_path.parent/'promotion-decision.json').write_text(json.dumps(decision,indent=2))
print(json.dumps({k:v for k,v in report.items() if k not in ['windows','limitations']},indent=2))
