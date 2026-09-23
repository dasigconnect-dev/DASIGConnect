import { useLocation, useNavigate } from "react-router-dom";
import ErrorPage from "./ErrorPage";

/** Unknown URL. Full-screen; the buttons depend on whether the user is signed in. */
export default function NotFoundPage({ signedIn }: { signedIn: boolean }) {
  const navigate = useNavigate();
  const location = useLocation();
  const canGoBack = window.history.length > 1;

  return (
    <ErrorPage
      variant="not-found"
      path={location.pathname}
      title="We couldn't find that page"
      message={
        <p>
          The link may be broken, or the page may have been moved or removed. Check the address, or head back to{" "}
          {signedIn ? "your dashboard" : "the home page"}.
        </p>
      }
      actions={[
        signedIn
          ? { label: "Go to dashboard", icon: "ti-layout-dashboard", onClick: () => navigate("/dashboard") }
          : { label: "Go to home page", icon: "ti-home", onClick: () => navigate("/") },
        ...(canGoBack
          ? [{ label: "Go back", icon: "ti-arrow-left", tone: "ghost" as const, onClick: () => navigate(-1) }]
          : []),
      ]}
    />
  );
}
