import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Proxy /api -> backend Spring Boot (port 4000): frontend goi duong dan tuong doi,
// khong dinh CORS, deploy that chi can dat reverse proxy tuong tu.
export default defineConfig({
  plugins: [react()],
  base: '/class-web-management/',
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.js',
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:4000',
    },
  },
});
