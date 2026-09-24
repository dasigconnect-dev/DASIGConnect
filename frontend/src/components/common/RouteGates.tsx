import { Outlet } from "react-router-dom";
import type { User, UserRole } from "../../types/auth.types";
import ProtectedRoute from "./ProtectedRoute";

/**
 * Layout-route access gates. Each wraps a group of routes and renders them
 * through <Outlet />, so a page's access level comes from where it sits in
 * the router instead of an `allowedRoles` array repeated on every route.
 * Named by access level, not role — Admins pass the reviewer gate, and every
 * signed-in role passes the signed-in gate. Denial behaviour is
 * ProtectedRoute's: signed out → `signedOutRedirect` (/login, or "/" right
 * after signing out), wrong role → ForbiddenPage.
 *
 * UI only: the backend's @PreAuthorize on each endpoint is the real check.
 */

const SIGNED_IN: UserRole[] = ["contributor", "moderator", "admin"];
const REVIEWERS: UserRole[] = ["moderator", "admin"];
const ADMINS: UserRole[] = ["admin"];

interface GateProps {
  user: User | null;
  signedOutRedirect?: string;
}

/** Contributor, Moderator, Admin. */
export function RequireSignedIn({ user, signedOutRedirect }: GateProps) {
  return (
    <ProtectedRoute user={user} allowedRoles={SIGNED_IN} signedOutRedirect={signedOutRedirect}>
      <Outlet />
    </ProtectedRoute>
  );
}

/** Moderator, Admin — the network-wide reviewing roles. */
export function RequireReviewer({ user, signedOutRedirect }: GateProps) {
  return (
    <ProtectedRoute user={user} allowedRoles={REVIEWERS} signedOutRedirect={signedOutRedirect}>
      <Outlet />
    </ProtectedRoute>
  );
}

/** Admin only. */
export function RequireAdmin({ user, signedOutRedirect }: GateProps) {
  return (
    <ProtectedRoute user={user} allowedRoles={ADMINS} signedOutRedirect={signedOutRedirect}>
      <Outlet />
    </ProtectedRoute>
  );
}
