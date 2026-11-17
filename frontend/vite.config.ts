import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In dev, /api is proxied to the Spring Boot app so the browser sees one origin.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    // Listen on the local network too, so a phone on the same Wi-Fi can open the dev site.
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        // The browser only ever talks to this dev server, so drop its Origin header: otherwise the
        // backend's CORS check rejects devices on the LAN (e.g. a phone at http://192.168.x.x:5173).
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'));
        },
      },
    },
  },
});
