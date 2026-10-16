import { getMe } from "../../api/authApi";
import type { UserProfileResponse } from "../../api/authApi";
import type { User } from "../../types/auth.types";
import { getUserDisplayName, getUserInitials } from "../../lib/userIdentity";

/** Pure helpers for the auth flows (features/auth/hooks). */

export const TOKEN_STORAGE_KEY = "dasigconnect_token";
export const USER_STORAGE_KEY = "dasigconnect_user";

export function formatTimer(seconds: number) {
  const safeSeconds = Math.max(seconds, 0);
  const minutes = Math.floor(safeSeconds / 60);
  const remaining = safeSeconds % 60;
  return `${minutes.toString().padStart(2, "0")}:${remaining.toString().padStart(2, "0")}`;
}

export function isPasswordResetPath(pathname: string) {
  return pathname === "/reset-password" || pathname === "/forgot-password/reset";
}

function mapApiRole(role: string): User["role"] {
  const normalized = role.toLowerCase();
  if (normalized === "admin" || normalized.includes("super")) return "admin";
  if (normalized === "moderator" || normalized.includes("admin")) return "moderator";
  return "contributor";
}

/** GET /me, returning the raw profile and the app's User built from it. */
export async function loadCurrentUser(email: string, signal?: AbortSignal) {
  const response = await getMe(signal);
  return {
    profile: response.data,
    user: buildUserFromProfile(response.data, email),
  };
}

function buildUserFromProfile(profile: UserProfileResponse, fallbackEmail: string): User {
  const email = (profile.email || fallbackEmail).trim().toLowerCase();
  return {
    id: profile.id,
    email,
    pw: "",
    role: mapApiRole(profile.role),
    name: getUserDisplayName(profile),
    firstName: profile.firstName,
    lastName: profile.lastName,
    displayName: profile.displayName,
    inst: profile.institutionName || institutionFallbackFromEmail(email),
    institutionId: profile.institutionId,
    initials: getUserInitials(profile),
    adminOwner: profile.adminOwner,
  };
}

function institutionFallbackFromEmail(email: string) {
  const emailDomain = email.split("@")[1]?.split(".")[0]?.toLowerCase() || "";
  return emailDomain.toUpperCase() || "Institution";
}

export function formatRoleLabel(role: string) {
  if (!role) return "Contributor";
  const normalized = role.toLowerCase();
  return normalized.charAt(0).toUpperCase() + normalized.slice(1);
}

export function formatExpiry(expiresAt: string) {
  const date = new Date(expiresAt);
  if (Number.isNaN(date.getTime())) return "soon";
  const diff = date.getTime() - Date.now();
  if (diff <= 0) return "soon";
  const hours = Math.floor(diff / (1000 * 60 * 60));
  const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
  return `in ${hours}h ${minutes}m`;
}

/** The JWT's `exp` in milliseconds, or null if it can't be read. */
export function getTokenExpiryMs(token: string) {
  const [, payload] = token.split(".");
  if (!payload) return null;
  try {
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    const claims = JSON.parse(window.atob(padded));
    return typeof claims.exp === "number" ? claims.exp * 1000 : null;
  } catch {
    return null;
  }
}

export function normalizeProfileName(value: string) {
  return value.trim().replace(/\s+/g, " ");
}

export function isValidProfileName(value: string) {
  const normalized = normalizeProfileName(value);
  return normalized.length >= 1 && normalized.length <= 100 && /^[\p{L}][\p{L} '-]*$/u.test(normalized);
}

export function getApiErrorMessage(error: unknown, fallback: string) {
  if (!isRecord(error)) return fallback;
  const response = error.response;
  if (isRecord(response)) {
    const data = response.data;
    if (isRecord(data)) {
      if (typeof data.error === "string") return data.error;
      // ApiResponse envelope: { success, data, error: { code, message } }
      if (isRecord(data.error) && typeof data.error.message === "string") {
        return data.error.message;
      }
      if (typeof data.message === "string") return data.message;
    }
  }
  return typeof error.message === "string" ? error.message : fallback;
}

// Matches the backend reason strings from InvitationService#assertTokenUnused
// and #acceptInvitation ("Invitation has expired", "Invitation has already
// been used", "Account is already active").
export function isExpiredInviteError(message: string) {
  return message.toLowerCase().includes("expired");
}

export function isAlreadyUsedInviteError(message: string) {
  const normalized = message.toLowerCase();
  return normalized.includes("already been used") || normalized.includes("already active");
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
