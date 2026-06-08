import { Component, type ErrorInfo, type ReactNode } from "react";

interface Props {
  children: ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * Catches render/load errors in a route subtree (e.g. a lazy chart page) so one failing
 * route can't blank the entire app, and surfaces the error message on screen for diagnosis.
 */
export default class RouteErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("Route error boundary caught:", error, info);
  }

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;
    return (
      <div
        style={{
          minHeight: "100vh",
          padding: "48px",
          background: "#fff",
          color: "#101828",
          fontFamily: "system-ui, sans-serif",
        }}
      >
        <h1 style={{ fontSize: 20, fontWeight: 800, marginBottom: 8 }}>This page hit an error</h1>
        <p style={{ color: "#b42318", fontWeight: 700, marginBottom: 12 }}>
          {error.name}: {error.message}
        </p>
        <pre
          style={{
            whiteSpace: "pre-wrap",
            fontSize: 12,
            color: "#475467",
            background: "#f6f8fb",
            border: "1px solid #e4e9f2",
            borderRadius: 8,
            padding: 16,
            maxHeight: "50vh",
            overflow: "auto",
          }}
        >
          {error.stack}
        </pre>
      </div>
    );
  }
}
