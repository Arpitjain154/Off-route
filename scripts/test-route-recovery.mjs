import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import ts from 'typescript';

// Run the hook effects with stable hook storage; only the asynchronous native router is replaced.
function navigationHarness(){
 const slots=[],effects=[];let cursor=0,now=0,resolveRoute,requests=0,updates=0;
 const react={
  useRef(value){const i=cursor++;return slots[i]??=( {current:value} );},
  useState(value){const i=cursor++;if(!(i in slots))slots[i]=value;return [slots[i],next=>{slots[i]=typeof next==='function'?next(slots[i]):next;}];},
  useEffect(callback,deps){const i=cursor++,previous=slots[i];if(!previous||deps.some((value,j)=>value!==previous.deps[j]))effects.push(()=>{previous?.cleanup?.();slots[i]={deps,cleanup:callback()};});},
 };
 function compile(file,dependencies){
  const output=ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
  const mod={exports:{}};
  new Function('require','module','exports','Date',output)(name=>dependencies[name],mod,mod.exports,{now:()=>now});
  return mod.exports;
 }
 const progress=compile('src/routeProgress.ts',{});
 const {useRouteNavigation}=compile('src/useRouteNavigation.ts',{
  react,'./routeProgress':progress,
  './directions':{resolveEndpoint:()=>({id:'current'}),calculateRoute:()=>{requests++;return new Promise(resolve=>{resolveRoute=resolve;});}},
 });
 const route={live:true,mode:'car',coordinates:[[0,0],[0,.01]],destination:{id:'end'}};
 const onRoute=()=>{updates++;};
 return {
  render(pose,time){now=time;cursor=0;useRouteNavigation(pose,route,onRoute);while(effects.length)effects.shift()();},
  get requests(){return requests;},get updates(){return updates;},
  async resolve(){resolveRoute({...route});await new Promise(resolve=>setImmediate(resolve));},
  close(){for(const slot of slots)slot?.cleanup?.();},
 };
}
const pose={lat:0,lon:.001,ready:true,estimated:false,positionFresh:true,health:'HEALTHY',accuracy:5,gpsAccuracy:5,speedAvailable:true,fixTime:1};
for(const interrupted of [false,true])test(interrupted?'an outage invalidates a pending reroute even if GPS returns before the router responds':'a healthy pending reroute still applies normally',async()=>{
 const app=navigationHarness();
 try{
  app.render(pose,1000);app.render({...pose,fixTime:2},2100);app.render({...pose,fixTime:3},4100);
  assert.equal(app.requests,1);
  if(interrupted){
   app.render({...pose,estimated:true,positionFresh:false,health:'BLACKOUT'},5000);
   app.render({...pose,fixTime:4},6000);
  }
  await app.resolve();assert.equal(app.updates,interrupted?0:1);
  if(interrupted){
   for(let i=7;i<=17;i++){app.render({...pose,fixTime:i},i*1000);assert.equal(app.requests,1);}
   app.render({...pose,fixTime:18},18000);assert.equal(app.requests,2);
  }
 }finally{app.close();}
});
