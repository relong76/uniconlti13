import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import vuetify from "vite-plugin-vuetify";

// the server serves the built app from static/app, at /app/
export default defineConfig({
  base: "./",
  plugins: [vue(), vuetify({ autoImport: true })],
  build: {
    outDir: "../resources/static/app",
    emptyOutDir: true,
    target: "esnext",
    reportCompressedSize: false
  },
  server: {
    // `npm run dev` against the tool running locally (mvn spring-boot:run, or F5 in VS Code)
    proxy: {
      "/api": { target: "https://localhost:9090", secure: false, changeOrigin: true },
      "/config": { target: "https://localhost:9090", secure: false, changeOrigin: true }
    }
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./tests/setup.js"],
    server: { deps: { inline: ["vuetify"] } },
    coverage: {
      provider: "v8",
      include: ["src/**/*.{js,vue}"],
      exclude: ["src/main.js"],
      reporter: ["text", "lcov"],
      thresholds: { lines: 95, branches: 95, functions: 95, statements: 95 }
    }
  }
});
