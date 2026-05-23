import { Navigate } from "react-router-dom";
import type { User, UserRole } from "../../types/auth.types";

interface Props {
  user: User | null;
  allowedRoles: UserRole[];
  children: React.ReactNode;
}

export default function ProtectedRoute({ user, allowedRoles, children }: Props) {
  if (!user) return <Navigate to="/login" replace />;
  if (!allowedRoles.includes(user.role)) return <Navigate to="/dashboard" replace />;
  return <>{children}</>;
}
