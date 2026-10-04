import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
const browser=await chromium.launch({headless:true,channel:'msedge'});
try{
 const page=await browser.newPage({viewport:{width:390,height:844}});
 await page.addInitScript(()=>localStorage.setItem('idr-onboarded','true'));
 await page.route('**/src/directions.ts',async route=>{const response=await route.fetch();const body=await response.text();await route.fulfill({response,body:body+'\ncalculateRoute=async(start,destination,mode)=>({start,destination,mode,live:true,coordinates:[[start.lon,start.lat],[destination.lon,destination.lat]],distance:200000,duration:8000,steps:[]});\n'});});
 await page.goto('http://127.0.0.1:5173',{waitUntil:'domcontentloaded'});await page.getByRole('button',{name:'Search offline destination',exact:true}).click();
 for(const [field,value] of [['Destination','Indore'],['Starting point','Bhopal']]){await page.getByRole('combobox',{name:field,exact:true}).fill(value);await page.getByRole('option').filter({has:page.getByText(value,{exact:true})}).first().click();}
 await page.getByRole('button',{name:'Get directions',exact:true}).click();await page.locator('dialog.destination-dialog').waitFor({state:'detached'});
 assert.equal(await page.locator('.stitch-nav').count(),0,'Live navigation has no dashboard tabs');
 await page.getByRole('button',{name:'Navigation sensor details'}).click();await page.getByRole('button',{name:'Navigate',exact:true}).click();
 assert.equal(await page.getByRole('region',{name:'Navigation trip summary'}).isVisible(),true);
 await page.waitForResponse(r=>r.url().includes('.pbf')&&r.status()===200,{timeout:15000}).catch(()=>{});await page.waitForTimeout(1000);
 await page.screenshot({path:'artifacts/refinement/screens/active-navigation-ui-fixture.png'});
 await page.getByRole('button',{name:'Exit navigation',exact:true}).click();await page.getByRole('button',{name:'Search offline destination',exact:true}).waitFor();
 console.log('PASS: UI fixture confirms planner-to-live-navigation and exit transitions. No sensor accuracy claim.');
}finally{await browser.close();}
