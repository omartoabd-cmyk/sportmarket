import { loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vitest/config";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const target = env.API_PROXY_TARGET || "http://localhost:8080";
  const proxy: Record<string, import("vite").ProxyOptions> = {
    "/api": {
      target,
      changeOrigin: true,
      bypass(request) {
        // Browser calls are same-origin with Vite. Match Origin to the
        // backend Host before forwarding; changeOrigin only rewrites Host.
        if (request.headers.origin) {
          request.headers.origin = new URL(target).origin;
        }
      },
    },
  };
  return {
    plugins: [react(), tailwindcss()],
    server: { port: 5173, strictPort: true, proxy },
    preview: { port: 4173, strictPort: true, proxy },
    test: {
      environment: "jsdom",
      setupFiles: "./src/test/setup.ts",
      clearMocks: true,
    },
  };
});
