"""Check the actual judge APK contains the current UI, maps and trained models."""
import hashlib
import json
from pathlib import Path
import zipfile
import sys

root = Path(__file__).resolve().parents[1]
apk = root / (sys.argv[1] if len(sys.argv)>1 else 'artifacts/IDR-Navigator-debug.apk')
with zipfile.ZipFile(apk) as archive:
    names = archive.namelist()
    tiles = [name for name in names if name.startswith('assets/public/maps/mp-tiles/') and name.endswith('.pbf')]
    assert len(tiles) == 39285, len(tiles)
    assert archive.read('assets/public/index.html') == (root / 'dist/index.html').read_bytes()
    manifest = json.loads(archive.read('assets/models/manifest.json'))
    assert manifest['dataset'] == 'IO-VNBD'
    for model in manifest['models']:
        assert not model['placeholder'] and not model['validated']
        payload = archive.read('assets/models/' + model['name'] + '.tflite')
        assert hashlib.sha256(payload).hexdigest() == model['sha256']
    graph = [name for name in names if name.startswith('assets/maps/mp-vehicle-v1/') and not name.endswith('/')]
    assert len(graph) >= 7
    assert any('maplibre-gl-worker-' in name for name in names)
    assert archive.getinfo('assets/maps/madhya-pradesh.osm.pbf').file_size == 50184156
report = dict(apk=str(apk), bytes=apk.stat().st_size, sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), statewide_tiles=len(tiles), graph_files=len(graph), models=3, current_ui=True)
(root / ('artifacts/'+apk.stem+'-verification.json')).write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
