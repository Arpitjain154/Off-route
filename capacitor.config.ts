import type { CapacitorConfig } from '@capacitor/cli';
const config: CapacitorConfig = {
  appId: 'com.idr.navigator',
  appName: 'Offroute',
  webDir: 'dist',
  android: { backgroundColor: '#0B0E14' },
  server: { androidScheme: 'https' },
};
export default config;
