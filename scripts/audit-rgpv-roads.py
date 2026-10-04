import osmium,json,sys
from pathlib import Path
nodes={};ways=[];barriers=[]
class Audit(osmium.SimpleHandler):
 def node(self,n):
  if 23.298<n.location.lat<23.314 and 77.348<n.location.lon<77.366:
   nodes[n.id]=[n.location.lon,n.location.lat]
   tags=dict(n.tags)
   if tags.get('barrier') or tags.get('entrance'):barriers.append(dict(id=n.id,point=nodes[n.id],tags=tags))
 def way(self,w):
  points=[nodes[n.ref] for n in w.nodes if n.ref in nodes]
  if len(points)>1:ways.append(dict(id=w.id,tags=dict(w.tags),points=points))
full='--full' in sys.argv
Audit().apply_file('data/maps/central-zone-260911.osm.pbf' if full else 'android/app/src/main/assets/maps/madhya-pradesh.osm.pbf')
Path('artifacts/physical-tests/rgpv-'+('full-osm' if full else 'road')+'-audit.json').write_text(json.dumps(dict(ways=ways,barriers=barriers),indent=2))
print(json.dumps(dict(ways=len(ways),barriers=barriers,barrierWays=[w for w in ways if w['tags'].get('barrier')]),indent=2))
