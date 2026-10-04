import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import multer from 'multer';
import mongoose from 'mongoose';
import { z } from 'zod';
import semver from 'semver';
import { randomUUID, createHash, timingSafeEqual } from 'node:crypto';
import { mkdirSync, createReadStream } from 'node:fs';
import { unlink, open } from 'node:fs/promises';
import path from 'node:path';
import { Drive, Model, Report } from './models.js';
const names = ['motion', 'gps-health', 'speed'];
const driveInput = z.object({
  phoneModel: z.string().min(1).max(120),
  mount: z.enum(['windshield', 'dashboard', 'console']),
  route: z.enum(['city', 'highway', 'tunnel', 'mixed']),
  notes: z.string().max(2000).default(''),
  sessionId: z.string().uuid(),
  durationSeconds: z.coerce.number().min(0).max(86400),
  distanceMeters: z.coerce.number().min(0).max(5000000),
  source: z.enum(['device', 'simulation']).default('device'),
  startedAt: z.string().datetime(),
});
const reportInput = z
  .object({
    driveId: z.string().regex(/^[a-f\d]{24}$/i),
    ateMeters: z.number().finite().min(0),
    driftPercent: z.number().finite().min(0),
    timestamps: z.array(z.number().finite().min(0)).min(1).max(100000),
    driftMeters: z.array(z.number().finite().min(0)).min(1).max(100000),
  })
  .refine(
    (v) =>
      v.timestamps.length === v.driftMeters.length &&
      v.timestamps.every((t, i) => !i || t >= v.timestamps[i - 1]),
    'Series must have equal lengths and ordered timestamps',
  );
