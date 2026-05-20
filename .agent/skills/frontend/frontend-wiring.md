---
name: frontend-wiring
description: >
  Wire React components or any frontend UI to an existing backend API. Use this skill whenever
  the user has a backend already built (REST, GraphQL, WebSocket, or otherwise) and wants to
  connect it to a React component, HTML page, or frontend app. Triggers include: "connect my
  frontend to the backend", "wire up the API", "integrate the endpoints", "fetch data from my
  API", "hook up the form to the server", "make this component call the API", "the backend is
  ready, now do the frontend", "add API calls to my component", or any time the user provides
  backend routes/endpoints and asks you to build or update frontend code against them. Also
  trigger when the user shows you a backend (Express, FastAPI, Django, NestJS, etc.) and asks
  you to "make the UI work with it". This skill covers auth headers, error handling, loading
  states, data mapping, optimistic updates, real-time subscriptions, and everything else needed
  for a production-quality frontend-to-backend integration.
---

# Frontend Wiring Skill

Connect a React (or plain HTML/JS) frontend to an **already-built backend**. The backend is the
source of truth. Your job is to read it carefully, then produce frontend code that integrates
with it cleanly, robustly, and idiomatically.

---

## Phase 0 — Read the Backend First

Before writing a single line of frontend code, extract every piece of information you need from
the backend the user provides. Never assume or guess; if something is ambiguous, ask.

### What to extract

| Signal | Where to look |
|---|---|
| Base URL / host | Environment config, README, or ask the user |
| Auth scheme | Middleware, route guards, token usage (`Bearer`, `Cookie`, `API-Key`) |
| All relevant endpoints | Route files, controllers, OpenAPI/Swagger spec |
| Request shape | Body schema, query params, path params, content-type |
| Response shape | Return types, serializers, example payloads |
| Error format | Error-handling middleware, status codes returned |
| Pagination style | `page`/`limit`, `cursor`, `offset`, or Link headers |
| Real-time | WebSocket events, SSE endpoints, polling intervals |
| CORS settings | `cors()` config — what origins are allowed |
| Rate limits | Headers like `X-RateLimit-*` or explicit middleware |

### Checklist before proceeding

- [ ] I know the base URL (or where it comes from at runtime)
- [ ] I know every endpoint the component will call
- [ ] I know the exact request body/params for each endpoint
- [ ] I know the success response shape for each endpoint
- [ ] I know the error response shape (status codes + body)
- [ ] I know the auth mechanism
- [ ] I know if any endpoint is paginated or real-time

If any item is unknown, ask the user before continuing.

---

## Phase 1 — Project Setup

### 1.1 API Client Foundation

Always create a **single, centralized API client** rather than scattering `fetch` calls across
components. This makes auth, base URLs, error handling, and interceptors manageable.

#### Option A — Plain `fetch` wrapper (no extra deps)

```typescript
// src/lib/api.ts
const BASE_URL = import.meta.env.VITE_API_URL ?? "http://localhost:3000";

type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
};

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, headers = {}, signal } = options;

  const token = localStorage.getItem("token"); // or from your auth store
  const authHeaders: Record<string, string> = token
    ? { Authorization: `Bearer ${token}` }
    : {};

  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    signal,
    headers: {
      "Content-Type": "application/json",
      ...authHeaders,
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (!res.ok) {
    // Parse the backend's error shape (adapt to what the backend actually returns)
    const err = await res.json().catch(() => ({ message: res.statusText }));
    throw new ApiError(res.status, err.message ?? "Unknown error", err);
  }

  // Handle 204 No Content
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public data?: unknown
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export const api = {
  get: <T>(path: string, opts?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(path, { ...opts, method: "GET" }),
  post: <T>(path: string, body: unknown, opts?: Omit<RequestOptions, "method">) =>
    request<T>(path, { ...opts, method: "POST", body }),
  put: <T>(path: string, body: unknown, opts?: Omit<RequestOptions, "method">) =>
    request<T>(path, { ...opts, method: "PUT", body }),
  patch: <T>(path: string, body: unknown, opts?: Omit<RequestOptions, "method">) =>
    request<T>(path, { ...opts, method: "PATCH", body }),
  delete: <T>(path: string, opts?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(path, { ...opts, method: "DELETE" }),
};
```

