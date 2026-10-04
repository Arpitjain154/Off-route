import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
const browser=await chromium.launch({headless:true,channel:'msedge'});
try {
 const page=await browser.newPage({viewport:{width:390,height:844}});
 await page.addInitScript(()=>localStorage.setItem('idr-onboarded','true'));
 await page.goto('http://127.0.0.1:5173',{waitUntil:'domcontentloaded'});
 await page.getByRole('button',{name:'Open settings',exact:true}).filter({visible:true}).click();
 await page.getByRole('heading',{name:'Cloud contribution',exact:true}).waitFor();
 assert.match(await page.locator('.cloud-settings').innerText(),/Browser simulations stay local/);
 assert.equal(await page.getByRole('button',{name:'Create account',exact:true}).count(),0);
 await page.locator('.cloud-settings').scrollIntoViewIfNeeded();
 await page.screenshot({path:'artifacts/cloud-integration/cloud-browser-settings.png'});
 await page.getByRole('button',{name:'Close settings',exact:true}).click();
 assert.equal(await page.getByRole('button',{name:'Search offline destination',exact:true}).isVisible(),true);
 console.log('PASS: cloud browser disclosure, simulation exclusion and navigation remain available.');
}finally{await browser.close();}
