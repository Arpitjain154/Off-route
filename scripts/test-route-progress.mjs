import {test} from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import ts from 'typescript';
const {outputText}=ts.transpileModule(fs.readFileSync('src/routeProgress.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}});
const {projectOnRoute,RerouteGate,routeGuidance}=await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
const pose={ready:true,positionFresh:true,health:'HEALTHY',gpsAccuracy:5,accuracy:5,speedAvailable:true,stationary:false,fixTime:1};
test('route progress projects onto the current road instead of extending a trail',()=>{
 const p=projectOnRoute({lat:23.3,lon:77.3505},[[77.35,23.3],[77.351,23.3],[77.351,23.301]]);
 assert.ok(p.distance<.01);assert.equal(p.index,0);assert.ok(Math.abs(p.point[0]-77.3505)<1e-8);
});
test('off-route requires sustained distinct reliable fixes with retry cooldown',()=>{
 const gate=new RerouteGate();assert.equal(gate.shouldReroute(pose,70,1000),false);
 assert.equal(gate.shouldReroute(pose,70,4000),false);
 assert.equal(gate.shouldReroute({...pose,fixTime:2},70,4100),false);
 assert.equal(gate.shouldReroute({...pose,fixTime:3},70,4200),true);
 for(let i=4;i<8;i++)assert.equal(gate.shouldReroute({...pose,fixTime:i},70,5000+i*100),false);
});
test('GPS noise, missing signals and a stationary phone do not trigger rerouting',()=>{
 for(const sample of [pose,{...pose,positionFresh:false},{...pose,gpsAccuracy:60},{...pose,stationary:true}]){
  const gate=new RerouteGate();for(let i=1;i<20;i++)assert.equal(gate.shouldReroute({...sample,fixTime:i},sample===pose?8:100,i*1000),false);
 }
});
test('remaining distance, next turn and travel time advance with position',()=>{
 const route={coordinates:[[77.35,23.3],[77.35,23.301],[77.351,23.301]],distance:213.55,duration:40,steps:[{text:'Continue north',distance:111.32,duration:20},{text:'Turn right',distance:102.23,duration:20},{text:'Arrive',distance:0,duration:0}]};
 const beginning=routeGuidance(route,{lat:23.3,lon:77.35}),middle=routeGuidance(route,{lat:23.3005,lon:77.35});
 assert.equal(beginning.instruction,'Turn right');assert.equal(beginning.nextInstruction,'Arrive');assert.equal(middle.instruction,'Turn right');
 assert.ok(middle.remaining<beginning.remaining-50);assert.ok(Math.abs(middle.seconds-30)<.1);assert.ok(Math.abs(middle.toTurn-55.66)<.1);
 const finish=routeGuidance(route,{lat:23.301,lon:77.351});assert.ok(finish.remaining<.1);assert.ok(finish.seconds<.1);
});
test('sensor estimates retain the route until reliable GNSS can confirm deviation',()=>{
 const gate=new RerouteGate(),sample={...pose,positionFresh:false,health:'BLACKOUT',estimated:true,accuracy:12};
 assert.equal(gate.shouldReroute(sample,70,1000),false);assert.equal(gate.shouldReroute(sample,70,1100),false);
 assert.equal(gate.shouldReroute(sample,70,2000),false);assert.equal(gate.shouldReroute(sample,70,4000),false);
 const uncertain=new RerouteGate();for(let i=1;i<20;i++)assert.equal(uncertain.shouldReroute({...sample,accuracy:50},100,i*1000),false);
});


test('post-outage rerouting requires over 40 metres for more than 10 seconds',()=>{
 const gate=new RerouteGate();
 assert.equal(gate.shouldReroute({...pose,estimated:true,positionFresh:false,health:'BLACKOUT'},80,1000),false);
 for(let i=2;i<=12;i++)assert.equal(gate.shouldReroute({...pose,fixTime:i},41,i*1000),false);
 assert.equal(gate.shouldReroute({...pose,fixTime:13},41,13000),true);
 const boundary=new RerouteGate();boundary.shouldReroute({...pose,estimated:true},80,1000);
 for(let i=2;i<30;i++)assert.equal(boundary.shouldReroute({...pose,fixTime:i},40,i*1000),false);
});

test('recovery deviation timer resets when evidence is interrupted',()=>{
 for(const extra of [{positionFresh:false},{gpsAccuracy:60},{health:'DEGRADING'},{stationary:true}]){
  const gate=new RerouteGate();gate.shouldReroute({...pose,estimated:true},80,1000);
  for(let i=2;i<10;i++)assert.equal(gate.shouldReroute({...pose,fixTime:i},80,i*1000),false);
  assert.equal(gate.shouldReroute({...pose,...extra,fixTime:10},80,10000),false);
  for(let i=11;i<=21;i++)assert.equal(gate.shouldReroute({...pose,fixTime:i},80,i*1000),false);
  assert.equal(gate.shouldReroute({...pose,fixTime:22},80,22000),true);
 }
 const gap=new RerouteGate();gap.shouldReroute({...pose,estimated:true},80,1000);
 for(let i=2;i<=9;i++)assert.equal(gap.shouldReroute({...pose,fixTime:i},80,i*1000),false);
 assert.equal(gap.shouldReroute({...pose,fixTime:30},80,30000),false,'a gap in observed GPS cannot count as continuous deviation');
});

test('recovery requires new fixes and reliable rejoin before restoring healthy rerouting',()=>{
 const gate=new RerouteGate();gate.shouldReroute({...pose,health:'BLACKOUT',positionFresh:false},80,1000);
 gate.shouldReroute({...pose,fixTime:2},80,2000);
 for(let i=3;i<20;i++)assert.equal(gate.shouldReroute({...pose,fixTime:2},80,i*1000),false);
 // A full period on the existing route ends recovery; ordinary healthy rerouting resumes.
 for(let i=20;i<=31;i++)assert.equal(gate.shouldReroute({...pose,fixTime:i},0,i*1000),false);
 assert.equal(gate.shouldReroute({...pose,fixTime:32},30,32000),false);
 assert.equal(gate.shouldReroute({...pose,fixTime:33},30,33000),false);
 assert.equal(gate.shouldReroute({...pose,fixTime:35},30,35000),true);
});

test('healthy-only journey keeps the existing reroute decisions',()=>{
 const gate=new RerouteGate();
 const fixes=[
  [1,1000,30,false],[1,4000,30,false],[2,4100,30,false],[3,4200,30,true],
  [4,5000,0,false],[5,6000,30,false],[6,8000,30,false],[7,9000,30,false],
  [8,14200,30,true],
 ];
 for(const [fixTime,now,distance,want] of fixes)assert.equal(gate.shouldReroute({...pose,fixTime},distance,now),want);
});
