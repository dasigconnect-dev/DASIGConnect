import "../../styles/dasig-loader.css";

export default function PageLoader() {
  return (
    <div
      className="dc-page-loader"
      role="status"
      aria-label="Loading"
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 2000,
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
