import axios from "axios";

const BASE_URL = import.meta.env.VITE_API_URL || "/api/v1";
const ADMIN_BASE_URL = BASE_URL.replace(/\/v1$/, "");

export const api = axios.create({ baseURL: BASE_URL });
export const adminApi = axios.create({ baseURL: ADMIN_BASE_URL });

export function setAuthToken(token: string | null) {
  if (token) {
    api.defaults.headers.common.Authorization = `Bearer ${token}`;
    adminApi.defaults.headers.common.Authorization = `Bearer ${token}`;
  } else {
    delete api.defaults.headers.common.Authorization;
    delete adminApi.defaults.headers.common.Authorization;
  }
}

export interface LoginResponse {
  accessToken: string;
  role: string;
  institutionId: string | null;
}

export interface UserProfileResponse {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  displayName: string | null;
  role: string;
  accountState: string;
  institutionId: string | null;
  institutionName: string | null;
  createdAt: string;
}

export function login(email: string, password: string) {
  return api.post<LoginResponse>("/auth/login", { email, password });
}

export function logout() {
  return api.post("/auth/logout");
}

export function requestPasswordReset(email: string) {
  return api.post("/auth/forgot-password", { email });
}

export function resetPassword(token: string, newPassword: string) {
  return api.post("/auth/reset-password", { token, newPassword });
}

export interface InvitationValidateResponse {
  recipientEmail: string;
  assignedRole: string;
  institutionName: string;
  expiresAt: string;
}

export function validateInvitation(token: string) {
  return api.get<InvitationValidateResponse>("/invitations/validate", {
    params: { token },
  });
}

export interface AcceptInvitationPayload {
  token: string;
  firstName: string;
  lastName: string;
  password: string;
}

export function acceptInvitation(payload: AcceptInvitationPayload) {
  return api.post<LoginResponse>("/invitations/accept", payload);
}

export function getMe() {
  return api.get<UserProfileResponse>("/me");
}

export interface InstitutionResponse {
  id: string;
  name: string;
  institutionCode: string;
  status: string;
  emailDomain: string;
}

export function createInstitution(
  name: string,
  institutionCode: string,
  emailDomain: string,
) {
  return api.post<InstitutionResponse>("/institutions", {
    name,
    institutionCode,
    emailDomain,
  });
}

export function listInstitutions() {
  return api.get<InstitutionResponse[]>("/institutions");
}

export function deleteInstitution(id: string) {
  return api.delete(`/institutions/${id}`);
}

export function getUserCounts(institutionId: string) {
  return api.get<{ contributors: number; validators: number }>(
    "/users/counts",
    {
      params: { institutionId },
    },
  );
}

export function listUsers(institutionId: string) {
  return api.get<UserProfileResponse[]>("/users", {
    params: { institutionId },
  });
}

export function updateUserStatus(
  id: string,
  accountState: "active" | "inactive",
) {
  return api.patch<UserProfileResponse>(`/users/${id}/status`, {
    accountState,
  });
}

export interface PendingInvitationResponse {
  id: string;
  recipientEmail: string;
  assignedRole: string;
  institutionId: string;
  expiresAt: string;
  createdAt: string;
}

export function listPendingInvitations(institutionId: string) {
  return api.get<PendingInvitationResponse[]>("/invitations/pending", {
    params: { institutionId },
  });
}

export function getPendingInvitationCount(institutionId: string) {
  return api.get<{ pendingInvitations: number }>("/invitations/pending/count", {
    params: { institutionId },
  });
}

export function resendInvitation(id: string) {
  return api.post<InvitationResponse>(`/invitations/${id}/resend`);
}

export interface InviteUserRequest {
  recipientEmail: string;
  institutionId: string;
  assignedRole: "contributor" | "validator";
}

export function inviteUser(data: InviteUserRequest) {
  return api.post<InvitationResponse>("/invitations", data);
}

export function deleteUser(id: string) {
  return api.delete<{ action: 'deactivated' | 'deleted' }>(`/users/${id}`);
}

export function cancelInvitation(id: string) {
  return api.delete(`/invitations/${id}`);
}

export interface InvitationResponse {
  id: string;
  recipientEmail: string;
  assignedRole: string;
  institutionId: string;
  expiresAt: string;
  createdAt: string;
  emailDelivered: boolean;
  invitationUrl: string;
}
