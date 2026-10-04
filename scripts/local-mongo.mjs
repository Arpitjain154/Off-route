import { MongoMemoryServer } from 'mongodb-memory-server';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';
await mkdir('data/mongo', { recursive: true });
const db = await MongoMemoryServer.create({
  instance: { port: 27017, dbPath: path.resolve('data/mongo'), storageEngine: 'wiredTiger' },
  binary: { version: '7.0.14' },
});
console.log(
  `Local MongoDB: ${db.getUri()} — persistent files in data/mongo. Keep this terminal open.`,
);
for (const signal of ['SIGINT', 'SIGTERM'])
  process.on(signal, async () => {
    await db.stop();
    process.exit(0);
  });
