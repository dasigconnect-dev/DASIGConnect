import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import "../styles/index.css";
import "../styles/submission.css";
import "../styles/validation.css";
import "../styles/user-management.css";
import "../styles/institution-management.css";
import "../styles/calendar.css";
import "../styles/ui.css";
import { ToastProvider } from "../context/ToastContext";
import App from "./App.tsx";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <ToastProvider>
        <App />
      </ToastProvider>
    </BrowserRouter>
  </StrictMode>,
);
