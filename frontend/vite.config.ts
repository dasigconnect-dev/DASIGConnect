import path from "path";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig, type Plugin } from "vite";

const securityHeaders = {
  "Content-Security-Policy": [
    "default-src 'self'",
    "script-src 'self' 'unsafe-inline'",
    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdn.jsdelivr.net",
    "img-src 'self' data: blob: https:",
    "font-src 'self' data: https://fonts.gstatic.com https://cdn.jsdelivr.net",
    "connect-src 'self' http://localhost:8080 https: ws: wss:",
    "frame-ancestors 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "object-src 'none'",
  ].join("; "),
  "X-Frame-Options": "DENY",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "strict-origin-when-cross-origin",
  "Permissions-Policy": "camera=(), microphone=(), geolocation=(), payment=(), usb=()",
  "Cross-Origin-Opener-Policy": "same-origin",
  "Cross-Origin-Resource-Policy": "same-origin",
};

const blockedDevPaths = new Set([
  "/.env",
  "/.env.example",
  "/.env.local",
  "/.env.development",
  "/.env.production",
  "/.gitignore",
  "/README.md",
  "/api/token",
  "/api/tokens",
  "/api/secret",
  "/api/secrets",
  "/api/internal",
  "/api/private",
  "/api/console",
  "/api/actuator",
  "/api-docs",
  "/api-docs/",
  "/api-docs/swagger.json",
  "/api-gateway/swagger/",
  "/api.php",
  "/package.json",
  "/package-lock.json",
  "/vite.config.js",
  "/vite.config.ts",
  "/tsconfig.json",
]);

function blockSensitiveDevFiles(): Plugin {
  function middleware(req: import("node:http").IncomingMessage, res: import("node:http").ServerResponse, next: () => void) {
    const pathname = req.url ? new URL(req.url, "http://localhost").pathname : "";
    if (!blockedDevPaths.has(pathname)) {
      next();
      return;
    }

    res.statusCode = 404;
    Object.entries(securityHeaders).forEach(([key, value]) => {
      res.setHeader(key, value);
    });
    res.setHeader("Content-Type", "text/plain; charset=utf-8");
    res.end("Not found");
  }

  return {
    name: "block-sensitive-dev-files",
    configureServer(server) {
      server.middlewares.use(middleware);
    },
    configurePreviewServer(server) {
      server.middlewares.use(middleware);
    },
  };
}

export default defineConfig({
  plugins: [blockSensitiveDevFiles(), react(), tailwindcss()],
  server: {
    headers: securityHeaders,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  preview: {
    headers: securityHeaders,
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