#### Option B — Axios (if already in the project)

```typescript
// src/lib/axios.ts
import axios from "axios";

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? "http://localhost:3000",
  headers: { "Content-Type": "application/json" },
});

// Request interceptor — attach auth token
http.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Response interceptor — normalize errors
http.interceptors.response.use(
  (res) => res,
  (err) => {
    const status = err.response?.status;
    const message = err.response?.data?.message ?? err.message;
    if (status === 401) {
      // e.g., redirect to login, clear token
      localStorage.removeItem("token");
      window.location.href = "/login";
    }
    return Promise.reject(new Error(`[${status}] ${message}`));
  }
);
```

#### Option C — TanStack Query (React Query) setup

```typescript
// src/lib/queryClient.ts
import { QueryClient } from "@tanstack/react-query";

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60, // 1 minute
      retry: (failureCount, error: unknown) => {
        // Don't retry on 4xx errors
        if (error instanceof ApiError && error.status < 500) return false;
        return failureCount < 2;
      },
    },
  },
});
```

### 1.2 TypeScript Types — Mirror the Backend

Define types that **exactly match** the backend's response shape. Read the backend's models,
serializers, or OpenAPI schema to get this right.

```typescript
// src/types/api.ts  (example — adapt to the actual backend)

export interface User {
  id: string;
  email: string;
  name: string;
  role: "admin" | "user";
  createdAt: string; // ISO 8601
}

export interface PaginatedResponse<T> {
  data: T[];
  total: number;
  page: number;
  pageSize: number;
  hasNext: boolean;
}

export interface ApiErrorBody {
  message: string;
  errors?: Record<string, string[]>; // field-level validation errors
  code?: string;
}
```

### 1.3 Environment Variables

```env
# .env.local (never commit secrets)
VITE_API_URL=http://localhost:3000/api

# .env.production
VITE_API_URL=https://api.yourapp.com
```

---

## Phase 2 — Data Fetching Patterns

Choose the right pattern based on what the backend endpoint does.

### 2.1 Read-only data — `useQuery` (TanStack) or custom hook

**With TanStack Query (preferred for most apps):**

```typescript
// src/hooks/useUsers.ts
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { PaginatedResponse, User } from "@/types/api";

export function useUsers(page = 1, pageSize = 20) {
  return useQuery({
    queryKey: ["users", page, pageSize],
    queryFn: ({ signal }) =>
      api.get<PaginatedResponse<User>>(
        `/users?page=${page}&pageSize=${pageSize}`,
        { signal }
      ),
    placeholderData: (prev) => prev, // keep previous data while fetching next page
  });
}
```

**Without TanStack Query (plain hook):**

```typescript
// src/hooks/useFetch.ts
import { useState, useEffect, useRef } from "react";
import { api, ApiError } from "@/lib/api";

export function useFetch<T>(path: string | null) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!path) return;
    const controller = new AbortController();
    setLoading(true);
    setError(null);

    api
      .get<T>(path, { signal: controller.signal })
      .then(setData)
      .catch((err) => {
        if (err.name !== "AbortError") {
          setError(err instanceof ApiError ? err.message : "Request failed");
        }
      })
      .finally(() => setLoading(false));

    return () => controller.abort();
  }, [path]);

  return { data, loading, error };
}
```

### 2.2 Mutations — POST / PUT / PATCH / DELETE

**With TanStack Query:**

