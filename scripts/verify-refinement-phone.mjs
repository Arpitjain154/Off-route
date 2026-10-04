import fs from 'node:fs';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {chromium} from '@playwright/test';
const adb='C:/Users/surya/AppData/Local/Android/Sdk/platform-tools/adb.exe',serial='RZCX80NLSNN';
const run=(...args)=>execFileSync(adb,['-s',serial,...args],{encoding:'utf8',timeout:30000}).trim();
run('shell','input','keyevent','224');run('shell','wm','dismiss-keyguard');run('shell','am','start','-W','-n','com.idr.navigator/.MainActivity');
const pid=run('shell','pidof','com.idr.navigator');run('forward','tcp:9223',`localabstract:webview_devtools_remote_${pid}`);
let browser;for(let attempt=0;attempt<20&&!browser;attempt++){try{browser=await chromium.connectOverCDP('http://127.0.0.1:9223');}catch{await new Promise(r=>setTimeout(r,500));}}if(!browser)throw Error('Phone WebView not available');const page=browser.contexts()[0].pages()[0];
const result={device:serial,checks:[],limitations:[],started:new Date().toISOString()},errors=[];page.on('pageerror',e=>errors.push(e.message));
const pose=()=>page.evaluate(()=>Capacitor.nativePromise('DrEngine','getPosition',{}));
const shot=async name=>{const b=execFileSync(adb,['-s',serial,'exec-out','screencap','-p'],{timeout:30000,maxBuffer:12*1024*1024});fs.writeFileSync('artifacts/refinement/screens/phone-'+name+'.png',b);};
let originalSettings,originalRoute;
try{
 await page.locator('.stitch-app').waitFor({timeout:60000});
 await page.waitForFunction(()=>!document.body.innerText.includes('Connecting to sensors'),{timeout:30000});
 originalSettings=await page.evaluate(()=>localStorage.getItem('idr-settings'));originalRoute=await page.evaluate(()=>localStorage.getItem('idr-active-route'));
 const before=await pose();result.initialPose=before;
 const saved=JSON.parse(fs.readFileSync('recovery/phone-local-storage-before.json','utf8'));const preserved=await page.evaluate(()=>JSON.parse(localStorage.getItem('idr-settings')||'{}'));const old=JSON.parse(saved['idr-settings']||'{}');for(const key of Object.keys(old))assert.deepEqual(preserved[key],old[key],'Preference retained: '+key);result.checks.push('Original preferences and backend configuration survived in-place update');
 await page.getByRole('button',{name:'Open settings',exact:true}).filter({visible:true}).click();await page.getByRole('button',{name:'Light',exact:true}).click();await page.getByRole('button',{name:'Save Preferences',exact:true}).click();
 await page.getByRole('button',{name:'Expand position details'}).click();if(await page.getByRole('button',{name:'Dismiss notification'}).count())await page.getByRole('button',{name:'Dismiss notification'}).click();await page.waitForTimeout(1500);await shot('dashboard');
 assert.equal(await page.locator('.maplibregl-canvas').count(),1);assert.equal(await page.getByText('A new journey awaits.',{exact:true}).count(),0);
 for(const tab of ['Journeys','Sensors','Navigate']){await page.getByRole('button',{name:tab,exact:true}).click();if(tab!=='Navigate')await shot(tab.toLowerCase());}
 await page.getByRole('button',{name:'Open settings',exact:true}).filter({visible:true}).click();await shot('settings');await page.getByRole('button',{name:'Close settings',exact:true}).click();
 result.checks.push('Dashboard, expandable sheet, Journeys, Sensors, and Settings render on physical phone with real state');
 await page.getByRole('button',{name:'Search offline destination',exact:true}).click();
 const from=page.getByRole('combobox',{name:'Starting point'}),to=page.getByRole('combobox',{name:'Destination',exact:true});
 async function choose(field,name){await field.fill(name);await page.getByRole('option').filter({has:page.getByText(name,{exact:true})}).first().click();}
 await choose(to,'Indore');await choose(from,'Bhopal');await page.getByRole('button',{name:'Swap starting point and destination'}).click();assert.equal(await from.inputValue(),'Indore');await page.getByRole('button',{name:'Swap starting point and destination'}).click();await shot('planner');
 await page.getByRole('button',{name:'Get directions',exact:true}).click();await page.locator('dialog.destination-dialog').waitFor({state:'detached',timeout:90000});await page.locator('.destination-pin').first().waitFor();await shot('route-preview');
 result.checks.push('Bundled offline index selection and swap produce a real native Bhopal–Indore route');
 for(let i=0;i<3;i++)for(const tab of ['Journeys','Sensors','Navigate'])await page.getByRole('button',{name:tab,exact:true}).click();
 const after=await pose();assert.equal(after.engineGeneration,before.engineGeneration);assert.equal(after.locationRegistrations,1);assert.equal(run('shell','pidof','com.idr.navigator'),pid);
 result.checks.push('Three tab cycles retain one sensor service, one location registration, and the map');
 if(after.ready&&after.positionFresh&&after.accuracy<=25&&after.lat>=21&&after.lat<=27&&after.lon>=74&&after.lon<=83&&!originalRoute){
  await page.getByRole('button',{name:'Edit destination',exact:true}).click();await from.focus();await page.locator('.destination-current').click();await choose(to,'Bhopal');await page.getByRole('button',{name:'Get directions',exact:true}).click();
  await page.locator('dialog.destination-dialog').waitFor({state:'detached',timeout:90000});await page.getByRole('region',{name:'Turn by turn guidance'}).waitFor();
  await page.waitForFunction(async()=> (await Capacitor.nativePromise('DrEngine','getPosition',{})).logging,{timeout:15000});await page.waitForTimeout(1000);await shot('active-navigation');
  await page.getByRole('button',{name:'Exit navigation',exact:true}).click();await page.waitForFunction(async()=> !(await Capacitor.nativePromise('DrEngine','getPosition',{})).logging);
  const drives=await page.evaluate(()=>JSON.parse(localStorage.getItem('idr-drives')||'[]'));assert.ok(drives[0].localPath);assert.equal(drives[0].route,'mixed');
  await page.evaluate(()=>{const d=JSON.parse(localStorage.getItem('idr-drives')||'[]');d[0].title='Verification - Bhopal';d[0].notes='Stationary UI verification; not a driving accuracy test.';localStorage.setItem('idr-drives',JSON.stringify(d));});
  result.checks.push('Live native route starts local recording automatically and Exit saves one journey');
 }else result.limitations.push('Live navigation/automatic recording not exercised: a precise local GPS fix was unavailable or a prior route needed preservation');
 run('shell','input','keyevent','3');await new Promise(r=>setTimeout(r,1200));run('shell','am','start','-W','-n','com.idr.navigator/.MainActivity');await page.waitForFunction(()=>document.visibilityState==='visible');const resumed=await pose();assert.equal(resumed.locationRegistrations,1);assert.equal(resumed.engineGeneration,before.engineGeneration);result.checks.push('Background and foreground return preserve one engine instance');
 assert.deepEqual(errors,[]);result.passed=true;
}catch(e){result.passed=false;result.error=e.stack;await shot('failure');throw e;}
finally{if(originalSettings){await page.evaluate(value=>localStorage.setItem('idr-settings',value),originalSettings);await page.reload({waitUntil:'domcontentloaded'}).catch(()=>{});}result.errors=errors;result.finished=new Date().toISOString();fs.writeFileSync('artifacts/refinement/phone-results.json',JSON.stringify(result,null,2));await browser.close();}
console.log(JSON.stringify(result,null,2));
