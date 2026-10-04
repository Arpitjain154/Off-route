import fs from 'node:fs';import assert from 'node:assert/strict';import {execFileSync} from 'node:child_process';
const serial=process.argv[2],seconds=Number(process.argv[3]||300);
assert.ok(serial&&!serial.startsWith('emulator-'));
const adb='C:/Users/surya/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const run=(...args)=>execFileSync(adb,['-s',serial,...args],{encoding:'utf8',timeout:20000}).trim();
const pid=run('shell','pidof','com.idr.navigator');run('forward','tcp:9223',`localabstract:webview_devtools_remote_${pid}`);
const target=(await(await fetch('http://localhost:9223/json/list')).json()).find(t=>t.type==='page'&&t.title==='Off Route');
const ws=new WebSocket(target.webSocketDebuggerUrl);await new Promise((resolve,reject)=>{ws.onopen=resolve;ws.onerror=reject;});
let sequence=0;const pending=new Map();ws.onmessage=e=>{const r=JSON.parse(e.data),p=pending.get(r.id);if(p){pending.delete(r.id);clearTimeout(p.timer);p.resolve(r);}};
async function evaluate(expression){const id=++sequence;const r=await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(new Error('WebView timeout')),15000);pending.set(id,{resolve,reject,timer});ws.send(JSON.stringify({id,method:'Runtime.evaluate',params:{expression,awaitPromise:true,returnByValue:true}}));});if(r.error||r.result.exceptionDetails)throw new Error(JSON.stringify(r));return r.result.result.value;}
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const pose=()=>evaluate("Capacitor.nativePromise('DrEngine','getPosition',{})");
const output='artifacts/physical-tests/phone-stationary-and-outage.json';
const report={device:serial,pid,started:new Date().toISOString(),injectedLocations:false,stationaryConfirmedByUser:true,samples:[],checks:[]};
const savedLocation=run('shell','cmd','location','is-location-enabled');let toggled=false;
const wake=setInterval(()=>run('shell','input','keyevent','224'),20000);
function save(){fs.writeFileSync(output,JSON.stringify(report,null,2));}
try {
 run('shell','input','keyevent','224');run('shell','wm','dismiss-keyguard');
 await evaluate("document.querySelector('.mobile-nav button[aria-label=\"Navigate\"]')?.click()");
 let healthySince=0;const start=Date.now();let generation;let nextLifecycle=60000;
 while(Date.now()-start<seconds*1000){
  const p=await pose();generation??=p.engineGeneration;
  assert.equal(run('shell','pidof','com.idr.navigator'),pid);assert.equal(p.engineGeneration,generation);assert.equal(p.locationRegistrations,1);
  assert.ok(Object.hasOwn(p,'speedAvailable'),'Wrong APK: navigation availability fields missing');
  if(p.positionFresh&&p.speedAvailable){healthySince ||= Date.now();if(Date.now()-healthySince>20000)assert.ok(p.speed<.05,`Stationary phone speed ${p.speed} m/s`);}
  const sample={seconds:Math.round((Date.now()-start)/1000),...p};report.samples.push(sample);save();
  if(Date.now()-start>=nextLifecycle){
   for(const tab of ['My drives','Settings','Navigate']){
    await evaluate(`document.querySelector('.mobile-nav button[aria-label="${tab}"]')?.click()`);await sleep(400);
   }
   run('shell','input','keyevent','3');await sleep(1500);run('shell','am','start','-n','com.idr.navigator/.MainActivity');await sleep(500);
   nextLifecycle+=60000;
  }
  if(report.samples.length%15===0)console.log(JSON.stringify({seconds:sample.seconds,speed:p.speed,available:p.speedAvailable,fresh:p.positionFresh,stationary:p.stationary}));
  await sleep(2000);
 }
 assert.ok(report.samples.filter(p=>p.positionFresh&&p.speedAvailable).length>seconds/4,'Not enough real fresh GPS observations');
 report.checks.push('Five-minute stationary real-GPS monitoring: zero speed after settling, same process and one GPS listener');
 if(savedLocation==='true') {
  toggled=true;run('shell','cmd','location','set-location-enabled','false');await sleep(3000);
  const lost=await pose();assert.equal(lost.positionFresh,false);assert.equal(lost.speedAvailable,false);
  assert.equal(await evaluate("document.querySelector('.hud-speed>span').textContent"),'—');
  await sleep(6000);const held=await pose();assert.equal(held.lat,lost.lat);assert.equal(held.lon,lost.lon);
  report.outage={lost,held};report.checks.push('Android location disabled: speed unavailable and last known position held');
  run('shell','cmd','location','set-location-enabled','true');toggled=false;
  const deadline=Date.now()+90000;let restored;
  do{await sleep(2000);restored=await pose();}while(!restored.positionFresh&&Date.now()<deadline);
  assert.equal(restored.positionFresh,true);assert.equal(restored.locationRegistrations,1);assert.equal(restored.engineGeneration,generation);report.recovery=restored;
  report.checks.push('Android location restored: fresh real GPS returns without another listener');
 }
 report.passed=true;console.log('PASS '+report.checks.join('; '));
}catch(error){report.passed=false;report.error=error.stack;throw error;}
finally{if(toggled)run('shell','cmd','location','set-location-enabled',savedLocation);clearInterval(wake);report.finished=new Date().toISOString();save();ws.close();}
