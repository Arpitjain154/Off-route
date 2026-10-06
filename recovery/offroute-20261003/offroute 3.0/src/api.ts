import axios from 'axios';
import type { Settings, Drive, Report } from './types';
export const defaults: Settings = {
  mount: 'dashboard',
  sampleRate: 100,
  units: 'km',
  theme: 'light',
  learningEnabled: true,
  apiUrl: import.meta.env.VITE_API_URL || '',
  apiKey: '',
};
export function readSettings(): Settings {
  try {
    return { ...defaults, ...JSON.parse(localStorage.getItem('idr-settings') || '{}') };
  } catch {
    return defaults;
  }
}
export function client() {
  const s = readSettings();
  return axios.create({
    baseURL: s.apiUrl.replace(/\/$/, ''),
    timeout: 15000,
    headers: s.apiKey ? { 'X-API-Key': s.apiKey } : {},
  });
}
export const api = {
  drives: () =>
    client()
      .get<Drive[]>('/api/drives')
      .then((r) => r.data),
  report: (id: string) =>
    client()
      .get<Report>('/api/reports/' + id)
      .then((r) => r.data),
  health: () =>
    client()
      .get('/api/health')
      .then((r) => {
        if (r.data?.status !== 'ready')
          throw new Error(
            'This address is not a ready IDR backend. Check the API URL in Settings.',
          );
        return r;
      }),
};
export function errorMessage(e: unknown) {
  return axios.isAxiosError(e)
    ? e.response?.data?.error || 'Cannot reach the server. Check the connection in Settings.'
    : e instanceof Error
      ? e.message
      : 'Something went wrong. Please try again.';
}

