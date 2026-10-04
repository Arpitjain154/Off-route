import {test} from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import ts from 'typescript';
const code=ts.transpileModule(fs.readFileSync('src/cloudState.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext}}).outputText;
const {cloudJourneyLabel,canUploadJourney}=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'));
test('legacy upload is never shown as Supabase upload',()=>assert.equal(cloudJourneyLabel(undefined),'Saved on phone'));
test('simulation and disabled consent cannot enqueue',()=>{assert.equal(canUploadJourney('simulation',true,true),false);assert.equal(canUploadJourney('device',true,false),false);assert.equal(canUploadJourney('device',true,true),true)});
test('queue states remain understandable',()=>{assert.equal(cloudJourneyLabel({state:'action-required',message:'Access denied'}),'Needs attention: Access denied');assert.equal(cloudJourneyLabel({state:'uploaded'}),'Uploaded to Supabase');});
