import { Component, type ErrorInfo, type ReactNode } from "react";
import { useLocation } from "react-router-dom";
import ErrorPage from "./ErrorPage";

interface BoundaryProps {
  children: ReactNode;
  /** Changing this clears a caught error — the route path, so navigating away recovers. */
  resetKey: string;
}

interface BoundaryState {
  error: Error | null;
  componentStack: string | null;
}

/**
 * Catches render errors anywhere in the app so one broken component shows a
 * recoverable error page instead of blanking the whole screen.
 */
class ErrorBoundary extends Component<BoundaryProps, BoundaryState> {
  state: BoundaryState = { error: null, componentStack: null };

  static getDerivedStateFromError(error: Error): Partial<BoundaryState> {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    this.setState({ componentStack: info.componentStack ?? null });
    console.error("Unhandled render error", error, info.componentStack);
  }

  componentDidUpdate(previous: BoundaryProps) {
    if (this.state.error && previous.resetKey !== this.props.resetKey) {
      this.setState({ error: null, componentStack: null });
    }
  }

  render() {
    const { error, componentStack } = this.state;
    if (!error) return this.props.children;

    const details = [
      `${error.name}: ${error.message}`,
      `Page: ${window.location.pathname}`,
      `Time: ${new Date().toISOString()}`,
      componentStack ? `Component stack:${componentStack.split("\n").slice(0, 8).join("\n")}` : null,
    ]
      .filter(Boolean)
      .join("\n");

    return (
      <ErrorPage
        variant="crash"
        title="Something went wrong on this page"
        message={
          <p>
            An unexpected error stopped this page from loading. Anything you already saved is safe — reload to try
            again. If it keeps happening, send the technical details below to your administrator.
          </p>
        }
        actions={[
          { label: "Reload page", icon: "ti-refresh", onClick: () => window.location.reload() },
          {
            label: "Go to dashboard",
            icon: "ti-layout-dashboard",
            tone: "ghost",
            // Full navigation: the in-memory state that crashed is discarded.
            onClick: () => window.location.assign("/dashboard"),
          },
        ]}
        details={details}
      />
    );
  }
}

export default function AppErrorBoundary({ children }: { children: ReactNode }) {
  const location = useLocation();
  return <ErrorBoundary resetKey={location.pathname}>{children}</ErrorBoundary>;
}
