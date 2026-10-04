import fs from 'node:fs';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {chromium} from '@playwright/test';

const adb='C:/Users/surya/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const serial='RZCX80NLSNN';
const mode=process.argv[2]||'inspect';
const dir='artifacts/eskf-phone';
fs.mkdirSync(dir,{recursive:true});
const run=(...args)=>execFileSync(adb,['-s',serial,...args],{encoding:'utf8',timeout:30000}).trim();
run('shell','am','start','-W','-n','com.idr.navigator/.MainActivity');
const pid=run('shell','pidof','com.idr.navigator');
run('forward','tcp:9223',`localabstract:webview_devtools_remote_${pid}`);
let browser;
for(let i=0;i<15&&!browser;i++){
  try{browser=await chromium.connectOverCDP('http://127.0.0.1:9223');}
  catch{await new Promise(r=>setTimeout(r,500));}
}
if(!browser)throw Error('Phone WebView unavailable');
try{
  const page=browser.contexts()[0].pages()[0];
  await page.waitForLoadState('domcontentloaded');
  const native=(plugin,method,args={})=>page.evaluate(({plugin,method,args})=>window.Capacitor.nativePromise(plugin,method,args),{plugin,method,args});
  await native('DrEngine','startEngine');
  if(mode==='record')await page.waitForFunction(async()=>{try{return (await window.Capacitor.nativePromise('DrEngine','getPosition',{})).ready;}catch{return false;}},{},{timeout:30000});
  const pose=await native('DrEngine','getPosition');
  const settings=await page.evaluate(()=>Object.fromEntries(Object.keys(localStorage).filter(k=>k.startsWith('idr-')).map(k=>[k,localStorage.getItem(k)])));
  const fingerprints=Object.fromEntries(Object.entries(settings).map(([k,v])=>[k,crypto.createHash('sha256').update(v).digest('hex')]));
  fs.writeFileSync(`${dir}/${mode}-state.json`,JSON.stringify({pose,fingerprints},null,2));
  console.log(JSON.stringify({mode,pid,ready:pose.ready,logging:pose.logging,positionFresh:pose.positionFresh,accuracy:pose.accuracy,aiFusionActive:pose.aiFusionActive,keys:Object.keys(fingerprints)}));
  const cloud=await native('CloudPlugin','getStatus');
  const testId=fs.existsSync(`${dir}/test-session.json`)?JSON.parse(fs.readFileSync(`${dir}/test-session.json`)).sessionId:null;
  const cloudSummary={connected:cloud.connected,consent:cloud.consent,wifiOnly:cloud.wifiOnly,queue:(cloud.queue||[]).filter(q=>q.sessionId===testId)};
  fs.writeFileSync(`${dir}/${mode}-cloud.json`,JSON.stringify(cloudSummary,null,2));
  console.log(JSON.stringify({cloud:cloudSummary}));
  if(mode==='record'&&fs.existsSync(`${dir}/before-state.json`)){
    const before=JSON.parse(fs.readFileSync(`${dir}/before-state.json`));
    for(const key of ['idr-settings','idr-drives'])if(before.fingerprints[key]!==fingerprints[key])throw Error('Saved state changed: '+key);
    console.log('Existing preferences and journey history preserved');
  }
  if(mode==='before'&&pose.logging)throw Error('Existing recording is active; preserve it before updating');
  if(mode==='collect'){
    const {sessionId}=JSON.parse(fs.readFileSync(`${dir}/test-session.json`));
    const local=`${dir}/${sessionId}`;fs.mkdirSync(local,{recursive:true});
    for(const suffix of ['.drlog','.fusion.csv','.quality.csv','.training.json','.summary.json','.sensors.csv','.gnss.csv']){
      const data=execFileSync(adb,['-s',serial,'exec-out','run-as','com.idr.navigator','cat',`files/drives/${sessionId}${suffix}`],{timeout:30000,maxBuffer:32*1024*1024});
      fs.writeFileSync(`${local}/${sessionId}${suffix}`,data);
      console.log(JSON.stringify({file:sessionId+suffix,bytes:data.length,sha256:crypto.createHash('sha256').update(data).digest('hex')}));
    }
  }
  if(mode==='record'){
    if(pose.logging)throw Error('Existing recording is active; test will not interrupt it');
    if(!pose.ready)throw Error('An initial real GPS fix is required for recording');
    const sessionId=crypto.randomUUID();
    fs.writeFileSync(`${dir}/test-session.json`,JSON.stringify({sessionId,purpose:'Stationary physical recording verification; not a driving accuracy test'},null,2));
    let started=false;
    try{
      await native('SensorPlugin','startLogging',{sessionId,mount:'dashboard',sampleRate:100});started=true;
      console.log('Recording real sensors for 30 seconds: '+sessionId);
      await new Promise(r=>setTimeout(r,30000));
    }finally{
      if(started){const result=await native('SensorPlugin','stopLogging');fs.writeFileSync(`${dir}/recording-result.json`,JSON.stringify(result,null,2));console.log(JSON.stringify(result));}
    }
  }
}finally{await browser.close();}
