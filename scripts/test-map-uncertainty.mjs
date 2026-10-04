import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
const root=process.cwd(),require=createRequire(path.join(root,'package.json')),ts=require('typescript');
const file=process.env.MAP_FILE||path.join(root,'src/MapView.tsx');
const output=ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,jsx:ts.JsxEmit.ReactJSX,esModuleInterop:true}}).outputText;
globalThis.window={location:{origin:'http://localhost'}};
const mod={exports:{}};
new Function('require','module','exports',output)(name=>name==='maplibre-gl'?{setWorkerUrl(){}}:name.endsWith('.css')||name.startsWith('./')||name.includes('worker')?{}:name==='react-map-gl/maplibre'?{}:require(name),mod,mod.exports);
const {positionAccuracyFeature}=mod.exports;
test('accuracy area uses geographic metres and grows with reported uncertainty',()=>{
 assert.equal(typeof positionAccuracyFeature,'function');
 const small=positionAccuracyFeature({lat:23.3,lon:77.35,accuracy:10,ready:true}),large=positionAccuracyFeature({lat:23.3,lon:77.35,accuracy:100,ready:true});
 assert.equal(small.features.length,1);const a=small.features[0].geometry.coordinates[0],b=large.features[0].geometry.coordinates[0];
 assert.deepEqual(a[0],a.at(-1));assert.ok(Math.abs((b[0][1]-23.3)/(a[0][1]-23.3)-10)<1e-4);
 assert.equal(large.features[0].properties.radiusMeters,100);
});
test('missing, nonfinite and negative uncertainty never becomes a measured halo',()=>{
 for(const sample of [{ready:false,accuracy:20},{ready:true,accuracy:NaN},{ready:true,accuracy:Infinity},{ready:true,accuracy:-1},{ready:true,accuracy:0}])assert.equal(positionAccuracyFeature({lat:23.3,lon:77.35,...sample}).features.length,0);
});