```typescript
// src/hooks/useCreateUser.ts
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { User } from "@/types/api";

interface CreateUserPayload {
  name: string;
  email: string;
  role: "admin" | "user";
}

export function useCreateUser() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreateUserPayload) =>
      api.post<User>("/users", payload),

    onSuccess: (newUser) => {
      // Invalidate the users list so it refetches
      queryClient.invalidateQueries({ queryKey: ["users"] });
      // Or optimistically insert:
      // queryClient.setQueryData(["users", ...], (old) => ...)
    },

    onError: (err) => {
      console.error("Create user failed:", err);
    },
  });
}
```

**In the component:**

```tsx
function CreateUserForm() {
  const { mutate, isPending, isError, error } = useCreateUser();

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    mutate({
      name: fd.get("name") as string,
      email: fd.get("email") as string,
      role: "user",
    });
  }

  return (
    <form onSubmit={handleSubmit}>
      <input name="name" required />
      <input name="email" type="email" required />
      {isError && <p className="error">{(error as Error).message}</p>}
      <button type="submit" disabled={isPending}>
        {isPending ? "Creating…" : "Create User"}
      </button>
    </form>
  );
}
```

### 2.3 Infinite / Cursor Pagination

```typescript
// src/hooks/useInfiniteItems.ts
import { useInfiniteQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";

export function useInfiniteItems() {
  return useInfiniteQuery({
    queryKey: ["items", "infinite"],
    queryFn: ({ pageParam, signal }) =>
      api.get<{ items: Item[]; nextCursor: string | null }>(
        `/items?cursor=${pageParam ?? ""}`,
        { signal }
      ),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
  });
}
```

---

## Phase 3 — Authentication Integration

Match the exact auth scheme the backend uses.

### 3.1 JWT — Bearer Token

```typescript
// src/lib/auth.ts
export const TOKEN_KEY = "auth_token";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}
```

```typescript
// src/hooks/useLogin.ts
import { useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { setToken } from "@/lib/auth";

interface LoginPayload { email: string; password: string }
interface LoginResponse { token: string; user: User }

export function useLogin() {
  return useMutation({
    mutationFn: (payload: LoginPayload) =>
      api.post<LoginResponse>("/auth/login", payload),
    onSuccess: ({ token }) => {
      setToken(token);
      window.location.href = "/dashboard";
    },
  });
}
```

### 3.2 HTTP-Only Cookie (Session / Refresh Token)

```typescript
// When the backend uses HttpOnly cookies, just add credentials: "include"
// The browser automatically sends the cookie — no manual token handling.

const res = await fetch(`${BASE_URL}${path}`, {
  credentials: "include", // ← key line
  headers: { "Content-Type": "application/json" },
  ...
});
```

### 3.3 Token Refresh (Silent Refresh)

```typescript
// src/lib/api.ts — add refresh logic around the request()
let isRefreshing = false;
let refreshQueue: Array<(token: string) => void> = [];

async function refreshAccessToken(): Promise<string> {
  const res = await fetch(`${BASE_URL}/auth/refresh`, {
    method: "POST",
    credentials: "include",
  });
  if (!res.ok) throw new Error("Session expired");
  const { token } = await res.json();
  setToken(token);
  return token;
}

// In your request() function, catch 401 and retry once:
if (res.status === 401 && !options._retry) {
  if (!isRefreshing) {
    isRefreshing = true;
    const newToken = await refreshAccessToken().finally(() => {
      isRefreshing = false;
    });
    refreshQueue.forEach((fn) => fn(newToken));
    refreshQueue = [];
  }
  return request<T>(path, { ...options, _retry: true });
}
```

---

## Phase 4 — Error Handling

### 4.1 Error boundary for unexpected failures

```tsx
// src/components/ErrorBoundary.tsx
import { Component, type ReactNode } from "react";

interface Props { fallback: ReactNode; children: ReactNode }
interface State { error: Error | null }

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  render() {
    if (this.state.error) return this.props.fallback;
    return this.props.children;
  }
}
```

### 4.2 Field-level validation errors from the backend

If the backend returns structured validation errors like:
```json
{ "errors": { "email": ["already taken"], "name": ["too short"] } }
```

Map them to form fields:

