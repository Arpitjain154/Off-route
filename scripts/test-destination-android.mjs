import fs from 'node:fs';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
const serial=process.argv[2];if(!serial)throw new Error('Specify device serial');
const adb='C:/Users/surya/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const run=(...args)=>execFileSync(adb,['-s',serial,...args],{encoding:'utf8',timeout:20000}).trim();
run('shell','input','keyevent','224');
run('shell','wm','dismiss-keyguard');
const pid=run('shell','pidof','com.idr.navigator');
run('forward','tcp:9223',`localabstract:webview_devtools_remote_${pid}`);
let target;
for(let retry=0;retry<40&&!target;retry++){
 try{const targets=await(await fetch('http://localhost:9223/json/list')).json();target=targets.find(t=>t.type==='page'&&t.title==='Off Route');}catch{}
 if(!target)await new Promise(resolve=>setTimeout(resolve,500));
}
assert.ok(target,'App WebView became available');
const socket=new WebSocket(target.webSocketDebuggerUrl);await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject;});
let sequence=0;const pending=new Map();
socket.onmessage=e=>{const r=JSON.parse(e.data),p=pending.get(r.id);if(p){clearTimeout(p.timer);pending.delete(r.id);r.error?p.reject(new Error(JSON.stringify(r.error))):p.resolve(r.result);}};
async function evaluate(expression){const id=++sequence;const r=await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(new Error('WebView timeout')),20000);pending.set(id,{resolve,reject,timer});socket.send(JSON.stringify({id,method:'Runtime.evaluate',params:{expression,awaitPromise:true,returnByValue:true}}));});if(r.exceptionDetails)throw new Error(JSON.stringify(r.exceptionDetails));return r.result.value;}
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function until(expression,timeout=30000){const deadline=Date.now()+timeout;while(Date.now()<deadline){if(await evaluate(expression))return;await sleep(200);}throw new Error('Condition not met: '+expression);}
async function click(selector){await evaluate(`document.querySelector(${JSON.stringify(selector)}).click()`);await sleep(150);}
async function focus(selector){await evaluate(`document.querySelector(${JSON.stringify(selector)}).focus()`);await sleep(150);}
async function fill(index,text){await evaluate(`(()=>{const e=document.querySelectorAll('.destination-field input')[${index}];e.focus();Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,${JSON.stringify(text)});e.dispatchEvent(new Event('input',{bubbles:true}));})()`);await sleep(250);}
async function choose(name){await until(`Array.from(document.querySelectorAll('.destination-result strong')).some(e=>e.textContent===${JSON.stringify(name)})`);await evaluate(`Array.from(document.querySelectorAll('.destination-result')).find(e=>e.querySelector('strong').textContent===${JSON.stringify(name)}).click()`);await sleep(150);}
const values=()=>evaluate("Array.from(document.querySelectorAll('.destination-field input')).map(e=>e.value)");
const pose=()=>evaluate("Capacitor.nativePromise('DrEngine','getPosition',{})");
const shot=name=>{run('shell','screencap','-p','/sdcard/idr-search-test.png');run('pull','/sdcard/idr-search-test.png',`artifacts/${name}.png`);};
const results={device:serial,pid,checks:[],started:new Date().toISOString()};
const pass=message=>{results.checks.push(message);console.log('PASS '+message);};
const wake=setInterval(()=>run('shell','input','keyevent','224'),20000);
try {
 await until("document.visibilityState==='visible'&&!!document.querySelector('.destination-launcher')");const before=await pose();
 await evaluate(`(()=>{window.__routeEvidence=[];const original=Capacitor.nativePromise;Capacitor.nativePromise=function(plugin,method,options){const promise=original.apply(this,arguments);if(plugin==='Directions')promise.then(r=>window.__routeEvidence.push({options,points:r.coordinates.length,distance:r.distance,duration:r.duration}));return promise;};})()`);
 assert.equal(await evaluate("document.body.innerText.includes('GO BEYOND THE SIGNAL')"),false);shot('destination-home-android');
 await click('.destination-launcher');assert.deepEqual(await values(),['Your current location','']);pass('Search opens with current GPS starting point and empty destination');
 await fill(1,'Indore');await choose('Indore');await fill(0,'Bhopal');await choose('Bhopal');
 assert.deepEqual(await values(),['Bhopal','Indore']);pass('Both fields autocomplete against the bundled OSM index');
 await click('.destination-swap');assert.deepEqual(await values(),['Indore','Bhopal']);await click('.destination-swap');
 pass('Swap exchanges both selected coordinates and text');shot('destination-search-android');
 await click('.destination-footer .primary');await until("!document.querySelector('dialog')",60000);
 await until(`(()=>{const map=document.querySelector('.maplibregl-map').getBoundingClientRect();const pins=[...document.querySelectorAll('.destination-pin')];return pins.length===2&&pins.every(p=>{const r=p.getBoundingClientRect();return r.left>=map.left&&r.right<=map.right&&r.top>=map.top&&r.bottom<=map.bottom;});})()`);
 const routes=await evaluate('window.__routeEvidence');assert.ok(routes[0].points>20);assert.ok(routes[0].distance>150000&&routes[0].distance<300000);
 assert.equal(routes[0].options.fromLat,23.2584857);assert.equal(routes[0].options.toLat,22.7203616);
 pass('Confirm returns a real Bhopal–Indore driving route and renders route endpoints');await sleep(1200);shot('destination-route-android');
 await click('.destination-launcher');await fill(1,'zzzznotaplacezzzz');await until("document.body.innerText.includes('No places found')");
 assert.equal(await evaluate("document.querySelector('.destination-footer .primary').disabled"),true);
 await click('[aria-label="Close destination search"]');await until("!document.querySelector('dialog')");
 assert.equal(await evaluate("document.querySelectorAll('.destination-pin').length"),2);pass('Cancel discards edits and preserves the confirmed route');
 await click('.destination-launcher');await focus('.destination-field input');await click('.destination-current');await fill(1,'Bhopal');await choose('Bhopal');
 assert.deepEqual(await values(),['Your current location','Bhopal']);
 const fresh=await pose();assert.ok(fresh.ready);await click('.destination-footer .primary');await until("!document.querySelector('dialog')",60000);
 const currentRoute=await evaluate('window.__routeEvidence.at(-1)');assert.ok(Math.abs(currentRoute.options.fromLat-fresh.lat)<.001);assert.ok(Math.abs(currentRoute.options.fromLon-fresh.lon)<.001);assert.equal(currentRoute.options.toLat,23.2584857);
 pass('Current-location confirmation uses live GPS coordinates');await sleep(1000);shot('destination-current-route-android');
 const after=await pose();assert.equal(after.engineGeneration,before.engineGeneration);assert.equal(after.locationRegistrations,1);assert.equal(run('shell','pidof','com.idr.navigator'),pid);
 assert.equal(await evaluate("document.querySelectorAll('.mobile-nav button').length"),3);assert.ok(await evaluate("document.body.innerText.includes('Your drive')||document.body.innerText.includes('YOUR DRIVE')"));
 pass('Search and route calculations preserve the existing GPS service, one listener, map and drive panels');
 for(let cycle=0;cycle<3;cycle++)for(const index of [1,2,0]){await evaluate(`document.querySelectorAll('.mobile-nav button')[${index}].click()`);await sleep(250);}
 await until("document.querySelectorAll('.destination-pin').length===2");
 run('shell','input','keyevent','3');await sleep(2000);run('shell','am','start','-W','-n','com.idr.navigator/.MainActivity');
 await until("document.visibilityState==='visible'&&!!document.querySelector('.destination-launcher')");
 const resumed=await pose();assert.equal(resumed.engineGeneration,before.engineGeneration);assert.equal(resumed.locationRegistrations,1);assert.equal(run('shell','pidof','com.idr.navigator'),pid);
 pass('Three tab cycles and a background return retain the route and one GPS listener');
 results.routes=await evaluate('window.__routeEvidence');results.passed=true;
}catch(error){results.passed=false;results.error=error.stack;shot('destination-android-failure');throw error;}
finally{clearInterval(wake);results.finished=new Date().toISOString();fs.writeFileSync('artifacts/destination-android-tests.json',JSON.stringify(results,null,2));socket.close();}
