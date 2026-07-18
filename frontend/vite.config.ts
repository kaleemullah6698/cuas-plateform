import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'node:path';

// ARCHITECTURE.md section 3.1: Vite SPA, not Next.js.
// The console is authenticated, real-time, and single-tenant; SSR adds no value
// and complicates WebSocket lifecycle, CSP, and air-gapped deployment.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: { port: 3000 },
});