```tsx
function FormWithErrors() {
  const { mutate, error } = useCreateUser();
  const fieldErrors = (error as ApiError)?.data?.errors ?? {};

  return (
    <form>
      <div>
        <input name="email" />
        {fieldErrors.email?.map((msg) => (
          <span key={msg} className="field-error">{msg}</span>
        ))}
      </div>
    </form>
  );
}
```

### 4.3 Global error toast / notification

```tsx
// src/hooks/useMutation.ts — wrapper that auto-toasts on error
import { useMutation as useRQMutation, type UseMutationOptions } from "@tanstack/react-query";
import { toast } from "sonner"; // or your toast library

export function useMutation<TData, TError, TVariables>(
  options: UseMutationOptions<TData, TError, TVariables>
) {
  return useRQMutation({
    ...options,
    onError: (err, vars, ctx) => {
      toast.error((err as Error).message ?? "Something went wrong");
      options.onError?.(err, vars, ctx);
    },
  });
}
```

---

## Phase 5 — Loading & UI States

Every API call must show three states: **loading**, **error**, **success**.

```tsx
function UserList() {
  const { data, isLoading, isError, error } = useUsers();

  if (isLoading) {
    return (
      <div className="skeleton-list">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="skeleton-row" />
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <div className="error-state">
        <p>{(error as Error).message}</p>
        <button onClick={() => window.location.reload()}>Retry</button>
      </div>
    );
  }

  return (
    <ul>
      {data?.data.map((user) => (
        <li key={user.id}>{user.name}</li>
      ))}
    </ul>
  );
}
```

### Optimistic Updates

Use when the mutation is very likely to succeed and UX responsiveness matters:

```typescript
onMutate: async (newItem) => {
  await queryClient.cancelQueries({ queryKey: ["items"] });
  const previous = queryClient.getQueryData(["items"]);
  queryClient.setQueryData(["items"], (old: Item[]) => [
    ...old,
    { ...newItem, id: "temp-" + Date.now(), optimistic: true },
  ]);
  return { previous }; // rollback context
},
onError: (err, newItem, ctx) => {
  queryClient.setQueryData(["items"], ctx?.previous);
},
onSettled: () => {
  queryClient.invalidateQueries({ queryKey: ["items"] });
},
```

---

## Phase 6 — Real-Time Connections

### 6.1 WebSocket

```typescript
// src/lib/socket.ts
let socket: WebSocket | null = null;

export function getSocket(): WebSocket {
  if (!socket || socket.readyState === WebSocket.CLOSED) {
    const token = getToken();
    socket = new WebSocket(
      `${import.meta.env.VITE_WS_URL}?token=${token}`
    );
    socket.addEventListener("open", () => console.log("WS connected"));
    socket.addEventListener("close", () => {
      // Reconnect after 3 s
      setTimeout(getSocket, 3000);
    });
  }
  return socket;
}
```

```tsx
// src/hooks/useSocketEvent.ts
import { useEffect } from "react";
import { getSocket } from "@/lib/socket";

export function useSocketEvent<T>(
  event: string,
  handler: (data: T) => void
) {
  useEffect(() => {
    const ws = getSocket();
    const listener = (msg: MessageEvent) => {
      const payload = JSON.parse(msg.data);
      if (payload.event === event) handler(payload.data as T);
    };
    ws.addEventListener("message", listener);
    return () => ws.removeEventListener("message", listener);
  }, [event, handler]);
}
```

### 6.2 Server-Sent Events (SSE)

```typescript
// src/hooks/useSSE.ts
import { useEffect } from "react";

export function useSSE(url: string, onMessage: (data: unknown) => void) {
  useEffect(() => {
    const es = new EventSource(url, { withCredentials: true });
    es.onmessage = (e) => onMessage(JSON.parse(e.data));
    es.onerror = () => es.close();
    return () => es.close();
  }, [url]);
}
```

---

## Phase 7 — File Uploads

