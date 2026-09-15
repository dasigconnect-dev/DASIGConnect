import { useEffect } from "react";
import { useLocation } from "react-router-dom";
import "../../styles/dasig-loader.css";
import { beginLoadingInterval, endLoadingInterval } from "../../lib/performanceTelemetry";

interface PageLoaderProps {
  contained?: boolean;
}

export default function PageLoader({ contained = false }: PageLoaderProps) {
  const location = useLocation();

  useEffect(() => {
    const timer = beginLoadingInterval(location.pathname, contained ? "contained" : "page");
    return () => endLoadingInterval(timer);
  }, [contained, location.pathname]);

  return (
    <div
      className="dc-page-loader"
      role="status"
      aria-label="Loading"
      style={{
        position: contained ? "relative" : "fixed",
        inset: contained ? undefined : 0,
        zIndex: contained ? undefined : 2000,
        width: "100%",
        minHeight: contained ? "calc(100vh - 58px)" : undefined,
        background: "#F8FAFC",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <div className="dc-dot-triangle-container">
        {/* Loading label positioned above the loader */}
        <div className="dc-dot-triangle-label">
          <span>Loading</span>
          <span className="dc-dot-triangle-label-dots">
            <span className="dc-dot-triangle-dot-char">.</span>
            <span className="dc-dot-triangle-dot-char">.</span>
            <span className="dc-dot-triangle-dot-char">.</span>
          </span>
        </div>

        {/* 1. Dot Triangle Element */}
        <div className="loader-stage" style={{ display: "flex", alignItems: "center", justifyContent: "center" }}>
          <div className="loader-dots" />
        </div>
      </div>
    </div>
  );
}
