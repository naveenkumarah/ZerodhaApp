import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Forwards /api to the Spring Boot backend so the browser never makes cross-origin calls.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.BACKEND_URL || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});

