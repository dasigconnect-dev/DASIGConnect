import path from "path";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig, type Plugin, type UserConfig } from "vite";

const baseSecurityHeaders = {
  "X-Frame-Options": "DENY",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "strict-origin-when-cross-origin",
  "Permissions-Policy": "camera=(), microphone=(), geolocation=(), payment=(), usb=()",
  "Cross-Origin-Opener-Policy": "same-origin",
  "Cross-Origin-Resource-Policy": "same-origin",
};

// Loosened for the dev server only: HMR needs inline scripts + ws:, the local
// backend is served over plain http on :8080, and dev tooling may create blob
// workers. Keep this narrower than script-src; do not add unsafe-eval.
const devSecurityHeaders = {
  "Content-Security-Policy": [
    "default-src 'self'",
    "script-src 'self' 'unsafe-inline'",
    "worker-src 'self' blob:",
    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdn.jsdelivr.net",
    "img-src 'self' data: blob: https:",
    "font-src 'self' data: https://fonts.gstatic.com https://cdn.jsdelivr.net",
    "connect-src 'self' http://localhost:8080 https: ws: wss:",
    "frame-ancestors 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "object-src 'none'",
  ].join("; "),
  ...baseSecurityHeaders,
};

// Mirrors frontend/vercel.json exactly so `vite preview` is a faithful
// production run and header scans / CSP breakages surface locally.
const prodSecurityHeaders = {
  "Content-Security-Policy": [
    "default-src 'self'",
    "script-src 'self'",
    "worker-src 'self'",
    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdn.jsdelivr.net",
    "img-src 'self' data: blob: https:",
    "font-src 'self' data: https://fonts.gstatic.com https://cdn.jsdelivr.net",
    "connect-src 'self' https:",
    "frame-ancestors 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "object-src 'none'",
  ].join("; "),
  ...baseSecurityHeaders,
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

// Sensitive basenames that must never be served regardless of the route used
// to request them — including Vite's filesystem route (/@fs/<abs path>) and
// query suffixes like ?raw / ?import, which the pathname check below ignores.
function isBlockedBasename(basename: string): boolean {
  if (basename === ".env" || basename.startsWith(".env.")) return true;
  if (basename === ".gitignore") return true;
  return false;
}

function isBlockedRequest(pathname: string): boolean {
  if (blockedDevPaths.has(pathname)) return true;
  let basename = pathname.split("/").pop() ?? "";
  try {
    basename = decodeURIComponent(basename);
  } catch {
    // malformed escape sequence — fall through with the raw segment
  }
  return isBlockedBasename(basename);
}

function blockSensitiveDevFiles(): Plugin {
  function makeMiddleware(headers: Record<string, string>) {
    return function middleware(req: import("node:http").IncomingMessage, res: import("node:http").ServerResponse, next: () => void) {
      const pathname = req.url ? new URL(req.url, "http://localhost").pathname : "";
      if (!isBlockedRequest(pathname)) {
        next();
        return;
      }

      res.statusCode = 404;
      Object.entries(headers).forEach(([key, value]) => {
        res.setHeader(key, value);
      });
      res.setHeader("Content-Type", "text/plain; charset=utf-8");
      res.end("Not found");
    };
  }

  return {
    name: "block-sensitive-dev-files",
    configureServer(server) {
      server.middlewares.use(makeMiddleware(devSecurityHeaders));
    },
    configurePreviewServer(server) {
      server.middlewares.use(makeMiddleware(prodSecurityHeaders));
    },
  };
}

function stripProductionDebugOutput(): Plugin {
  const consoleReferencePattern =
    /\bconsole\.(?:debug|log|info|warn|error|trace|table|group|groupCollapsed|groupEnd)\b/g;

  return {
    name: "strip-production-debug-output",
    apply: "build",
    enforce: "post",
    transform(code, id) {
      if (!/\.[cm]?[jt]sx?$/.test(id) || id.includes("node_modules")) {
        return null;
      }
      const stripped = code
        .split(/\r?\n/)
        .filter((line) => {
          const trimmed = line.trim();
          return !trimmed.startsWith("debugger") && !trimmed.startsWith("console.");
        })
        .join("\n");
      return stripped === code ? null : { code: stripped, map: null };
    },
    renderChunk(code) {
      const stripped = code
        .replace(/\bdebugger;?/g, "")
        .replace(consoleReferencePattern, "(() => {})");
      return stripped === code ? null : { code: stripped, map: null };
    },
  };
}

export default defineConfig(() => {
  const config: UserConfig = {
    plugins: [blockSensitiveDevFiles(), stripProductionDebugOutput(), react(), tailwindcss()],
    server: {
      headers: devSecurityHeaders,
      proxy: {
        "/api": {
          target: "http://localhost:8080",
          changeOrigin: true,
        },
      },
    },
    preview: {
      headers: prodSecurityHeaders,
    },
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "./src"),
      },
    },
  };
  return config;
});
