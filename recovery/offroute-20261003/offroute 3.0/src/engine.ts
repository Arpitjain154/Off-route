import { Capacitor, registerPlugin } from '@capacitor/core';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { client } from './api';
import type { Pose, Settings, Drive } from './types';
interface EnginePlugin {
  setAppearance(o:{dark:boolean}):Promise<void>;
  startEngine(): Promise<void>;
  stopEngine(): Promise<void>;
  getPosition(): Promise<Pose>;
  requestPermissions(): Promise<unknown>;
  backgroundSettings(): Promise<void>;
  reportDiagnostic(o: { message: string }): Promise<void>;
}
interface LogPlugin {
  shareDiagnostics(): Promise<void>;
  recoverLog(o:{sessionId:string}):Promise<{path:string;sizeBytes:number;durationSeconds:number;distanceMeters:number;phoneModel:string;recovered?:boolean}>;
  startLogging(o: { mount: string; sampleRate: number; sessionId: string }): Promise<void>;
  stopLogging(): Promise<{
    path: string;
    sizeBytes: number;
    durationSeconds: number;
    distanceMeters: number;
    phoneModel: string;
  }>;
  getLogFile(o?: { path?: string }): Promise<{ path: string; sizeBytes: number }>;
}
interface ModelsPlugin {
  checkForUpdate(): Promise<{ models: { name: string; version: string; placeholder: boolean; validated: boolean }[] }>;
  downloadModel(o: {
    name: string;
    path: string;
    version: string;
    sha256: string;
    placeholder: boolean;
    validated: boolean;
  }): Promise<void>;
}
export const DrEngine = registerPlugin<EnginePlugin>('DrEngine');
export const SensorPlugin = registerPlugin<LogPlugin>('SensorPlugin');
export const ModelPlugin = registerPlugin<ModelsPlugin>('ModelPlugin');
export const native = Capacitor.isNativePlatform();
export function reportEngineDiagnostic(error: unknown) {
  const message = error instanceof Error ? error.stack || error.message : String(error);
  console.error('IDR navigation:', message);
  if (native) void DrEngine.reportDiagnostic({ message: message.slice(0, 8192) }).catch(() => {});
}
let nativeStart: Promise<void> | null = null;
// Share a pending permission/start request across StrictMode mounts, tab changes and resume events.
export function ensureNativeEngine(): Promise<void> {
  if (!native) return Promise.resolve();
  if (!nativeStart) {
    nativeStart = DrEngine.startEngine().finally(() => { nativeStart = null; });
  }
  return nativeStart;
}
export const initialPose: Pose = {
  lat: 23.2418,
  lon: 77.4029,
  heading: 0,
  speed: 0,
  accuracy: 0,
  confidence: 0,
  health: 'BLACKOUT',
  distance: 0,
  elapsed: 0,
  bytes: 0,
  gpsTrust: 0,
  physicsTrust: 0,
  mapTrust: 0,
  aiTrust: 0,
  ready: false,
  source: native ? 'device' : 'simulation',
  satellites: 0,
  cn0: 0,
};
let simulated = false,
  simStart = 0,
  logStart = 0,
  logDistance = 0;
