import fs from 'node:fs';import assert from 'node:assert/strict';import {execFileSync} from 'node:child_process';import {chromium} from '@playwright/test';
const adb='C:/Users/surya/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const run=(...a)=>execFileSync(adb,['-s','RZCX80NLSNN',...a],{encoding:'utf8',timeout:30000}).trim();
run('shell','input','keyevent','224');run('shell','wm','dismiss-keyguard');run('shell','am','start','-W','-n','com.idr.navigator/.MainActivity');
const pid=run('shell','pidof','com.idr.navigator');run('forward','tcp:9223',`localabstract:webview_devtools_remote_${pid}`);
const browser=await chromium.connectOverCDP('http://127.0.0.1:9223');const page=browser.contexts()[0].pages()[0];const errors=[];page.on('pageerror',e=>errors.push(e.message));let prefs;
const out={checks:[],passed:false};
try{
 await page.locator('.stitch-app').waitFor();prefs=await page.evaluate(()=>localStorage.getItem('idr-settings'));
 const pre=await page.evaluate(()=>JSON.parse(localStorage.getItem('idr-active-route')||'null'));
 if(!pre){
  await page.getByRole('button',{name:'Open settings',exact:true}).filter({visible:true}).click();await page.getByRole('button',{name:'Light',exact:true}).click();await page.getByRole('button',{name:'Save Preferences',exact:true}).click();
  await page.waitForFunction(async()=>{const p=await Capacitor.nativePromise('DrEngine','getPosition',{});return p.ready&&p.positionFresh&&p.accuracy<=25;},{timeout:20000});
  await page.getByRole('button',{name:/Search offline destination|Edit destination/,exact:true}).click();
  await page.getByRole('combobox',{name:'Starting point',exact:true}).focus();await page.locator('.destination-current').click();
  await page.getByRole('combobox',{name:'Destination',exact:true}).fill('Bhopal');await page.getByRole('option').filter({has:page.getByText('Bhopal',{exact:true})}).first().click();await page.getByRole('button',{name:'Get directions',exact:true}).click();
 }
 await page.locator('.stitch-guidance').waitFor({timeout:60000});assert.equal(await page.locator('.stitch-nav').count(),0);
 await page.getByRole('button',{name:'Show IMU details'}).click();await page.getByText('Inertial tracking',{exact:true}).waitFor();await page.getByRole('button',{name:'Show GPS details'}).click();await page.getByText('GPS signal',{exact:true}).waitFor();await page.getByRole('button',{name:'Show GPS details'}).click();
 await page.getByRole('button',{name:'Switch to north up'}).click();await page.getByRole('button',{name:'Follow travel direction'}).click();await page.getByRole('button',{name:'Recenter map',exact:true}).click();
 await page.waitForTimeout(1200);fs.writeFileSync('artifacts/navigation-ui/phone-navigation.png',execFileSync(adb,['-s','RZCX80NLSNN','exec-out','screencap','-p'],{maxBuffer:12*1024*1024}));
 await page.getByRole('button',{name:'Navigation sensor details'}).click();await page.getByRole('button',{name:'Navigate',exact:true}).click();
 out.checks.push('Real native route renders focused navigation','GPS/IMU detail taps','Heading-up / north-up / recenter controls','Sensor page and return');
 if(!pre){await page.getByRole('button',{name:'Exit navigation',exact:true}).click();await page.getByRole('button',{name:'Search offline destination',exact:true}).waitFor();out.checks.push('Exit restores dashboard');}
 assert.deepEqual(errors,[]);out.passed=true;
}catch(e){out.error=e.message;console.log(e.message);}
finally{if(prefs){await page.evaluate(v=>localStorage.setItem('idr-settings',v),prefs);await page.reload();}out.errors=errors;fs.writeFileSync('artifacts/navigation-ui/phone-results.json',JSON.stringify(out,null,2));await browser.close();}
console.log(JSON.stringify(out));if(!out.passed)process.exitCode=1;
