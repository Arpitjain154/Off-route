import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import mongoose from 'mongoose';
import request from 'supertest';
import { mkdtemp, rm, readFile, readdir } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { createApp } from '../src/app.js';
let app, folder, id;
before(async () => {
  folder = await mkdtemp(path.join(os.tmpdir(), 'idr-api-test-'));
  await mongoose.connect('mongodb://127.0.0.1:27017/idr_test_' + randomUUID().replaceAll('-', ''));
  app = createApp({ uploadDir: folder, apiKey: 'test-key' });
});
after(async () => {
  await mongoose.connection.dropDatabase();
  await mongoose.disconnect();
  await rm(folder, { recursive: true, force: true });
});
const metadata = {
  phoneModel: 'Test phone',
  mount: 'dashboard',
  route: 'city',
  notes: 'Synthetic integration test',
  sessionId: randomUUID(),
  durationSeconds: 1,
  distanceMeters: 10,
  source: 'simulation',
  startedAt: new Date().toISOString(),
};
const log = Buffer.alloc(270);
log.write('DRLOG');
log[5] = 1;
log.writeUInt16LE(100, 6);
test('Phone credentials cannot publish models or reports when a separate publisher key is configured', async () => {
  const secured=createApp({uploadDir:folder,apiKey:'phone-key',adminKey:'publisher-key'});
  assert.equal((await request(secured).post('/api/models').set('X-API-Key','phone-key')).status,401);
  assert.equal((await request(secured).post('/api/reports').set('X-API-Key','phone-key')).status,401);
  assert.equal((await request(secured).get('/api/drives').set('X-API-Key','phone-key')).status,200);
  assert.equal((await request(secured).post('/api/reports').set('X-API-Key','publisher-key').send({})).status,400);
});
function driveUpload(data = metadata, file = log) {
  let req = request(app).post('/api/drives').set('X-API-Key', 'test-key');
  for (const [k, v] of Object.entries(data)) req = req.field(k, String(v));
  return req.attach('file', file, 'test.drlog');
}
test('API auth, DRLOG streaming, metadata, retry idempotency, filters, reports and model registry', async () => {
  assert.equal((await request(app).get('/api/drives')).status, 401);
  const upload = await driveUpload();
  assert.equal(upload.status, 201, JSON.stringify(upload.body));
  id = upload.body._id;
  assert.equal(upload.body.sizeBytes, 270);
  assert.equal(upload.body.mount, 'dashboard');
  assert.equal(upload.body.source, 'simulation');
  const retry = await driveUpload();
  assert.equal(retry.status, 200);
  assert.equal(retry.body._id, id);
  assert.equal((await readdir(path.join(folder, 'drives'))).length, 1);
  const list = await request(app).get('/api/drives?mount=dashboard').set('X-API-Key', 'test-key');
  assert.equal(list.body.length, 1);
  const download = await request(app)
    .get(`/api/drives/${id}/file`)
    .set('X-API-Key', 'test-key')
    .buffer(true);
  assert.equal(download.status, 200);
  assert.deepEqual(download.body, log);
  assert.equal(
    (await request(app).get(`/api/reports/${id}`).set('X-API-Key', 'test-key')).status,
    404,
  );
  const report = {
    driveId: id,
    ateMeters: 2.5,
    driftPercent: 1.2,
    timestamps: [0, 1, 2],
    driftMeters: [0, 1, 3],
  };
  assert.equal(
    (await request(app).post('/api/reports').set('X-API-Key', 'test-key').send(report)).status,
    201,
  );
  assert.equal(
    (await request(app).get(`/api/reports/${id}`).set('X-API-Key', 'test-key')).body.ateMeters,
    2.5,
  );
  assert.equal(
    (
      await request(app)
        .post('/api/reports')
        .set('X-API-Key', 'test-key')
        .send({ ...report, timestamps: [2, 1] })
    ).status,
    400,
  );
  for (const version of ['1.2.0', '1.10.0']) {
    const model = await readFile(
      new URL('../../android/app/src/main/assets/models/speed.tflite', import.meta.url),
    );
    const result = await request(app)
      .post('/api/models')
      .set('X-API-Key', 'test-key')
      .field('name', 'speed')
      .field('version', version)
      .field('placeholder', 'false')
      .field('validated', 'false')
      .field('dataset', 'IO-VNBD')
      .attach('file', model, 'speed.tflite');
    assert.equal(result.status, 201, JSON.stringify(result.body));
  }
  const latest = await request(app).get('/api/models/latest/speed').set('X-API-Key', 'test-key');
  assert.equal(latest.body.version, '1.10.0');
  assert.equal(latest.body.sha256.length, 64);
  assert.equal(latest.body.placeholder, false);
  assert.equal(latest.body.validated, false);
  assert.equal(latest.body.dataset, 'IO-VNBD');
  assert.equal(
    (await request(app).get(latest.body.downloadUrl).set('X-API-Key', 'test-key')).status,
    200,
  );
});
test('Reject invalid log, mount and object ID; do not leave orphan files', async () => {
  assert.equal(
    (await driveUpload({ ...metadata, sessionId: randomUUID() }, Buffer.from('invalid'))).status,
    400,
  );
  assert.equal(
    (await driveUpload({ ...metadata, sessionId: randomUUID(), mount: 'bad' })).status,
    400,
  );
  assert.equal(
    (await request(app).get('/api/drives/not-an-id/file').set('X-API-Key', 'test-key')).status,
    400,
  );
  assert.equal((await readdir(path.join(folder, 'drives'))).length, 1);
});
