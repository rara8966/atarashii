import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // 直接构建进后端的静态资源目录，避免手动拷贝导致 static/ 用了旧前端
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
        proxyTimeout: 600_000,
        timeout: 600_000
      }
    }
  }
});