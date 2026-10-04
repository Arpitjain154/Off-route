import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const browser=await chromium.launch({headless:true,channel:'msedge'});
try {
 const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{localStorage.setItem('idr-onboarded','true');window.__navigationFixture={lat:23.30845,lon:77.3568,heading:0,headingReady:true,speed:9.2,speedAvailable:true,accuracy:7,gpsAccuracy:7,gpsAge:0,confidence:.88,gpsTrust:.75,physicsTrust:.8,mapTrust:.5,aiTrust:0,ready:true,positionFresh:true,trackingAvailable:true,imuFresh:true,estimated:false,health:'HEALTHY',source:'device',distance:0,elapsed:0,bytes:0,satellites:17,cn0:30};});
 await page.route('**/src/engine.ts',async r=>{const response=await r.fetch();await r.fulfill({response,body:await response.text()+'\ngetPose=async()=>({...window.__navigationFixture});\n'});});
 await page.route('**/src/directions.ts',async r=>{const response=await r.fetch();await r.fulfill({response,body:await response.text()+'\ncalculateRoute=async(start,destination,mode)=>({start,destination,mode,live:true,coordinates:[[77.3568,23.30845],[77.3568,23.309],[77.359,23.309]],distance:300,duration:70,steps:[{text:"Continue",distance:61,duration:20},{text:"Turn right onto the next road",distance:239,duration:50},{text:"Arrive at destination",distance:0,duration:0}]});\n'});});
 await page.goto('http://127.0.0.1:5173',{waitUntil:'domcontentloaded'});
 await page.getByRole('button',{name:'Expand position details'}).click();await page.getByRole('button',{name:'Explore simulated motion'}).click();
 await page.getByRole('button',{name:'Search offline destination',exact:true}).click();
 const dest=page.getByRole('combobox',{name:'Destination',exact:true});await dest.fill('Bhopal');await page.getByRole('option').filter({has:page.getByText('Bhopal',{exact:true})}).first().click();
 await page.getByRole('button',{name:'Get directions',exact:true}).click();await page.locator('.stitch-guidance').waitFor();
 await page.getByText('GPS TRACKING',{exact:true}).waitFor();assert.equal(await page.locator('.stitch-nav').count(),0);
 await page.getByRole('button',{name:'Show IMU details'}).click();await page.getByText('Inertial tracking',{exact:true}).waitFor();await page.getByRole('button',{name:'Show GPS details'}).click();await page.getByText('GPS signal',{exact:true}).waitFor();await page.getByRole('button',{name:'Show GPS details'}).click();
 await page.waitForTimeout(2000);await page.screenshot({path:'artifacts/navigation-ui/navigation-gps-fixture.png'});
 await page.evaluate(()=>Object.assign(window.__navigationFixture,{estimated:true,health:'BLACKOUT',positionFresh:false,accuracy:18,gpsTrust:0}));
 await page.getByText('GPS LOST · IMU TRACKING',{exact:true}).waitFor();await page.screenshot({path:'artifacts/navigation-ui/navigation-imu-fixture.png'});
 await page.evaluate(()=>Object.assign(window.__navigationFixture,{imuFresh:false,trackingAvailable:false}));
 await page.getByText('POSITION UNAVAILABLE',{exact:true}).waitFor();await page.getByText('Guidance paused',{exact:true}).waitFor();
 await page.getByRole('button',{name:'Navigation sensor details'}).click();await page.getByRole('button',{name:'Navigate',exact:true}).click();
 await page.evaluate(()=>Object.assign(window.__navigationFixture,{estimated:false,health:'HEALTHY',positionFresh:true,imuFresh:true,trackingAvailable:true,accuracy:7,gpsTrust:.75}));
 await page.getByText('GPS TRACKING',{exact:true}).waitFor();
 for(const viewport of [{width:360,height:640},{width:844,height:390}]){
  await page.setViewportSize(viewport);await page.waitForTimeout(200);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  const hud=await page.locator('.navigation-summary').boundingBox();assert.ok(hud.x>=0&&hud.y>=0&&hud.y+hud.height<=viewport.height);
  await page.screenshot({path:`artifacts/navigation-ui/navigation-${viewport.width}.png`});
 }
 await page.getByRole('button',{name:'Exit navigation',exact:true}).click();await page.getByRole('button',{name:'Search offline destination',exact:true}).waitFor();assert.equal(await page.locator('.stitch-nav').isVisible(),true);assert.deepEqual(errors,[]);
 fs.writeFileSync('artifacts/navigation-ui/browser-results.json',JSON.stringify({passed:true,checks:['planner to active navigation','GPS to IMU to unavailable to GPS display','no dashboard tabs while navigating','sensor details return','360px portrait and landscape layout','exit restores dashboard'],fixtureOnly:true,errors},null,2));
 console.log('PASS: navigation flow, source transitions, responsive instruments, details and exit. Sensor transitions used explicit UI fixtures.');
} finally {await browser.close();}