const asyncRoute = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
export function createApp({
  uploadDir = process.env.UPLOAD_DIR || 'uploads',
  apiKey = process.env.API_KEY || '',
  adminKey = process.env.ADMIN_API_KEY || apiKey,
} = {}) {
  const app = express(),
    root = path.resolve(uploadDir);
  if (process.env.TRUST_PROXY_HOPS) app.set('trust proxy', Number(process.env.TRUST_PROXY_HOPS));
  for (const kind of ['drives', 'models']) mkdirSync(path.join(root, kind), { recursive: true });
  app.use(
    helmet(),
    cors({
      origin: (origin, done) => {
        const allowed = (
          process.env.CORS_ORIGINS ||
          'http://localhost:5173,http://127.0.0.1:5173,https://localhost,http://localhost'
        ).split(',');
        done(null, !origin || allowed.includes(origin));
      },
    }),
    express.json({ limit: '3mb' }),
  );
  app.get('/api/health', (_req, res) =>
    res
      .status(mongoose.connection.readyState === 1 ? 200 : 503)
      .json({ status: mongoose.connection.readyState === 1 ? 'ready' : 'database-unavailable' }),
  );
  app.use(
    '/api',
    rateLimit({ windowMs: 60000, limit: 180, standardHeaders: true, legacyHeaders: false }),
  );
  app.use('/api', (req, res, next) => {
    if (apiKey) {
      const value = Buffer.from(req.get('X-API-Key') || '');
      const privileged=req.method==='POST' && ['/models','/reports'].includes(req.path.replace(/\/$/,''));
      const expected = Buffer.from(privileged ? adminKey : apiKey);
      if (value.length !== expected.length || !timingSafeEqual(value, expected))
        return res.status(401).json({ error: privileged ? 'Publisher API key required' : 'API key required' });
    }
    next();
  });
  const upload = (kind) =>
    multer({
      storage: multer.diskStorage({
        destination: path.join(root, kind),
        filename: (_r, f, cb) =>
          cb(null, randomUUID() + (kind === 'drives' ? '.drlog' : '.tflite')),
      }),
      limits: {
        fileSize: kind === 'drives' ? 500 * 1024 * 1024 : 100 * 1024 * 1024,
        files: 1,
        fields: 20,
      },
      fileFilter: (_r, f, cb) =>
        cb(null, f.originalname.toLowerCase().endsWith(kind === 'drives' ? '.drlog' : '.tflite')),
    }).single('file');
  const clean = async (file) => {
    if (file) await unlink(file.path).catch(() => {});
  };
  const validateFile = async (file, kind) => {
    if (!file)
      throw Object.assign(new Error('A valid ' + kind + ' file is required'), { status: 400 });
    const handle = await open(file.path, 'r');
    try {
      const b = Buffer.alloc(200);
      await handle.read(b, 0, 200, 0);
      if (kind === 'DRLOG') {
        if (
          file.size < 200 ||
          b.subarray(0, 5).toString() !== 'DRLOG' ||
          b[5] !== 1 ||
          ![100, 200].includes(b.readUInt16LE(6)) ||
          (file.size - 200) % 70 !== 0
        )
          throw Object.assign(new Error('Invalid DRLOG v1 header or record length'), {
            status: 400,
          });
      } else if (b.subarray(4, 8).toString() !== 'TFL3')
        throw Object.assign(new Error('Invalid TFLite FlatBuffer'), { status: 400 });
    } finally {
      await handle.close();
    }
  };
  app.post(
    '/api/drives',
    upload('drives'),
    asyncRoute(async (req, res) => {
      try {
        const input = driveInput.parse(req.body);
        await validateFile(req.file, 'DRLOG');
        const existing = await Drive.findOne({ sessionId: input.sessionId });
        if (existing) {
          await clean(req.file);
          return res.status(200).json(existing);
        }
        const doc = await Drive.create({
          ...input,
          filename: req.file.filename,
          sizeBytes: req.file.size,
        });
        res.status(201).json(doc);
      } catch (e) {
        await clean(req.file);
        throw e;
      }
    }),
  );
  app.get(
    '/api/drives',
    asyncRoute(async (req, res) => {
      const filter = {};
      for (const key of ['mount', 'route', 'phoneModel', 'source'])
        if (typeof req.query[key] === 'string') filter[key] = req.query[key];
      if (req.query.phone) filter.phoneModel = String(req.query.phone);
      const limit = Math.min(100, Math.max(1, Number(req.query.limit) || 50));
      const skip = Math.max(0, Number(req.query.offset) || 0);
      res.json(await Drive.find(filter).sort({ uploadedAt: -1 }).skip(skip).limit(limit));
    }),
  );
  app.param('id', (req, res, next, id) =>
    mongoose.isValidObjectId(id) ? next() : res.status(400).json({ error: 'Invalid ID' }),
  );
  app.get(
    '/api/drives/:id/file',
    asyncRoute(async (req, res) => {
      const doc = await Drive.findById(req.params.id);
      if (!doc) return res.status(404).json({ error: 'Drive not found' });
      res.setHeader('Content-Disposition', `attachment; filename="${doc.filename}"`);
      res.sendFile(path.join(root, 'drives', path.basename(doc.filename)));
    }),
  );
  app.post(
    '/api/models',
    upload('models'),
    asyncRoute(async (req, res) => {
      try {
        const input = z
          .object({
            name: z.enum(names),
            version: z
              .string()
              .refine((v) => !!semver.valid(v), 'Use semantic version, e.g. 1.0.0'),
            placeholder: z.enum(['true', 'false']).default('true'),
            validated: z.enum(['true', 'false']).default('false'),
            dataset: z.string().max(120).optional(),
          })
          .parse(req.body);
        await validateFile(req.file, 'TFLite');
        const hash = createHash('sha256');
        for await (const chunk of createReadStream(req.file.path)) hash.update(chunk);
        const doc = await Model.create({
          ...input,
          placeholder: input.placeholder === 'true',
          validated: input.validated === 'true',
          filename: req.file.filename,
          sizeBytes: req.file.size,
          sha256: hash.digest('hex'),
        });
        res.status(201).json(doc);
      } catch (e) {
        await clean(req.file);
        throw e;
      }
    }),
  );
  app.get(
    '/api/models/latest/:name',
    asyncRoute(async (req, res) => {
      if (!names.includes(req.params.name))
        return res.status(400).json({ error: 'Unknown model name' });
      const all = await Model.find({ name: req.params.name });
      all.sort((a, b) => semver.rcompare(a.version, b.version));
      if (!all.length) return res.status(404).json({ error: 'No model published' });
      res.json({ ...all[0].toObject(), downloadUrl: `/api/models/${all[0]._id}/file` });
    }),
  );
  app.get(
    '/api/models/:id/file',
    asyncRoute(async (req, res) => {
      const doc = await Model.findById(req.params.id);
      if (!doc) return res.status(404).json({ error: 'Model not found' });
      res
        .type('application/octet-stream')
        .sendFile(path.join(root, 'models', path.basename(doc.filename)));
    }),
  );
  app.post(
    '/api/reports',
    asyncRoute(async (req, res) => {
      const input = reportInput.parse(req.body);
      if (!(await Drive.exists({ _id: input.driveId })))
        return res.status(404).json({ error: 'Drive not found' });
      res
        .status(201)
        .json(
          await Report.findOneAndUpdate({ driveId: input.driveId }, input, {
            upsert: true,
            new: true,
            runValidators: true,
          }),
        );
    }),
  );
  app.get(
    '/api/reports/:driveId',
    asyncRoute(async (req, res) => {
      if (!mongoose.isValidObjectId(req.params.driveId))
        return res.status(400).json({ error: 'Invalid drive ID' });
      const report = await Report.findOne({ driveId: req.params.driveId });
      if (!report) return res.status(404).json({ error: 'Report pending' });
      res.json(report);
    }),
  );
  app.use((err, req, res, _next) => {
    if (res.headersSent) return _next(err);
    const status =
      err instanceof z.ZodError
        ? 400
        : err instanceof multer.MulterError
          ? 413
          : err.code === 11000
            ? 409
            : err.status || 500;
    res
      .status(status)
      .json({
        error:
          status === 500
            ? 'Internal server error'
            : err instanceof z.ZodError
              ? err.issues.map((i) => i.path.join('.') + ': ' + i.message).join('; ')
              : err.message,
      });
    if (status === 500) console.error(err);
  });
  return app;
}
