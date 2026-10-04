import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import ts from 'typescript';
const { outputText } = ts.transpileModule(fs.readFileSync(new URL('../src/navigationPose.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
});
const { safeNavigationPose } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
const fix = { lat: 23.2418, lon: 77.4029, heading: 0, ready: true, health: 'HEALTHY' };
test('GPS lock with absent or invalid heading stays renderable and awaits heading', () => {
  for (const heading of [undefined, null, NaN, Infinity, -Infinity]) {
    const result = safeNavigationPose({ ...fix, heading });
    assert.equal(result.heading, 0);
    assert.equal(result.headingReady, false);
    assert.equal(result.ready, true);
    assert.equal(result.health, 'HEALTHY');
  }
});
test('invalid coordinates never reach the map camera', () => {
  for (const value of [undefined, null, NaN, Infinity, 181, -181]) {
    assert.equal(safeNavigationPose({ ...fix, lat: value }), null);
    assert.equal(safeNavigationPose({ ...fix, lon: value }), null);
  }
});
test('heading wrap and pending alignment are preserved', () => {
  assert.equal(safeNavigationPose({ ...fix, heading: -1 }).heading, 359);
  assert.equal(safeNavigationPose({ ...fix, heading: 721 }).heading, 1);
  assert.equal(safeNavigationPose({ ...fix, heading: 90, headingReady: false }).headingReady, false);
});
