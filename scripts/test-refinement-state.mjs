import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import ts from 'typescript';
const load=async file=>{
 const {outputText}=ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}});
 return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
};
const {RerouteGate}=await load('src/routeProgress.ts');
const base={ready:true,health:'HEALTHY',positionFresh:true,speedAvailable:true,gpsAccuracy:5,accuracy:5,fixTime:1};
test('inertial estimates never change the route without reliable GNSS confirmation',()=>{
 const gate=new RerouteGate();
 for(let i=1;i<40;i++)assert.equal(gate.shouldReroute({...base,estimated:true,fixTime:i},90,i*1000),false);
});
test('invalid precision and degraded fixes never request a route',()=>{
 for(const extra of [{gpsAccuracy:NaN},{gpsAccuracy:-1},{accuracy:60},{health:'DEGRADING'},{gpsAge:10}]){
 const gate=new RerouteGate();for(let i=1;i<20;i++)assert.equal(gate.shouldReroute({...base,...extra,fixTime:i},80,i*1000),false);
 }
});
test('GPS recovery must establish several distinct reliable fixes before rerouting',()=>{
 const gate=new RerouteGate();
 for(let i=1;i<8;i++)assert.equal(gate.shouldReroute({...base,estimated:true,fixTime:i},80,i*1000),false);
 assert.equal(gate.shouldReroute({...base,fixTime:10},80,10000),false);
 assert.equal(gate.shouldReroute({...base,fixTime:11},80,11000),false);
 assert.equal(gate.shouldReroute({...base,fixTime:12},80,13000),true);
});
