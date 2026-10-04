import {chromium,expect} from '@playwright/test';
const browser=await chromium.launch({headless:true,channel:'msedge'});
try{
 const page=await browser.newPage({viewport:{width:392,height:850}});
 await page.addInitScript(()=>localStorage.setItem('idr-onboarded','true'));
 await page.goto('http://127.0.0.1:5173',{waitUntil:'domcontentloaded'});
 await page.getByRole('button',{name:'Sensors',exact:true}).click();
 await expect(page.getByText('AI speed estimate (experimental)',{exact:true})).toBeVisible();
 await expect(page.getByText(/Diagnostic output only/)).toBeVisible();
 await page.getByText('AI speed estimate (experimental)',{exact:true}).scrollIntoViewIfNeeded();
 await page.screenshot({path:'artifacts/refinement-oct03/sensors-preview.png'});
 await page.getByRole('button',{name:'Open settings',exact:true}).click();
 await page.getByText('Data collection',{exact:true}).click();
 for(const label of ['Backend API address','Workspace API key','Test saved connection','Check legacy server models'])await expect(page.getByText(label,{exact:true})).toHaveCount(0);
 await expect(page.getByRole('combobox',{name:'Capture rate',exact:true})).toBeVisible();
 await expect(page.getByRole('checkbox',{name:'Collect navigation data locally for learning',exact:true})).toBeVisible();
 await expect(page.getByRole('heading',{name:'Cloud contribution',exact:true})).toBeVisible();
 await page.getByText('Data collection',{exact:true}).scrollIntoViewIfNeeded();
 await page.screenshot({path:'artifacts/refinement-oct03/settings-preview.png'});
 console.log('PASS: raw AI diagnostic is visible; legacy fields/buttons absent; cloud contribution, collection and capture settings retained.');
}finally{await browser.close();}
