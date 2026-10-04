import geojsonvt from 'geojson-vt';
import vtpbf from 'vt-pbf';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
const geo=JSON.parse(await readFile('data/maps/madhya-pradesh.geojson','utf8'));
const meta=await readFile('public/maps/mp-manifest.json','utf8').then(JSON.parse).catch(()=>({bounds:[74.029382,21.0706885,82.8126116,26.8695616]}));
const index=geojsonvt(geo,{maxZoom:13,indexMaxZoom:8,indexMaxPoints:50000,tolerance:3,extent:4096});
const tx=(lon,z)=>Math.floor((lon+180)/360*2**z);
const ty=(lat,z)=>Math.floor((1-Math.asinh(Math.tan(lat*Math.PI/180))/Math.PI)/2*2**z);
let count=0,bytes=0;
for(let z=5;z<=13;z++) {
 for(let x=tx(meta.bounds[0],z);x<=tx(meta.bounds[2],z);x++) {
  let made=false;
  for(let y=ty(meta.bounds[3],z);y<=ty(meta.bounds[1],z);y++) {
   const tile=index.getTile(z,x,y) || {features:[]};
   if(!made){await mkdir(`public/maps/mp-tiles/${z}/${x}`,{recursive:true});made=true;}
   const data=vtpbf.fromGeojsonVt({bhopal:tile},{version:2});
   await writeFile(`public/maps/mp-tiles/${z}/${x}/${y}.pbf`,data);count++;bytes+=data.length;
  }
 }
 console.log('zoom',z,'tiles',count,'MB',(bytes/1e6).toFixed(1));
}
meta.tiles=count;meta.tile_bytes=bytes;
await writeFile('public/maps/mp-tiles-stats.json',JSON.stringify({tiles:count,tile_bytes:bytes,minzoom:5,maxzoom:13},null,2));