```tsx
// src/hooks/useFileUpload.ts
import { useState } from "react";
import { getToken } from "@/lib/auth";

export function useFileUpload(endpoint: string) {
  const [progress, setProgress] = useState(0);
  const [uploading, setUploading] = useState(false);

  async function upload(file: File): Promise<{ url: string }> {
    setUploading(true);
    setProgress(0);

    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      const fd = new FormData();
      fd.append("file", file);

      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) setProgress((e.loaded / e.total) * 100);
      };

      xhr.onload = () => {
        setUploading(false);
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve(JSON.parse(xhr.responseText));
        } else {
          reject(new Error(`Upload failed: ${xhr.status}`));
        }
      };

      xhr.onerror = () => reject(new Error("Network error"));

      xhr.open("POST", `${import.meta.env.VITE_API_URL}${endpoint}`);
      xhr.setRequestHeader("Authorization", `Bearer ${getToken()}`);
      xhr.send(fd);
    });
  }

  return { upload, uploading, progress };
}
```

---

## Phase 8 — CORS & Environment Gotchas

| Problem | Symptom | Fix |
|---|---|---|
| CORS error | `Access-Control-Allow-Origin` missing | Ask user to add your origin to backend CORS config, or proxy in dev |
| Dev proxy | Works in prod but not dev | Add Vite proxy: `server.proxy` in `vite.config.ts` |
| Cookie not sent | Auth broken in prod | Ensure `credentials: "include"` and backend allows credentials |
| HTTPS mismatch | Mixed content error | Both frontend and backend must use HTTPS in production |
| Wrong base URL | 404 on all calls | Check `VITE_API_URL` in `.env.local` vs actual backend port |

### Vite dev proxy (avoid CORS in development)

```typescript
// vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:3000",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ""),
      },
    },
  },
});
```

---

## Phase 9 — Code Organization

```
src/
├── lib/
│   ├── api.ts          # Base fetch wrapper / axios instance
│   ├── auth.ts         # Token storage helpers
│   └── queryClient.ts  # TanStack Query client
├── hooks/
│   ├── useUsers.ts     # One file per resource domain
│   ├── useCreateUser.ts
│   └── useSocketEvent.ts
├── types/
│   └── api.ts          # All backend-mirrored types
├── components/
│   ├── UserList.tsx    # Consumes hooks, renders state
│   └── ErrorBoundary.tsx
└── .env.local          # VITE_API_URL
```

**Rules:**
- No `fetch` or `axios` calls inside component files — always through a hook
- No hardcoded URLs in components — always from `api.ts`
- No raw `any` types — match the backend's actual shape

---

## Phase 10 — Final Checklist

Before handing off, verify every item:

- [ ] **All endpoints wired** — every backend route the feature needs is called
- [ ] **Request shapes match** — field names, types, and nesting exactly match the backend
- [ ] **Response shapes mapped** — TypeScript types reflect actual response
- [ ] **Auth attached** — token or cookie sent on every protected route
- [ ] **Loading states** — skeleton or spinner shown while fetching
- [ ] **Error states** — user-visible message for every failure case, never a silent catch
- [ ] **Abort signals** — all `GET` calls pass `signal` to cancel on unmount
- [ ] **No hardcoded URLs** — base URL from environment variable
- [ ] **Form disabled during submit** — prevents double-submission
- [ ] **Cache invalidated after mutations** — stale data doesn't persist in UI
- [ ] **CORS confirmed** — tested in the target environment, not just assumed

---

## Quick Reference — Common Patterns

```typescript
// GET with query params
api.get<Product[]>(`/products?category=${cat}&limit=10`)

// POST with JSON body
api.post<Order>("/orders", { productId, quantity, userId })

// PATCH partial update
api.patch<User>(`/users/${id}`, { name })

// DELETE and handle 204
api.delete<void>(`/users/${id}`)

// Upload file (use XHR for progress, see Phase 7)
const { upload, progress } = useFileUpload("/uploads")

// Optimistic toggle
queryClient.setQueryData(["todos", id], (old) => ({ ...old, done: !old.done }))
```
