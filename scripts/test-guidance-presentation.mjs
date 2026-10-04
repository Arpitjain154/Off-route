import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
const root=process.cwd();
const require=createRequire(path.join(root,'package.json'));
const ts=require('typescript'), React=require('react'),{renderToStaticMarkup}=require('react-dom/server');
function compile(filename){
 const output=ts.transpileModule(fs.readFileSync(filename,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,jsx:ts.JsxEmit.ReactJSX,esModuleInterop:true}}).outputText;
 const mod={exports:{}};
 new Function('require','module','exports',output)((name)=>name.endsWith('.css')?{}:name==='./routeProgress'?compile(path.join(root,'src/routeProgress.ts')):require(name),mod,mod.exports);
 return mod.exports;
}
const {default:Guidance,NavigationSummary}=compile(process.env.GUIDANCE_FILE||path.join(root,'src/RouteGuidance.tsx'));
const route={coordinates:[[77.35,23.3],[77.35,23.301],[77.351,23.301]],distance:213.55,duration:40,start:{id:'current',name:'Current position'},destination:{id:'end',name:'Destination'},mode:'car',live:true,steps:[{text:'Continue north',distance:111.32,duration:20},{text:'Turn right',distance:102.23,duration:20},{text:'Arrive',distance:0,duration:0}]};
const pose={lat:23.301,lon:77.351,ready:true,positionFresh:true,headingReady:true,heading:0,health:'HEALTHY',accuracy:5,gpsAccuracy:5,gpsAge:0,source:'device',trackingAvailable:true,speedAvailable:true,speed:0};
const render=(p,extra={})=>renderToStaticMarkup(React.createElement(Guidance,{route,pose:p,units:'km',...extra}));
test('missing, stale and uncertain poses pause precise maneuvers and never announce arrival',()=>{
 for(const sample of [{...pose,ready:false},{...pose,positionFresh:false},{...pose,estimated:true,accuracy:51},{...pose,accuracy:NaN},{...pose,health:'BLACKOUT'},{...pose,gpsAge:12}]){
  const html=render(sample);assert.match(html,/Guidance paused/);assert.doesNotMatch(html,/You have arrived/);
 }
});
test('fresh precise accepted GPS can announce arrival',()=>assert.match(render(pose),/You have arrived/));
test('usable sensor estimate can show maneuver but cannot announce arrival',()=>{
 const html=render({...pose,estimated:true,health:'BLACKOUT',positionFresh:false,accuracy:12,imuFresh:true,estimateAge:10});
 assert.doesNotMatch(html,/You have arrived/);assert.doesNotMatch(html,/Guidance paused/);assert.match(html,/IMU TRACKING/);
});
test('summary suppresses ETA and progress when guidance is paused',()=>{
 assert.equal(typeof NavigationSummary,'function');
 const html=renderToStaticMarkup(React.createElement(NavigationSummary,{route,pose:{...pose,estimated:true,accuracy:500},units:'km'}));
 assert.match(html,/Waiting for a reliable position/);assert.doesNotMatch(html,/ETA/);
});

test('navigation source changes automatically and never represents advisor trust as fusion proportions',()=>{
 const {navigationTelemetry}=compile(path.join(root,'src/RouteGuidance.tsx'));
 const base={...pose,confidence:.8,gpsTrust:.75,physicsTrust:.25,imuFresh:true,estimated:false};
 assert.equal(navigationTelemetry(base).mode,'GPS TRACKING');
 assert.equal(navigationTelemetry({...base,estimated:true,health:'BLACKOUT',positionFresh:false}).mode,'GPS LOST · IMU TRACKING');
 assert.equal(navigationTelemetry({...base,estimated:true,health:'BLACKOUT',imuFresh:false}).mode,'POSITION UNAVAILABLE');
 assert.equal(navigationTelemetry({...base,health:'BLACKOUT',positionFresh:false}).mode,'GPS LOST · POSITION HELD');
 assert.equal(navigationTelemetry({...base,gpsTrust:NaN}).gpsTrust,null);
 const html=renderToStaticMarkup(React.createElement(NavigationSummary,{route,pose:base}));
 assert.match(html,/GPS trust/);assert.match(html,/IMU ready/);assert.doesNotMatch(html,/IMU 25%/);
});
