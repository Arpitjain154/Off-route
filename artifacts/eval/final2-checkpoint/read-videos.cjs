const {chromium}=require('@playwright/test');
const fs=require('node:fs');
const path=require('node:path');
const folder='C:/Users/surya/Desktop/offroute by pankaj';
const out=path.resolve('artifacts/eval/final2-checkpoint');fs.mkdirSync(out,{recursive:true});
(async()=>{
 const server=require('node:http').createServer((req,res)=>{const i=Number(req.url.slice(1));const names=['WhatsApp Video 2026-10-05 at 12.36.55 PM.mp4','WhatsApp Video 2026-10-05 at 12.31.57 PM.mp4'];const file=path.join(folder,names[i]);const size=fs.statSync(file).size;const range=req.headers.range;if(range){const parts=range.replace('bytes=','').split('-');const start=Number(parts[0]);const end=parts[1]?Number(parts[1]):size-1;res.writeHead(206,{'Content-Range':'bytes '+start+'-'+end+'/'+size,'Accept-Ranges':'bytes','Content-Length':end-start+1,'Content-Type':'video/mp4','Access-Control-Allow-Origin':'*'});fs.createReadStream(file,{start,end}).pipe(res);}else{res.writeHead(200,{'Content-Length':size,'Content-Type':'video/mp4','Access-Control-Allow-Origin':'*','Accept-Ranges':'bytes'});fs.createReadStream(file).pipe(res);}});await new Promise(ok=>server.listen(0,'127.0.0.1',ok));
 const browser=await chromium.launch({channel:'msedge',headless:true});
 try {for(const [index,name] of ['WhatsApp Video 2026-10-05 at 12.36.55 PM.mp4','WhatsApp Video 2026-10-05 at 12.31.57 PM.mp4'].entries()){
  const page=await browser.newPage({viewport:{width:1440,height:1600}});
  await page.setContent('<style>body{margin:0;background:#171a20;color:white;font:18px Arial}#frames{display:grid;grid-template-columns:repeat(4,360px)}.frame{text-align:center;padding:5px}.frame img{width:350px}video{display:none}</style><video muted></video><div id="frames"></div>');
  const result=await page.evaluate(async source=>{
   const video=document.querySelector('video');video.crossOrigin='anonymous';video.src=source;
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
  },'http://127.0.0.1:'+server.address().port+'/'+index);
  const prefix='video-'+(index+1);
  for(const [i,frame] of result.frames.entries())fs.writeFileSync(path.join(out,prefix+'-'+String(i).padStart(2,'0')+'.jpg'),Buffer.from(frame.data.split(',')[1],'base64'));
  await page.locator('#frames').screenshot({path:path.join(out,prefix+'-sheet.jpg')});
  const meta={name,duration:result.duration,width:result.width,height:result.height,times:result.frames.map(f=>f.time)};
  fs.writeFileSync(path.join(out,prefix+'.json'),JSON.stringify(meta,null,2));console.log(JSON.stringify(meta));await page.close();
 }}finally{await browser.close();server.close();}
})().catch(e=>{console.error(e.message);process.exitCode=1});



