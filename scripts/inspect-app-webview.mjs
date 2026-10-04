import fs from 'node:fs';
const targets=await(await fetch(`http://localhost:${process.argv[3]||9223}/json/list`)).json();
const socket=new WebSocket(targets.find(t=>t.type==='page'&&t.title==='Off Route').webSocketDebuggerUrl);
await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject;});
const timer=setTimeout(()=>{socket.close();process.exitCode=1;},15000);
socket.onmessage=e=>{const r=JSON.parse(e.data);if(r.id===1){console.log(JSON.stringify(r.result||r.error,null,2));clearTimeout(timer);socket.close();}};
socket.send(JSON.stringify({id:1,method:'Runtime.evaluate',params:{expression:fs.readFileSync(process.argv[2],'utf8'),awaitPromise:true,returnByValue:true}}));
