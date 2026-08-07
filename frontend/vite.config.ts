/// <reference types="vitest/config" />
import { defineConfig } from 'vitest/config';
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
  test: {
    // Realtime-core tests (M9.B) are plain TypeScript; the component tests (M9.C)
    // switch to jsdom per-file with a `// @vitest-environment jsdom` pragma.
    environment: 'node',
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    setupFiles: ['./src/test/setup.ts'],
    clearMocks: true,
  },
});
