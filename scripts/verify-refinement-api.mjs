import {MongoMemoryServer} from 'mongodb-memory-server';
import {spawn} from 'node:child_process';
const db=await MongoMemoryServer.create({instance:{port:27017},binary:{version:'7.0.14'}});
try {const result=await new Promise((resolve,reject)=>{const p=spawn(process.execPath,['--test','server/test/api.test.js'],{stdio:'inherit'});p.on('error',reject);p.on('close',resolve);});process.exitCode=result;}finally{await db.stop();}
