from pathlib import Path
import hashlib,json,zipfile
import numpy as np
import pandas as pd
from PIL import Image, ImageOps, ImageDraw

p=Path(__file__).resolve().parent
root=p.parents[1]
r=pd.read_csv(p/'real_outage_metrics.csv')
s=pd.read_csv(root/'artifacts/qa-1.5/iovnbd/drift-summary.csv')
assert len(r)==15 and (r.drift_percent<10).sum()==1
assert np.allclose(r.drift_percent,s.drift_percent,atol=.02)
assert np.isfinite(r.select_dtypes('number')).all().all()
syn=pd.read_csv(p/'synthetic_test_matrix.csv')
assert len(syn)==12 and syn.evidence.str.contains('SYNTHETIC').all()
for j in range(1,5):
    d=pd.read_csv(p/f'synthetic_variant_{j}.csv')
    for dur in [30,60,120]:
        w=d[d.seconds<=dur]; m=syn[(syn.configuration==syn.configuration.unique()[j-1])&(syn.outage_s==dur)].iloc[0]
        assert np.isclose(m.position_rmse_m,np.sqrt(np.mean((w.estimated_east_m-w.reference_east_m)**2+(w.estimated_north_m-w.reference_north_m)**2)))
        assert np.isclose(m.speed_rmse_mps,np.sqrt(np.mean((w.estimated_speed_mps-w.reference_speed_mps)**2)))
for item in json.loads((p/'source_manifest.json').read_text()):
    assert hashlib.sha256((root/item['path']).read_bytes()).hexdigest()==item['sha256']
imgs=sorted(p.glob('[0-9][0-9]_*.png'))
assert len(imgs)==7
sheet=Image.new('RGB',(1400,4*420),'#dae0e8')
draw=ImageDraw.Draw(sheet)
for i,f in enumerate(imgs):
    with Image.open(f) as im:
        im.verify()
    with Image.open(f) as im:
        im=ImageOps.contain(im.convert('RGB'),(690,388))
        sheet.paste(im,((i%2)*700,(i//2)*420))
        draw.text(((i%2)*700+8,(i//2)*420+391),f.stem,fill='black')
sheet.save(root/'tmp/ppt-evidence-contact-sheet.jpg')
with zipfile.ZipFile(p.parent/'OFFROUTE-PPT-Evidence-Pack.zip','w',zipfile.ZIP_DEFLATED) as z:
    for f in p.iterdir():
        if f.is_file():z.write(f,'OFFROUTE-PPT-Evidence-Pack/'+f.name)
print('Verified: 15 real outage rows, 12 synthetic cases, source hashes, 7 PNGs; ZIP created.')
