import fs from 'node:fs';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {chromium} from '@playwright/test';
const adb='C:/Users/surya/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const run=(...a)=>execFileSync(adb,['-s','RZCX80NLSNN',...a],{encoding:'utf8',timeout:30000}).trim();
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const out={samples:[],errors:[],passed:false};let browser,original,changed=false;
const distance=(a,b)=>Math.hypot((a.lat-b.lat)*111320,(a.lon-b.lon)*111320*Math.cos(a.lat*Math.PI/180));
try {
 run('shell','input','keyevent','224');run('shell','wm','dismiss-keyguard');run('shell','am','start','-W','-n','com.idr.navigator/.MainActivity');
 const pid=run('shell','pidof','com.idr.navigator');run('forward','tcp:9223',`localabstract:webview_devtools_remote_${pid}`);
 for(let i=0;i<15&&!browser;i++){try{browser=await chromium.connectOverCDP('http://127.0.0.1:9223');}catch{await sleep(500);}}
 if(!browser)throw Error('WebView unavailable');
 const page=browser.contexts()[0].pages()[0];page.on('pageerror',e=>out.errors.push(e.message));await page.locator('.stitch-app').waitFor();
 const state=await page.evaluate(()=>Object.fromEntries(Object.keys(localStorage).filter(k=>k.startsWith('idr-')).map(k=>[k,localStorage.getItem(k)])));
 const before=JSON.parse(fs.readFileSync('recovery/drift-fix-before/phone-local-storage.json','utf8'));
 for(const [k,v] of Object.entries(before))assert.equal(state[k],v,`Preserve ${k}`);
 out.preferencesAndJourneysPreserved=true;
 await page.evaluate(()=>Capacitor.nativePromise('DrEngine','startEngine',{}));
 const pose=()=>page.evaluate(()=>Capacitor.nativePromise('DrEngine','getPosition',{}));
 if((await pose()).logging)throw Error('Active journey detected; GPS outage test skipped to preserve recording');
 original=run('shell','cmd','location','is-location-enabled');assert.ok(['true','false'].includes(original));
 if(original!=='true'){run('shell','cmd','location','set-location-enabled','true');changed=true;}
 const sample=async phase=>{const p=await pose();out.samples.push({phase,time:Date.now(),...p});return p;};
 for(let i=0;i<30;i++){await sample('gps');await sleep(1000);}
 const anchor=out.samples.at(-1);console.log(JSON.stringify({stage:'before outage',stationary:anchor.stationary,estimated:anchor.estimated,ready:anchor.ready,accuracy:anchor.accuracy,status:anchor.fusionStatus}));
 if(!anchor.ready||!anchor.positionFresh||!anchor.stationary){out.limitation='A fresh GPS-confirmed stop was unavailable; stationary outage validation requires another phone test.';}
 else{
  run('shell','cmd','location','set-location-enabled','false');changed=true;
  for(let i=0;i<40;i++){await sample('outage');await sleep(1000);}
  fs.writeFileSync('artifacts/drift-fix/phone-gps-off.png',execFileSync(adb,['-s','RZCX80NLSNN','exec-out','screencap','-p'],{maxBuffer:12*1024*1024}));
  const samples=out.samples.filter(p=>p.phase==='outage');out.outage={seconds:40,maxDisplacementMetres:Math.max(...samples.map(p=>distance(p,anchor))),maxSpeedKmh:Math.max(...samples.map(p=>p.speed)),initialUncertaintyMetres:anchor.accuracy,finalUncertaintyMetres:samples.at(-1).accuracy,estimatedSamples:samples.filter(p=>p.estimated).length,stationarySamples:samples.filter(p=>p.stationary).length};
  out.stationaryOutageVerified=out.outage.estimatedSamples>30&&out.outage.stationarySamples>30&&out.outage.maxDisplacementMetres<1&&out.outage.maxSpeedKmh<.1;
  if(!out.stationaryOutageVerified)out.limitation='Phone check did not validate aligned stationary INS; review samples before claiming a physical drift fix.';
  run('shell','cmd','location','set-location-enabled',original);changed=false;
  for(let i=0;i<12;i++){await sample('restored');await sleep(1000);}
 }
 assert.deepEqual(out.errors,[]);out.passed=true;
} catch(e){out.error=e.message;console.error(e.message);}
finally{
 if(changed&&original){try{run('shell','cmd','location','set-location-enabled',original);}catch(e){out.restoreError=e.message;out.passed=false;}}
 out.locationAfter=run('shell','cmd','location','is-location-enabled');out.originalLocation=original;
 fs.writeFileSync('artifacts/drift-fix/phone-results.json',JSON.stringify(out,null,2));if(browser)await browser.close();
}
console.log(JSON.stringify({...out,samples:out.samples.length}));if(!out.passed)process.exitCode=1;
