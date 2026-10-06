import { Capacitor, registerPlugin } from '@capacitor/core';
import { useEffect, useState, useCallback } from 'react';
import type { CloudEntry } from './cloudState';
export type CloudStatus = {
  connected: boolean;
  keyConfigured: boolean;
  email: string;
  url: string;
  bucket: string;
  consent: boolean;
  wifiOnly: boolean;
  queue: CloudEntry[];
  modelMessage: string;
};
interface CloudBridge {
  configure(o: { url: string; key: string; bucket: string }): Promise<void>;
  signIn(o: { email: string; password: string }): Promise<{ message: string }>;
  signUp(o: { email: string; password: string }): Promise<{ message: string }>;
  signOut(): Promise<void>;
  setPreferences(o: { consent: boolean; wifiOnly: boolean }): Promise<void>;
  getStatus(): Promise<CloudStatus>;
  enqueueTrip(o: { sessionId: string }): Promise<void>;
  retryTrip(o: { sessionId: string }): Promise<void>;
  checkModelUpdates(): Promise<void>;
  applyPendingModels(): Promise<void>;
}
export const Cloud = registerPlugin<CloudBridge>('CloudPlugin');
export const initialCloud: CloudStatus = {
  connected: false,
  keyConfigured: false,
  email: '',
  url: 'https://bepfbvpwdguupquuthoe.supabase.co',
  bucket: 'journeys',
  consent: false,
  wifiOnly: true,
  queue: [],
  modelMessage: 'No cloud model checked yet',
};
export function useCloud() {
  const [status, setStatus] = useState(initialCloud),
    [error, setError] = useState('');
  const refresh = useCallback(async () => {
    if (!Capacitor.isNativePlatform()) return;
    try {
      setStatus(await Cloud.getStatus());
      setError('');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Cloud status unavailable');
    }
  }, []);
  useEffect(() => {
    void refresh();
    const timer = setInterval(() => {
      if (!document.hidden) void refresh();
    }, 5000);
    const resume = () => void refresh();
    document.addEventListener('visibilitychange', resume);
    return () => {
      clearInterval(timer);
      document.removeEventListener('visibilitychange', resume);
    };
  }, [refresh]);
  return { status, error, refresh };
}
