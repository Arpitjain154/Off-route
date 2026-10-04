// Read real sensor snapshots from this app's debug WebView; never inject locations.
// Requires an adb forward of the app's webview_devtools_remote_<pid> to port 9223.
import fs from 'node:fs';
import { execFileSync } from 'node:child_process';
import assert from 'node:assert/strict';

const [serial, durationArg = '600'] = process.argv.slice(2);
if (!serial || serial.startsWith('emulator-')) throw new Error('Specify the authorized physical device serial');
const adb = 'C:/Users/surya/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const shell = (...args) => execFileSync(adb, ['-s', serial, 'shell', ...args], { encoding: 'utf8', timeout: 15000 }).trim();
const originalPid = shell('pidof', 'com.idr.navigator');
const targets = await (await fetch('http://localhost:9223/json/list')).json();
const target = targets.find(t => t.type === 'page' && t.title === 'Off Route' && t.url === 'https://localhost/');
assert.ok(target, 'App debug WebView not found');
const socket = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
let sequence = 0;
const pending = new Map();
socket.onmessage = e => {
  const response = JSON.parse(e.data);
  if (pending.has(response.id)) {
    const request = pending.get(response.id); pending.delete(response.id); clearTimeout(request.timer);
    if (response.error) request.reject(new Error(JSON.stringify(response.error))); else request.resolve(response.result);
  }
};
async function evaluate(expression) {
  const id = ++sequence;
  const response = await new Promise((resolve, reject) => {
    const timer = setTimeout(() => { pending.delete(id); reject(new Error('WebView response timed out')); }, 12000);
    pending.set(id, { resolve, reject, timer });
    socket.send(JSON.stringify({ id, method: 'Runtime.evaluate', params: { expression, returnByValue: true, awaitPromise: true } }));
  });
  if (response.exceptionDetails) throw new Error(JSON.stringify(response.exceptionDetails));
  return response.result.value;
}
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const started = Date.now();
const report = { device: serial, model: shell('getprop', 'ro.product.model'), android: shell('getprop', 'ro.build.version.release'),
  originalPid, started: new Date(started).toISOString(), injectedLocations: false, samples: [], tabCycles: 0, backgroundCycles: 0 };
const output = 'artifacts/gps-lock-physical-monitor.json';
let generation;
let nextCycle = 60;
try {
  while (true) {
    const seconds = (Date.now() - started) / 1000;
    assert.equal(shell('pidof', 'com.idr.navigator'), originalPid, 'App process disappeared or restarted');
    const p = await evaluate("window.Capacitor.nativePromise('DrEngine', 'getPosition', {})");
    generation ??= p.engineGeneration;
    assert.equal(p.engineGeneration, generation, 'Another engine instance appeared');
    assert.equal(p.locationRegistrations, 1, 'Duplicate GPS registration');
    assert.ok(Number.isFinite(p.heading) && Number.isFinite(p.speed), 'Non-finite motion output');
    assert.ok(!p.mapStatus.includes('unavailable'), p.mapStatus);
    assert.equal(await evaluate("document.body.innerText.includes('Start engine')"), false);
    const sample = { seconds: Math.round(seconds), time: new Date().toISOString() };
    for (const key of ['ready','health','headingReady','speed','stationary','gpsAccuracy','gpsAge','satellites','mapStatus','engineGeneration','locationRegistrations']) sample[key] = p[key];
    report.samples.push(sample);
    fs.writeFileSync(output, JSON.stringify(report, null, 2));
    console.log(JSON.stringify(sample));
    if (seconds >= Number(durationArg)) break;
    if (seconds >= nextCycle) {
      for (const tab of ['My drives', 'Settings', 'Navigate']) {
        await evaluate(`document.querySelector('.mobile-nav button[aria-label="${tab}"]').click()`);
        await sleep(600);
        assert.equal(await evaluate("document.querySelector('.mobile-nav button.active').getAttribute('aria-label')"), tab);
      }
      report.tabCycles++;
      if (report.tabCycles % 3 === 0) {
        shell('input', 'keyevent', 'KEYCODE_HOME');
        await sleep(2000);
        shell('am', 'start', '-n', 'com.idr.navigator/.MainActivity');
        await sleep(1000);
        report.backgroundCycles++;
      }
      nextCycle += 60;
    }
    await sleep(Math.min(15000, Math.max(1000, Number(durationArg) * 1000 - (Date.now() - started))));
  }
  assert.ok(report.samples.some(s => s.ready && s.health === 'HEALTHY'), 'No real healthy GPS lock observed');
  report.passed = true;
} catch (error) {
  report.passed = false; report.error = error.stack; throw error;
} finally {
  report.finished = new Date().toISOString();
  report.durationSeconds = (Date.now() - started) / 1000;
  fs.writeFileSync(output, JSON.stringify(report, null, 2));
  socket.close();
}
