import { Navigate } from "react-router-dom";
import type { User, UserRole } from "../../types/auth.types";
import ForbiddenPage from "./ForbiddenPage";

interface Props {
  user: User | null;
  allowedRoles: UserRole[];
  children: React.ReactNode;
}

export default function ProtectedRoute({ user, allowedRoles, children }: Props) {
  if (!user) return <Navigate to="/login" replace />;
  // Explain the denial instead of silently bouncing to the dashboard.
  if (!allowedRoles.includes(user.role)) return <ForbiddenPage allowedRoles={allowedRoles} />;
  return <>{children}</>;
}
