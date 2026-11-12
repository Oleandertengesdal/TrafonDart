import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import pkg from "./package.json";

// In development the Vite server proxies the API and the live socket to Spring Boot on :8080,
// so the browser only ever talks to one origin – exactly like production behind the Vercel rewrite.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  // The app version goes along with error reports, so Sentry can tell which release broke.
  define: { __APP_VERSION__: JSON.stringify(pkg.version) },
  server: {
    port: 5173,
    proxy: {
      "/api": { target: "http://localhost:8080", changeOrigin: false },
      "/ws": { target: "ws://localhost:8080", ws: true },
    },
  },
  build: {
    sourcemap: false,
    chunkSizeWarningLimit: 900,
  },
});
