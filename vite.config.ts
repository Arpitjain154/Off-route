import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({
  plugins: [react()],
  // MapLibre 6 loads its worker relative to its module. Prebundling moves the
  // module without its worker, leaving DOM labels visible but vector tiles blank.
  optimizeDeps: { exclude: ['maplibre-gl'] },
  worker: { format: 'es' },
  server: {
    port: 5173,
    proxy: { '/api': 'http://127.0.0.1:3001' },
    watch: { ignored: ['**/android/**','**/data/**','**/artifacts/**','**/tmp/**','**/.venv*/**'] },
  },
  build: { chunkSizeWarningLimit: 1100 },
});
