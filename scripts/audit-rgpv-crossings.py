"""Read-only audit: geometric crossings are candidates, not proof of blocked access."""
import json
from pathlib import Path

d = json.loads(Path('artifacts/physical-tests/rgpv-full-osm-audit.json').read_text())
fences = [w for w in d['ways'] if w['tags'].get('barrier')]
def cross(a,b): return a[0]*b[1]-a[1]*b[0]
def sub(a,b): return [a[0]-b[0],a[1]-b[1]]
def intersection(a,b,c,e):
    r,s,q=sub(b,a),sub(e,c),sub(c,a)
    den=cross(r,s)
    if abs(den)<1e-16:return None
    t,u=cross(q,s)/den,cross(q,r)/den
    if 0<=t<=1 and 0<=u<=1:return [a[0]+t*r[0],a[1]+t*r[1]]
rows=[]
for road in d['ways']:
    if 'highway' not in road['tags']:continue
    for fence in fences:
        for a,b in zip(road['points'],road['points'][1:]):
            for c,e in zip(fence['points'],fence['points'][1:]):
                p=intersection(a,b,c,e)
                if p:rows.append(dict(road=road['id'],tags=road['tags'],point=p,fence=fence['id']))
Path('artifacts/physical-tests/rgpv-crossings.json').write_text(json.dumps(rows,indent=2))
print(json.dumps(rows,indent=2))
