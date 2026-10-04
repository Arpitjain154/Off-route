import {chromium} from '@playwright/test';
import {pathToFileURL} from 'node:url';
import path from 'node:path';
const browser=await chromium.launch({headless:true,channel:'msedge'});
try {
 const page=await browser.newPage({viewport:{width:1020,height:900},deviceScaleFactor:1});
 await page.goto(pathToFileURL(path.resolve('artifacts/refinement/screens.html')).href);
 await page.locator('img').evaluateAll(imgs=>Promise.all(imgs.map(i=>i.decode())));
 await page.screenshot({path:'artifacts/refinement/screens-overview.png',fullPage:true});
 console.log('Rendered six actual-phone screen captures.');
} finally { await browser.close(); }