let points: [number, number][] = [
  [77.4029, 23.2418],
  [77.4028, 23.246],
  [77.407, 23.251],
  [77.412, 23.252],
  [77.416, 23.248],
  [77.414, 23.243],
  [77.409, 23.24],
  [77.4029, 23.2418],
];
export async function startEngine(sim = false) {
  simulated = sim;
  if (sim) {
    try {
      const map = await fetch('/maps/route.json').then((r) => r.json());
      if (map.length > 1) points = map;
    } catch {}
    simStart = performance.now();
  } else if (native) await ensureNativeEngine();
  else throw new Error('Live sensor tracking requires the Android app. Try the test drive here.');
}
export async function stopEngine() {
  if (!simulated && native) await DrEngine.stopEngine();
  simulated = false;
}
export async function getPose(): Promise<Pose> {
  if (!simulated) return native ? DrEngine.getPosition() : initialPose;
  const t = (performance.now() - simStart) / 1000;
  const length = points.length - 1;
  const f = (t / 8) % length;
  const i = Math.floor(f);
  const a = points[i],
    b = points[i + 1];
  const k = f - i;
  const cycle = t % 90;
  const health =
    cycle > 50 && cycle < 68 ? 'BLACKOUT' : cycle > 43 && cycle <= 50 ? 'DEGRADING' : 'HEALTHY';
  const speed = 9.8 + Math.sin(t / 9) * 2;
  return {
    ...initialPose,
    lat: a[1] + (b[1] - a[1]) * k,
    lon: a[0] + (b[0] - a[0]) * k,
    heading:
      ((Math.atan2((b[0] - a[0]) * Math.cos((a[1] * Math.PI) / 180), b[1] - a[1]) * 180) / Math.PI +
        360) %
      360,
    speed,
    ready: true,
    health,
    accuracy: health === 'BLACKOUT' ? 18 : 3.2,
    confidence: health === 'BLACKOUT' ? 0.71 : 0.96,
    gpsTrust: health === 'BLACKOUT' ? 0 : 0.95,
    physicsTrust: 0.92,
    mapTrust: 0.86,
    aiTrust: 0,
    source: 'simulation',
    elapsed: logStart ? (performance.now() - logStart) / 1000 : 0,
    distance: logStart ? ((performance.now() - logStart) / 1000) * 10 : 0,
    bytes: logStart ? 200 + Math.floor((performance.now() - logStart) / 10) * 70 : 0,
    satellites: health === 'BLACKOUT' ? 0 : 18,
    cn0: health === 'BLACKOUT' ? 0 : 36,
    modelStatus: 'Simulation · native IO-VNBD inference runs on Android',
    mapStatus: 'Bhopal offline area',
  };
}
export async function beginLog(s: Settings, sessionId: string) {
  if (simulated) {
    logStart = performance.now();
    logDistance = 0;
  } else await SensorPlugin.startLogging({ mount: s.mount, sampleRate: s.sampleRate, sessionId });
}
export async function endLog() {
  if (simulated) {
    const durationSeconds = (performance.now() - logStart) / 1000;
    logStart = 0;
    return {
      path: 'simulation',
      sizeBytes: 200 + 70 * Math.floor(durationSeconds * 100),
      durationSeconds,
      distanceMeters: durationSeconds * 10 + logDistance,
      phoneModel: 'Browser test drive',
    };
  }
  return SensorPlugin.stopLogging();
}
// Simulation creates a bounded DRLOG fixture, never a fabricated physical sensor recording.
function simulationLog(d: Drive) {
  const count = Math.min(60000, Math.floor(d.durationSeconds * 100));
  const buffer = new ArrayBuffer(200 + 70 * count),
    v = new DataView(buffer),
    u = new Uint8Array(buffer),
    enc = new TextEncoder();
  u.set(enc.encode('DRLOG'));
  v.setUint8(5, 1);
  v.setUint16(6, 100, true);
  for (const [text, offset] of [
    [d.phoneModel, 8],
    [d.mount, 72],
    [d.sessionId, 136],
  ] as const)
    u.set(enc.encode(text).slice(0, 63), offset);
  for (let i = 0; i < count; i++) {
    const o = 200 + i * 70;
    v.setBigInt64(o, BigInt(i) * 10000000n, true);
    v.setFloat32(o + 16, 9.80665, true);
    v.setFloat32(o + 44, 23.2418, true);
    v.setFloat32(o + 48, 77.4029, true);
    v.setFloat32(o + 56, 10, true);
    v.setFloat32(o + 60, 5, true);
    v.setUint8(o + 64, 18);
    v.setFloat32(o + 65, 36, true);
    v.setUint8(o + 69, 1);
  }
  return new Blob([buffer], { type: 'application/octet-stream' });
}
export async function uploadDrive(d: Drive) {
  const form = new FormData();
  if (d.source === 'simulation') form.append('file', simulationLog(d), d.filename);
  else {
    const info = await SensorPlugin.getLogFile({ path: d.localPath });
    const response = await fetch(Capacitor.convertFileSrc(info.path));
    if (!response.ok) throw new Error('The local drive file could not be opened.');
    form.append('file', await response.blob(), d.filename);
  }
  for (const key of [
    'phoneModel',
    'mount',
    'route',
    'notes',
    'sessionId',
    'durationSeconds',
    'distanceMeters',
    'source',
    'startedAt',
  ] as const)
    form.append(key, String(d[key]));
  const res = await client().post<Drive>('/api/drives', form, { timeout: 0 });
  return res.data;
}
export async function updateModels() {
  const messages = [];
  for (const name of ['motion', 'gps-health', 'speed']) {
    try {
      const { data } = await client().get('/api/models/latest/' + name);
      if (native) {
        const response = await client().get(data.downloadUrl, {
          responseType: 'arraybuffer',
          timeout: 120000,
        });
        const bytes = new Uint8Array(response.data);
        let binary = '';
        for (let i = 0; i < bytes.length; i += 8192)
          binary += String.fromCharCode(...bytes.subarray(i, i + 8192));
        const result = await Filesystem.writeFile({
          path: `model-${name}.tflite`,
          data: btoa(binary),
          directory: Directory.Cache,
        });
        await ModelPlugin.downloadModel({
          name,
          path: result.uri,
          version: data.version,
          sha256: data.sha256,
          placeholder: data.placeholder,
          validated: data.validated === true,
        });
      }
      messages.push(`${name} ${data.version}${data.placeholder ? ' (placeholder)' : ''}`);
    } catch (e) {
      if ((e as { response?: { status: number } }).response?.status === 404)
        messages.push(`${name}: no update published`);
      else throw e;
    }
  }
  return messages;
}
