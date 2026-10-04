const {chromium}=require('@playwright/test');
const fs=require('node:fs');
const path=require('node:path');
const folder='C:/Users/surya/AppData/Local/Packages/5319275A.WhatsAppDesktop_cv1g1gvanyjgm/LocalState/sessions/CC64D0F77EC1D5B981B37B746384E8EA598AA13A/transfers/2026-39';
const out=path.resolve('artifacts/sept29-navigation');fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true});
 try {for(const [index,name] of ['WhatsApp Video 2026-09-29 at 10.22.12 PM.mp4','WhatsApp Video 2026-09-29 at 10.22.29 PM.mp4'].entries()){
  const page=await browser.newPage({viewport:{width:1440,height:1600}});
  await page.setContent('<style>body{margin:0;background:#171a20;color:white;font:18px Arial}#frames{display:grid;grid-template-columns:repeat(4,360px)}.frame{text-align:center;padding:5px}.frame img{width:350px}video{display:none}</style><video muted></video><div id="frames"></div>');
  const result=await page.evaluate(async source=>{
   const video=document.querySelector('video');video.src='data:video/mp4;base64,'+source;
   await new Promise((ok,no)=>{video.onloadedmetadata=ok;video.onerror=no});
   const frames=[];
   for(let i=0;i<16;i++){
    const time=Math.max(.05,Math.min(video.duration-.1,i*video.duration/16));
    const seek=new Promise(ok=>video.onseeked=ok);video.currentTime=time;await seek;
    const canvas=document.createElement('canvas');canvas.width=video.videoWidth;canvas.height=video.videoHeight;
    canvas.getContext('2d').drawImage(video,0,0);const data=canvas.toDataURL('image/jpeg',.93);
    const div=document.createElement('div');div.className='frame';div.innerHTML='<div>'+time.toFixed(1)+'s</div><img src="'+data+'">';document.querySelector('#frames').append(div);
    frames.push({time,data});
   }
   return {duration:video.duration,width:video.videoWidth,height:video.videoHeight,frames};
  },fs.readFileSync(path.join(folder,name)).toString('base64'));
  const prefix='video-'+(index+1);
  for(const [i,frame] of result.frames.entries())fs.writeFileSync(path.join(out,prefix+'-'+String(i).padStart(2,'0')+'.jpg'),Buffer.from(frame.data.split(',')[1],'base64'));
  await page.locator('#frames').screenshot({path:path.join(out,prefix+'-sheet.jpg')});
  const meta={name,duration:result.duration,width:result.width,height:result.height,times:result.frames.map(f=>f.time)};
  fs.writeFileSync(path.join(out,prefix+'.json'),JSON.stringify(meta,null,2));console.log(JSON.stringify(meta));await page.close();
 }}finally{await browser.close();}
})().catch(e=>{console.error(e.message);process.exitCode=1});
