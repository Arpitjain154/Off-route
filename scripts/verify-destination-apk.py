import hashlib,json,re,sys,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
apk=root/sys.argv[1]
with zipfile.ZipFile(apk) as z:
    content=z.read('assets/public/maps/destination-places.json')
    assert content==(root/'public/maps/destination-places.json').read_bytes()
    places=json.loads(content)
    assert len(places)==45010
    for p in places:
        assert p['name'] and p['id'] and -90<=p['lat']<=90 and -180<=p['lon']<=180
    assert any(p['name']=='Bhopal' and p['type']=='city' for p in places)
    assert any(p['name']=='Indore' and p['type']=='city' for p in places)
    html=z.read('assets/public/index.html').decode()
    for asset in re.findall(r'(?:src|href)="(/assets/[^"]+)"',html):
        assert z.read('assets/public'+asset)==(root/'dist'/asset.lstrip('/')).read_bytes(),asset
report=dict(apk=apk.name,places=len(places),place_index_sha256=hashlib.sha256(content).hexdigest(),current_bundled_ui=True)
(root/'artifacts/destination-apk-verification.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
