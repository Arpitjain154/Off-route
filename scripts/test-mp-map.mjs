import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {VectorTile} from '@mapbox/vector-tile';
import Pbf from 'pbf';
test('State map includes roads in six Madhya Pradesh cities at every supported zoom',async()=>{
 const cities=[['Bhopal',77.4126,23.2599],['Indore',75.8577,22.7196],['Jabalpur',79.9864,23.1815],['Gwalior',78.1828,26.2183],['Rewa',81.3037,24.5362],['Ujjain',75.7885,23.1765]];
 for(const [name,lon,lat]of cities)for(let z=5;z<=13;z++){
  const x=Math.floor((lon+180)/360*2**z),y=Math.floor((1-Math.asinh(Math.tan(lat*Math.PI/180))/Math.PI)/2*2**z);
  const layer=new VectorTile(new Pbf(await readFile(`public/maps/mp-tiles/${z}/${x}/${y}.pbf`))).layers.bhopal;
  assert.ok(layer,`${name} at zoom ${z}`);
  let roads=0;
  for(let i=0;i<layer.length;i++) {const f=layer.feature(i);if(f.properties.kind==='road'){const[a,b,c,d]=f.bbox();if(Math.hypot(c-a,d-b)>1)roads++;}}
  assert.ok(roads>0,`Road geometry for ${name} at zoom ${z}`);
 }
});
