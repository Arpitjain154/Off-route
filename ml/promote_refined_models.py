"""Package validation-improved research models; never enable learned fusion without device validation."""
from pathlib import Path
import json,hashlib
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'artifacts/ml/refinement';ASSETS=ROOT/'android/app/src/main/assets/models'
comparison=json.loads((OUT/'validation-comparison.json').read_text());report=json.loads((OUT/'report.json').read_text())
manifest=json.loads((ASSETS/'manifest.json').read_text());promoted=[]
for m in manifest['models']:
    name=m['name'];c=comparison[name]
    better=c['candidate']['mae_mps']<c['shipped']['mae_mps'] if name=='speed' else c['candidate']['balanced_accuracy']>c['shipped']['balanced_accuracy']
    if not better: continue
    blob=(OUT/(name+'.tflite')).read_bytes();checksum=hashlib.sha256(blob).hexdigest()
    assert checksum==report['models'][name]['sha256']
    (ASSETS/(name+'.tflite')).write_bytes(blob)
    m.update(version='1.1.0',sha256=checksum,placeholder=False,validated=False)
    promoted.append(name)
manifest['version']='1.1.0'
for p in [ASSETS/'manifest.json',ROOT/'src/model-manifest.json']:p.write_text(json.dumps(manifest,indent=2))
(OUT/'promotion.json').write_text(json.dumps({'bundled':promoted,'fusion_enabled':False,'selection':'Improved validation metrics only; regression set did not select checkpoints.'},indent=2))
print('Bundled research models:',promoted)
