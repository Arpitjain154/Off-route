import {test} from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import ts from 'typescript';
const code=ts.transpileModule(fs.readFileSync('src/recordingLifecycle.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
const {RecordingLifecycle}=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'));
test('navigation start and immediate exit serialize one start and one stop',async()=>{
 const calls=[];let release;const wait=new Promise(r=>release=r);
 const recorder=new RecordingLifecycle(async()=>{calls.push('start');await wait;},async()=>calls.push('stop'));
 const a=recorder.set(true),b=recorder.set(true),c=recorder.set(false);release();await Promise.all([a,b,c]);
 assert.deepEqual(calls,['start','stop']);assert.equal(recorder.active,false);
});
test('failed start permits retry without a false recording state',async()=>{
 let n=0;const recorder=new RecordingLifecycle(async()=>{if(++n===1)throw Error('storage full');},async()=>{});
 await assert.rejects(recorder.set(true),/storage full/);assert.equal(recorder.active,false);
 await recorder.set(true);assert.equal(recorder.active,true);
});
test('restored native recording is stopped without starting a duplicate',async()=>{
 const calls=[];const recorder=new RecordingLifecycle(async()=>calls.push('start'),async()=>calls.push('stop'));
 recorder.adopt(true);await recorder.set(true);await recorder.set(false);assert.deepEqual(calls,['stop']);
});


test('recovery failure retains identity and releases recording state without blocking the engine',async()=>{
 const events=[];const recorder=new RecordingLifecycle(async()=>{},async()=>{throw Error('Incomplete header');});recorder.adopt(true);
 const recovered=await recorder.recover(async error=>events.push(error.message));
 assert.equal(recovered,false);assert.equal(recorder.active,false);assert.deepEqual(events,['Incomplete header']);
 await recorder.set(true);assert.equal(recorder.active,true);
});
