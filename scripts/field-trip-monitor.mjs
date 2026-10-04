// Read-only phone telemetry. Does not start/stop navigation or change device settings.
import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
const [serial,folder,secondsText='5']=process.argv.slice(2);
const seconds=Number(secondsText);
if(!serial||!folder||!Number.isFinite(seconds)||seconds<1||seconds>180)throw Error('Usage: node scripts/field-trip-monitor.mjs SERIAL OUTPUT_DIRECTORY SECONDS(1..180)');
const adb='C:/Users/surya/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const run=(...args)=>execFileSync(adb,['-s',serial,...args],{encoding:'utf8',timeout:10000});
fs.mkdirSync(folder,{recursive:true});
const stamp=new Date().toISOString().replaceAll(':','-');
const file=path.join(folder,stamp+'-telemetry.jsonl');
const save=value=>fs.appendFileSync(file,JSON.stringify({receivedAt:new Date().toISOString(),...value})+'\n');
const version=run('shell','dumpsys','package','com.idr.navigator').split('\n').filter(l=>/versionCode=|versionName=/.test(l)).map(l=>l.trim());
if(!version.some(l=>l.includes('versionCode=15 ')))throw Error('Expected installed Offroute version code 15');
const pid=run('shell','pidof','com.idr.navigator').trim();
if(!/^\d+$/.test(pid))throw Error('Open Offroute before capture');
const port=run('forward','tcp:0','localabstract:webview_devtools_remote_'+pid).trim();
let socket;let next=0;const pending=new Map();
try{
 const targets=await(await fetch('http://127.0.0.1:'+port+'/json/list',{signal:AbortSignal.timeout(10000)})).json();
 const target=targets.find(t=>t.url==='https://localhost/');if(!target)throw Error('Offroute web view unavailable');
 socket=new WebSocket(target.webSocketDebuggerUrl);
 await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('Phone connection timeout')),10000);socket.onopen=()=>{clearTimeout(timer);resolve();};socket.onerror=()=>{clearTimeout(timer);reject(Error('Phone connection error'));};});
 socket.onmessage=e=>{const m=JSON.parse(e.data);pending.get(m.id)?.(m);};
 function read(expression){return new Promise((resolve,reject)=>{const id=++next;const timer=setTimeout(()=>{pending.delete(id);reject(Error('Telemetry timeout; phone recording is independent'));},5000);pending.set(id,m=>{pending.delete(id);clearTimeout(timer);if(m.error||m.result?.exceptionDetails)reject(Error('Phone evaluation failed'));else resolve(m.result.result.value);});socket.send(JSON.stringify({id,method:'Runtime.evaluate',params:{expression,returnByValue:true,awaitPromise:true}}));});}
 save({kind:'metadata',serial,version,seconds});
 const state=await read(`(()=>{const s=JSON.parse(localStorage.getItem('idr-settings')||'{}');return {hidden:document.hidden,mount:s.mount,sampleRate:s.sampleRate,collectionEnabled:s.learningEnabled!==false,activeLog:JSON.parse(localStorage.getItem('idr-active-log')||'null'),routeActive:!!JSON.parse(localStorage.getItem('idr-active-route')||'null')?.live,journeyCount:JSON.parse(localStorage.getItem('idr-drives')||'[]').length};})()`);
 save({kind:'state',state});
 let count=0,imu=0,gps=0,logging=0,ai=0,firstInferences,lastInferences;
 const start=Date.now();
 while(Date.now()-start<seconds*1000){
  const p=await read("window.Capacitor.nativePromise('DrEngine','getPosition',{})");
  save({kind:'pose',pose:p});count++;imu+=Number(p.imuFresh===true);gps+=Number(p.positionFresh===true);logging+=Number(p.logging===true);ai+=Number(p.aiSpeedAvailable===true);firstInferences??=p.modelInferences;lastInferences=p.modelInferences;
  await new Promise(r=>setTimeout(r,500));
 }
 const summary={file,state,samples:count,imuFreshSamples:imu,gpsFreshSamples:gps,loggingSamples:logging,aiAvailableSamples:ai,inferenceCountDelta:lastInferences-firstInferences};
 save({kind:'summary',summary});console.log(JSON.stringify(summary,null,2));
}catch(e){save({kind:'error',message:String(e)});throw e;}
finally{socket?.close();try{run('forward','--remove','tcp:'+port);}catch{}}
