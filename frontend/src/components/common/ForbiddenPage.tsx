import { useNavigate } from "react-router-dom";
import type { UserRole } from "../../types/auth.types";
import ErrorPage from "./ErrorPage";

const ROLE_LABELS: Record<UserRole, string> = {
  admin: "Administrators",
  moderator: "Moderators",
  contributor: "Contributors",
};

/** A signed-in user opened a page their role can't use. */
export default function ForbiddenPage({ allowedRoles }: { allowedRoles: UserRole[] }) {
  const navigate = useNavigate();
  const who = allowedRoles.map((role) => ROLE_LABELS[role] ?? role);
  const audience =
    who.length <= 1 ? who.join("") : `${who.slice(0, -1).join(", ")} and ${who[who.length - 1]}`;

  return (
    <ErrorPage
      variant="forbidden"
      layout="in-shell"
      title="You don't have access to this page"
      message={
        <p>
          This area is available to <strong>{audience}</strong> only. If you think you should have access, ask an
          Administrator to review your role.
        </p>
      }
      actions={[
        { label: "Go to dashboard", icon: "ti-layout-dashboard", onClick: () => navigate("/dashboard") },
        { label: "Go back", icon: "ti-arrow-left", tone: "ghost", onClick: () => navigate(-1) },
      ]}
    />
  );
}
