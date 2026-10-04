import dotenv from 'dotenv';
import mongoose from 'mongoose';
import { createApp } from './app.js';
dotenv.config();
if (process.env.NODE_ENV === 'production' && !process.env.API_KEY)
  throw new Error('Set API_KEY before deploying');
if (process.env.NODE_ENV === 'production' && (!process.env.ADMIN_API_KEY || process.env.ADMIN_API_KEY===process.env.API_KEY))
  throw new Error('Set a separate ADMIN_API_KEY for model and report publishing');
await mongoose.connect(process.env.MONGO_URI || 'mongodb://127.0.0.1:27017/idr_navigator', {
  serverSelectionTimeoutMS: 5000,
});
const server = createApp().listen(Number(process.env.PORT) || 3001, '0.0.0.0', () =>
  console.log('IDR API ready on port ' + (process.env.PORT || 3001)),
);
server.requestTimeout=15*60*1000;
for (const signal of ['SIGINT', 'SIGTERM'])
  process.on(signal, () =>
    server.close(async () => {
      await mongoose.disconnect();
      process.exit(0);
    }),
  );
