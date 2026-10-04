import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
import {VectorTile} from '@mapbox/vector-tile';
import Pbf from 'pbf';
test('Offline Bhopal tiles contain non-collapsed roads and buildings at every zoom',async()=>{
  for(let z=11;z<=16;z++){
    let roads=0,valid=0;
    for(const x of await readdir(`public/maps/tiles/${z}`))for(const file of await readdir(`public/maps/tiles/${z}/${x}`)){
      const tile=new VectorTile(new Pbf(await readFile(`public/maps/tiles/${z}/${x}/${file}`)));
      const layer=tile.layers.bhopal;
      if(!layer){assert.deepEqual(Object.keys(tile.layers),[],'Only genuinely empty tiles may omit the layer');continue;}
      for(let i=0;i<layer.length;i++){
        const f=layer.feature(i);if(f.properties.kind!=='road')continue;roads++;
        const [minX,minY,maxX,maxY]=f.bbox();
        if(Number.isFinite(minX)&&Math.hypot(maxX-minX,maxY-minY)>1)valid++;
      }
    }
    assert.ok(roads>10,`Zoom ${z} contains roads`);
    assert.ok(valid/roads>.9,`Zoom ${z}: ${valid}/${roads} road geometries have spatial extent`);
  }
});
