import { readFile } from 'node:fs/promises';
const base = process.env.API_URL || 'http://127.0.0.1:3001';
const manifest = JSON.parse(await readFile('android/app/src/main/assets/models/manifest.json', 'utf8'));
for (const { name, version, placeholder, validated, dataset } of manifest.models) {
  const data = await readFile(`android/app/src/main/assets/models/${name}.tflite`);
  const form = new FormData();
  form.set('name', name);
  form.set('version', version);
  form.set('placeholder', String(placeholder));
  form.set('validated', String(validated));
  form.set('dataset', dataset);
  form.set('file', new Blob([data]), name + '.tflite');
  const r = await fetch(base + '/api/models', {
    method: 'POST',
    headers: process.env.ADMIN_API_KEY || process.env.API_KEY ? { 'X-API-Key': process.env.ADMIN_API_KEY || process.env.API_KEY } : {},
    body: form,
  });
  if (!r.ok && r.status !== 409) throw new Error(await r.text());
  console.log(name, version, r.status === 409 ? 'already registered' : `registered (${dataset})`);
}
