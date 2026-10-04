"""Convert the downloaded, attributed Bhopal OSM extract to offline display geometry.
No location data is invented. Also extract a real road polyline for the labeled simulation.
"""
import json, pathlib, xml.etree.ElementTree as ET, shutil
root=pathlib.Path(__file__).resolve().parent.parent
source=root/'android/app/src/main/assets/maps/bhopal.osm'
tree=ET.parse(source).getroot()
nodes={n.attrib['id']:[float(n.attrib['lon']),float(n.attrib['lat'])] for n in tree.findall('node')}
features=[]; roads=[]
for way in tree.findall('way'):
    tags={t.attrib['k']:t.attrib['v'] for t in way.findall('tag')}
    coords=[nodes[n.attrib['ref']] for n in way.findall('nd') if n.attrib['ref'] in nodes]
    if len(coords)<2: continue
    road=tags.get('highway'); closed=coords[0]==coords[-1] and len(coords)>3
    if road:
        kind='road'; typ=road; geom={'type':'LineString','coordinates':coords}
        if road in ('primary','secondary','tertiary') and len(coords)>8: roads.append(coords)
    elif closed and 'building' in tags: kind='building';typ='building';geom={'type':'Polygon','coordinates':[coords]}
    elif closed and (tags.get('natural')=='water' or 'landuse' in tags or tags.get('leisure') in ('park','garden')):
        kind='land';typ='water' if tags.get('natural')=='water' else 'park' if tags.get('leisure') in ('park','garden') or tags.get('landuse') in ('forest','grass','recreation_ground') else 'land';geom={'type':'Polygon','coordinates':[coords]}
    else: continue
    features.append({'type':'Feature','properties':{'kind':kind,'type':typ,'name':tags.get('name','')},'geometry':geom})
(root/'data/maps').mkdir(parents=True,exist_ok=True)
(root/'data/maps/bhopal.geojson').write_text(json.dumps({'type':'FeatureCollection','features':features},separators=(',',':')),encoding='utf8')
# Select a substantial central road; a back-and-forth trip keeps the simulation on real OSM geometry.
road=max(roads,key=lambda c:len(c)/(1+abs(c[0][0]-77.405)*100+abs(c[0][1]-23.243)*100))
(root/'public/maps/route.json').write_text(json.dumps(road+road[-2::-1]),encoding='utf8')
assets=root/'android/app/src/main/assets/maps';assets.mkdir(parents=True,exist_ok=True)
print(f'{len(features)} OSM features; {len(road)} points in simulation road; Android OSM asset copied.')
