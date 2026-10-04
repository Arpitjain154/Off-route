import {chromium} from '@playwright/test';
const b=await chromium.launch({headless:true,channel:'msedge'});
const p=await b.newPage({viewport:{width:390,height:844}}),events=[];
p.on('console',m=>{if(['warning','error'].includes(m.type()))events.push(m.type()+': '+m.text());});p.on('pageerror',e=>events.push(e.message));p.on('response',r=>{if(r.url().includes('.pbf')||r.status()>=400)events.push(r.status()+' '+r.url());});
await p.addInitScript(()=>localStorage.setItem('idr-onboarded','true'));await p.goto('http://127.0.0.1:5173',{waitUntil:'domcontentloaded'});await p.waitForTimeout(18000);
console.log(JSON.stringify(events.slice(0,30),null,2));console.log(await p.locator('.maplibregl-canvas').boundingBox());await p.screenshot({path:'artifacts/refinement/screens/map-render-check.png'});await b.close();
