import fs from 'node:fs';
import {execFileSync} from 'node:child_process';
import {chromium} from '@playwright/test';
const adb='C:/Users/surya/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const run=(...args)=>execFileSync(adb,['-s','RZCX80NLSNN',...args],{encoding:'utf8',timeout:30000}).trim();
run('shell','input','keyevent','224');run('shell','am','start','-W','-n','com.idr.navigator/.MainActivity');
const pid=run('shell','pidof','com.idr.navigator');run('forward','tcp:9223',`localabstract:webview_devtools_remote_${pid}`);
let browser;for(let i=0;i<15&&!browser;i++){try{browser=await chromium.connectOverCDP('http://127.0.0.1:9223');}catch{await new Promise(r=>setTimeout(r,500));}}
if(!browser)throw Error('WebView unavailable');
try{const page=browser.contexts()[0].pages()[0];await page.waitForLoadState('domcontentloaded');const state=await page.evaluate(()=>Object.fromEntries(Object.keys(localStorage).filter(k=>k.startsWith('idr-')).map(k=>[k,localStorage.getItem(k)])));fs.writeFileSync('recovery/phone-local-storage-before.json',JSON.stringify(state,null,2));console.log('Saved preference/history recovery snapshot; keys:',Object.keys(state));}finally{await browser.close();}
