// Read-only laptop capture. The app's normal navigation flow owns trip recording.
import fs from 'node:fs';
import path from 'node:path';
import {spawn,execFileSync} from 'node:child_process';
const [serial,output,secondsText='600']=process.argv.slice(2),seconds=Number(secondsText);
if(serial!=='RZCX71S8G8L'||!output||!Number.isFinite(seconds)||seconds<3||seconds>1200)throw Error('Use the confirmed wired serial, a new output directory and 3..1200 seconds');
const folder=path.resolve(output),marker=path.join(folder,'STOP');
if(fs.existsSync(folder))throw Error('Use a new directory: never overwrite an earlier capture');
fs.mkdirSync(folder,{recursive:true});
const adb='C:/Users/surya/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const run=(...args)=>execFileSync(adb,['-s',serial,...args],{encoding:'utf8',timeout:10000});
const events=path.join(folder,'capture-events.jsonl');
const event=data=>fs.appendFileSync(events,JSON.stringify({receivedAt:new Date().toISOString(),...data})+'\n');
const pid=run('shell','pidof','com.idr.navigator').trim();
if(!/^\d+$/.test(pid))throw Error('Open Offroute first');
for(const [name,args] of Object.entries({version:['shell','dumpsys','package','com.idr.navigator'],sensors:['shell','dumpsys','sensorservice'],space:['shell','df','-h','/data']})){
 const text=run(...args);fs.writeFileSync(path.join(folder,name+'.txt'),name==='version'?text.split('\n').filter(l=>/versionCode=|versionName=|ACCESS_FINE_LOCATION:/.test(l)).join('\n'):name==='sensors'?text.split('\n').filter(l=>/Accelerometer|Gyroscope|Gravity|Rotation Vector|sampling_period|com.idr.navigator/.test(l)).join('\n'):text);
}
const logfd=fs.openSync(path.join(folder,'app-logcat.txt'),'wx');
const log=spawn(adb,['-s',serial,'logcat','--pid='+pid,'-v','epoch','-T','1'],{stdio:['ignore',logfd,logfd],windowsHide:true});
const monitorfd=fs.openSync(path.join(folder,'monitor-output.txt'),'wx');
const deadline=Date.now()+seconds*1000;let monitor,stopped=false;
process.on('SIGINT',()=>{stopped=true;});process.on('SIGTERM',()=>{stopped=true;});
function startMonitor(){const remaining=Math.ceil((deadline-Date.now())/1000);if(remaining<1)return;monitor=spawn(process.execPath,['scripts/field-trip-monitor.mjs',serial,folder,String(Math.min(90,remaining))],{stdio:['ignore',monitorfd,monitorfd],windowsHide:true});monitor.on('error',e=>event({kind:'monitor-error',message:String(e)}));monitor.on('exit',code=>event({kind:'monitor-exit',code}));}
event({kind:'capture-start',serial,pid,limitSeconds:seconds,phoneRecordingControlled:false});
try{
 startMonitor();
 while(!stopped&&!fs.existsSync(marker)&&Date.now()<deadline){
  try{const start=Date.now();const mode=run('shell','settings','get','secure','location_mode').trim();const uptime=run('shell','cat','/proc/uptime').trim().split(' ')[0];event({kind:'provider-state',locationMode:mode,deviceUptimeSeconds:Number(uptime),roundTripMs:Date.now()-start});}catch(e){event({kind:'connection-error',message:String(e.message)});}
  if(monitor?.exitCode!==null&&Date.now()<deadline)startMonitor();
  await new Promise(r=>setTimeout(r,1500));
 }
}finally{
 const finish=async child=>{if(child&&child.exitCode===null){child.kill();await Promise.race([new Promise(r=>child.once('exit',r)),new Promise(r=>setTimeout(r,1500))]);}};
 await finish(monitor);await finish(log);fs.closeSync(logfd);fs.closeSync(monitorfd);
 event({kind:'capture-end',reason:fs.existsSync(marker)||stopped?'stop':'time-limit',phoneRecordingStopped:false});
 console.log(JSON.stringify({folder,finished:true,note:'Laptop capture ended; stop and save the journey through the app separately.'}));
}
