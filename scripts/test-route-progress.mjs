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
