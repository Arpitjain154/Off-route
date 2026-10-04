import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),ts=require('typescript'),React=require('react'),{renderToStaticMarkup}=require('react-dom/server');
const mod={exports:{}};
new Function('require','module','exports',ts.transpileModule(fs.readFileSync('src/AiSpeedStatus.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX}}).outputText)(require,mod,mod.exports);
const render=(pose,units='km')=>renderToStaticMarkup(React.createElement(mod.exports.default,{pose,units}));
test('raw AI reading is visible without granting navigation trust',()=>{
 const html=render({aiSpeedAvailable:true,aiSpeedEstimate:10,aiTrust:0,aiSpeedValidated:false});
 assert.match(html,/36.0 km\/h/);assert.match(html,/not used for navigation/);
 assert.match(render({aiSpeedAvailable:true,aiSpeedEstimate:10},'mi'),/22.4 mph/);
});
test('stale, absent and invalid estimates never display a speed',()=>{
 for(const p of [{},{aiSpeedAvailable:false,aiSpeedEstimate:10},{aiSpeedAvailable:true,aiSpeedEstimate:NaN},{aiSpeedAvailable:true,aiSpeedEstimate:-1}])assert.match(render(p),/Unavailable/);
 assert.match(render({aiSpeedAvailable:true,aiSpeedEstimate:0}),/0.0 km\/h/);
});
test('calibration waiting does not claim corrections are active',()=>{
 assert.match(render({aiSpeedValidated:true,aiTrust:0}),/await calibration/);
 assert.match(render({aiSpeedValidated:true,aiTrust:.2,aiFusionActive:true}),/corrections active/);
});
test('calibrated model does not claim ESKF use when measurement was rejected',()=>{
 const html=render({aiSpeedValidated:true,aiTrust:.2,aiFusionActive:false});
 assert.doesNotMatch(html,/corrections active/);
 assert.match(html,/ESKF/);
});
