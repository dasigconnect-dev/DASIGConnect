import { Navigate } from "react-router-dom";
import type { User, UserRole } from "../../types/auth.types";
import ForbiddenPage from "./ForbiddenPage";

interface Props {
  user: User | null;
  allowedRoles: UserRole[];
  children: React.ReactNode;
  /** Where a signed-out visitor goes (useAuthSession.signedOutRedirect; "/" right after signing out). */
  signedOutRedirect?: string;
}

export default function ProtectedRoute({ user, allowedRoles, children, signedOutRedirect = "/login" }: Props) {
  if (!user) return <Navigate to={signedOutRedirect} replace />;
  // Explain the denial instead of silently bouncing to the dashboard.
  if (!allowedRoles.includes(user.role)) return <ForbiddenPage allowedRoles={allowedRoles} />;
  return <>{children}</>;
}
