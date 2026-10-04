"""Offline search index from the same verified OSM extract as the bundled map."""
import hashlib, json
from pathlib import Path
import osmium
from shapely.geometry import Point, shape
from shapely.prepared import prep

root = Path(__file__).resolve().parents[1]
data = root / 'data/maps'
source = data / 'central-zone-260911.osm.pbf'
assert hashlib.md5(source.read_bytes()).hexdigest() == (data / 'central-zone-260911.osm.pbf.md5').read_text().split()[0]
boundary = prep(shape(json.loads((data / 'madhya-pradesh-boundary.geojson').read_text())))
places = []
class SearchPlaces(osmium.SimpleHandler):
    def add(self, kind, ident, tags, lat, lon):
        name = tags.get('name:en') or tags.get('name')
        category = next((tags.get(k) for k in ('place','amenity','tourism','leisure','shop','railway','aeroway') if tags.get(k)), None)
        if not name or not category or not boundary.covers(Point(lon, lat)): return
        address = ', '.join(dict.fromkeys(tags.get(k) for k in ('addr:housenumber','addr:street','addr:suburb','addr:city','addr:district') if tags.get(k)))
        places.append(dict(id=f'{kind}{ident}', name=name, aliases=' '.join(dict.fromkeys(tags.get(k) for k in ('name','name:hi','alt_name','short_name') if tags.get(k))), lat=round(lat,7), lon=round(lon,7), type=category.replace('_',' '), address=address or 'Madhya Pradesh'))
    def node(self, n):
        if n.location.valid(): self.add('n', n.id, n.tags, n.location.lat, n.location.lon)
    def way(self, w):
        if not (w.tags.get('name') or w.tags.get('name:en')): return
        if not any(w.tags.get(k) for k in ('place','amenity','tourism','leisure','shop','railway','aeroway')): return
        try:
            coords = [(n.lat,n.lon) for n in w.nodes]
            if coords: self.add('w', w.id, w.tags, sum(p[0] for p in coords)/len(coords),sum(p[1] for p in coords)/len(coords))
        except osmium.InvalidLocationError: pass
SearchPlaces().apply_file(str(source), locations=True, idx='flex_mem')
places.sort(key=lambda p: p['name'].casefold())
out = root / 'public/maps/destination-places.json'
out.write_text(json.dumps(places, ensure_ascii=False, separators=(',',':')), encoding='utf8')
print(json.dumps(dict(places=len(places), bytes=out.stat().st_size, source=source.name, sha256=hashlib.sha256(source.read_bytes()).hexdigest())))
