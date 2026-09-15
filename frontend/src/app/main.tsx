import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import "../styles/index.css";
import "../styles/ui.css";
import { ToastProvider } from "../context/ToastContext";
import { appQueryClient } from "../lib/queryClient";
import { installNavigationStartInstrumentation } from "../lib/performanceTelemetry";
import PerformanceRouteObserver from "../components/common/PerformanceRouteObserver";
import App from "./App.tsx";

installNavigationStartInstrumentation();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={appQueryClient}>
      <BrowserRouter>
        <PerformanceRouteObserver />
        <ToastProvider>
          <App />
        </ToastProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
