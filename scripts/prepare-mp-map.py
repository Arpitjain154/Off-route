"""Build attributed statewide map geometry and routing PBF from Geofabrik OSM."""
import json,hashlib
from pathlib import Path
import osmium
from shapely.geometry import shape, mapping, LineString
from shapely.prepared import prep

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'data/maps';SOURCE=DATA/'central-zone-260911.osm.pbf'
expected=(DATA/'central-zone-260911.osm.pbf.md5').read_text().split()[0]
assert hashlib.md5(SOURCE.read_bytes()).hexdigest()==expected, 'Source checksum mismatch'
factory=osmium.geom.GeoJSONFactory()
class Boundary(osmium.SimpleHandler):
    polygon=None
    def area(self,a):
        if a.tags.get('boundary')=='administrative' and a.tags.get('admin_level')=='4' and a.tags.get('name')=='Madhya Pradesh':
            self.polygon=shape(json.loads(factory.create_multipolygon(a)))
            print('Madhya Pradesh boundary found',self.polygon.bounds,flush=True)
b=Boundary();b.apply_file(str(DATA/'mp-boundary.osm'),locations=True,idx='flex_mem')
assert b.polygon is not None, 'State boundary missing'
(DATA/'madhya-pradesh-boundary.geojson').write_text(json.dumps(mapping(b.polygon)))
boundary=prep(b.polygon); features=[];places=[]; ways=set();nodes=set()
car={'motorway','motorway_link','trunk','trunk_link','primary','primary_link','secondary','secondary_link','tertiary','tertiary_link','unclassified','residential','living_street','service','road','track'}
class Geometry(osmium.SimpleHandler):
    def node(self,n):
        if n.tags.get('place') in ('city','town'):
            from shapely.geometry import Point
            if boundary.covers(Point(n.location.lon,n.location.lat)):
                places.append({'name':n.tags.get('name:en',n.tags.get('name','')),'lat':n.location.lat,'lon':n.location.lon,'type':n.tags.get('place')})
    def way(self,w):
        road=w.tags.get('highway');water=w.tags.get('natural')=='water' or w.tags.get('waterway')=='riverbank'
        park=w.tags.get('leisure') in ('park','garden') or w.tags.get('landuse')=='forest'
        if not road and not water and not park:return
        try:
            coords=[[n.lon,n.lat] for n in w.nodes];line=LineString(coords)
            if not boundary.intersects(line):return
            closed=len(coords)>3 and coords[0]==coords[-1]
            if not road and not closed:return
            kind='road' if road else 'land';typ=road or ('water' if water else 'park')
            features.append({'type':'Feature','properties':{'kind':kind,'type':typ,'name':w.tags.get('name:en',w.tags.get('name',''))},'geometry':{'type':'LineString' if road else 'Polygon','coordinates':coords if road else [coords]}})
            if road in car:
                ways.add(w.id);nodes.update(n.ref for n in w.nodes)
        except (osmium.InvalidLocationError,ValueError):pass
g=Geometry();g.apply_file(str(SOURCE),locations=True,idx='flex_mem')
print('Geometry',len(features),'routing ways',len(ways),'nodes',len(nodes),flush=True)
(DATA/'madhya-pradesh.geojson').write_text(json.dumps({'type':'FeatureCollection','features':features},separators=(',',':')),encoding='utf8')
assets=ROOT/'android/app/src/main/assets/maps';assets.mkdir(parents=True,exist_ok=True)
pbf=assets/'madhya-pradesh.osm.pbf'
with osmium.SimpleWriter(str(pbf),overwrite=True) as writer:
    class Routing(osmium.SimpleHandler):
        def node(self,n):
            if n.id in nodes:writer.add_node(n)
        def way(self,w):
            if w.id in ways:writer.add_way(w)
        def relation(self,r):
            if r.tags.get('type')=='restriction' and all(m.ref in (ways if m.type=='w' else nodes) for m in r.members):writer.add_relation(r)
    Routing().apply_file(str(SOURCE))
public=ROOT/'public/maps';public.mkdir(exist_ok=True)
(public/'mp-places.json').write_text(json.dumps(places,ensure_ascii=False),encoding='utf8')
meta={'name':'Madhya Pradesh','bounds':list(b.polygon.bounds),'minzoom':5,'maxzoom':13,'features':len(features),'routing_ways':len(ways),'source_url':'https://download.geofabrik.de/asia/india/central-zone-260911.osm.pbf','source_md5':expected,'source_sha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),'license':'ODbL 1.0; OpenStreetMap contributors','detail':'Statewide roads, water and parks; central Bhopal includes additional buildings. Roads crossing state boundary retain complete ways.'}
(public/'mp-manifest.json').write_text(json.dumps(meta,indent=2))
print(json.dumps(meta,indent=2),flush=True)
