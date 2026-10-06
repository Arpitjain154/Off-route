const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
(async () => {
  const input = 'C:/Users/surya/AppData/Local/Packages/5319275A.WhatsAppDesktop_cv1g1gvanyjgm/LocalState/sessions/CC64D0F77EC1D5B981B37B746384E8EA598AA13A/transfers/2026-39/WhatsApp Video 2026-09-28 at 4.02.16 AM.mp4';
  const browser = await chromium.launch({channel:'msedge',headless:true});
  const page = await browser.newPage({ viewport: {width:1280,height:1540} });
  const source = fs.readFileSync(input).toString('base64');
  await page.setContent('<style>body{margin:0;background:#20232a;color:white;font:16px Arial}#frames{display:grid;grid-template-columns:repeat(4,320px)}.frame{text-align:center;padding:5px;box-sizing:border-box}.frame img{width:100%;height:auto}video{display:none}</style><video muted></video><div id="frames"></div>');
  const meta = await page.evaluate(async (source) => {
    const video = document.querySelector('video');
    video.src='data:video/mp4;base64,'+source;
    await new Promise((resolve,reject)=>{video.onloadedmetadata=resolve;video.onerror=()=>reject(new Error('Video decoder failed'))});
    const duration=video.duration;
    for(let i=0;i<20;i++){
      const second=Math.max(0.05,Math.min(duration-0.1,i*(duration-0.1)/19));
      video.currentTime=second;
      await new Promise(resolve=>video.onseeked=resolve);
      const canvas=document.createElement('canvas');canvas.width=video.videoWidth;canvas.height=video.videoHeight;canvas.getContext('2d').drawImage(video,0,0);
      const div=document.createElement('div');div.className='frame';div.innerHTML='<div>'+second.toFixed(1)+' seconds</div><img src="'+canvas.toDataURL('image/jpeg',0.85)+'">';document.querySelector('#frames').append(div);
    }
    return {duration,width:video.videoWidth,height:video.videoHeight};
  },source);
  await page.locator('#frames').screenshot({path:path.join(__dirname,'drift-video-contact-sheet.jpg')});
  fs.writeFileSync(path.join(__dirname,'drift-video-metadata.json'),JSON.stringify(meta,null,2));
  const frames = await page.locator('#frames img').evaluateAll(imgs => imgs.map(img => img.src)); frames.forEach((src,i) => fs.writeFileSync(path.join(__dirname, 'frame-'+String(i).padStart(2,'0')+'.jpg'), Buffer.from(src.split(',')[1],'base64'))); console.log(JSON.stringify(meta));
  await browser.close();
})();


