import axios from "axios";

const BASE_URL = import.meta.env.VITE_API_URL || "/api/v1";

export const api = axios.create({ baseURL: BASE_URL });

function isEnvelope(body: unknown): body is { success: boolean; data: unknown; error: unknown } {
  return typeof body === "object" && body !== null && typeof (body as { success?: unknown }).success === "boolean";
}

api.interceptors.response.use(
  (response) => {
    if (isEnvelope(response.data) && response.data.success) {
      response.data = response.data.data;
    }
    return response;
  },
  (error) => {
    const url = String(error?.config?.url || "");
    if (error?.response?.status === 401 && !url.includes("/auth/login") && !url.includes("/auth/forgot-password")) {
      window.dispatchEvent(new CustomEvent("dasigconnect:session-expired"));
    }
    const body = error?.response?.data;
    if (isEnvelope(body) && body.error && typeof body.error === "object") {
      body.error = (body.error as { message?: string }).message;
    }
    return Promise.reject(error);
  },
);

export function setAuthToken(token: string | null) {
  if (token) {
    api.defaults.headers.common.Authorization = `Bearer ${token}`;
  } else {
    delete api.defaults.headers.common.Authorization;
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
  adminOwner: boolean;
  superAdminTransferRequestedBy: string | null;
  superAdminTransferExpiresAt: string | null;
  institutionId: string | null;
  institutionName: string | null;
  createdAt: string;
  notifyInApp: boolean;
  notifyEmail: boolean;
  hasAvatar: boolean;
  avatarUpdatedAt: string | null;
  avatarUrl?: string | null;
}

export function login(email: string, password: string) {
  delete api.defaults.headers.common.Authorization;
  return api.post<LoginResponse>(
    "/auth/login",
    { email, password },
    { headers: { Authorization: undefined } }
  );
}

export function logout() {
  return api.post("/auth/logout");
}

export function requestPasswordReset(email: string) {
  delete api.defaults.headers.common.Authorization;
  return api.post("/auth/forgot-password", { email }, { headers: { Authorization: undefined } });
}

export function resetPassword(token: string, newPassword: string) {
  delete api.defaults.headers.common.Authorization;
  return api.post("/auth/reset-password", { token, newPassword }, { headers: { Authorization: undefined } });
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

export function resendExpiredInvitation(payload: { token?: string | null; email?: string | null }) {
  return api.post<{ message: string }>("/invitations/resend-expired", payload);
}

export function getMe() {
  return api.get<UserProfileResponse>("/me");
}

export function refreshSession() {
  return api.post<LoginResponse>("/auth/refresh");
}

export function updateAccountSettings(data: { displayName: string; notifyInApp: boolean; notifyEmail: boolean }) {
  return api.patch<UserProfileResponse>("/me/settings", data);
}

export function changePassword(currentPassword: string, newPassword: string) {
  return api.post("/auth/change-password", { currentPassword, newPassword });
}

export interface PageSettingsResponse {
  institutionId: string | null;
  watermarkEnabled: boolean;
  watermarkText: string | null;
  facebookPageId: string | null;
  updatedAt: string | null;
}

export function getPageSettings(institutionId?: string | null) {
  return api.get<PageSettingsResponse>("/settings/page", { params: institutionId ? { institutionId } : {} });
}

export function updatePageSettings(data: Omit<PageSettingsResponse, "institutionId" | "updatedAt">, institutionId?: string | null) {
  return api.put<PageSettingsResponse>("/settings/page", data, { params: institutionId ? { institutionId } : {} });
}

export interface InstitutionResponse {
  id: string;
  name: string;
  institutionCode: string;
  status: string;
  emailDomain: string;
  hasLogo: boolean;
  logoUpdatedAt: string | null;
  isProtected?: boolean;
  protected?: boolean;
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

export function listInstitutions(signal?: AbortSignal) {
  return api.get<InstitutionResponse[]>("/institutions", { signal });
}

export function listPublicInstitutions(signal?: AbortSignal) {
  return api.get<InstitutionResponse[]>("/institutions/public", { signal });
}

export function deleteInstitution(id: string) {
  return api.delete(`/institutions/${id}`);
}

export function updateInstitution(id: string, name: string, emailDomain: string) {
  return api.put<InstitutionResponse>(`/institutions/${id}`, { name, emailDomain });
}

export function deactivateInstitution(id: string) {
  return api.patch<InstitutionResponse>(`/institutions/${id}/deactivate`);
}

export function reactivateInstitution(id: string) {
  return api.patch<InstitutionResponse>(`/institutions/${id}/reactivate`);
}
export function uploadInstitutionLogo(id: string, file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return api.put<InstitutionResponse>(`/institutions/${id}/logo`, formData);
}

export function getInstitutionLogoUrl(id: string, logoUpdatedAt: string | null) {
  return api.getUri({
    url: `/institutions/${id}/logo`,
    params: logoUpdatedAt ? { v: logoUpdatedAt } : undefined,
  });
}

export function getUserCounts(institutionId: string) {
  return api.get<{ contributors: number; moderators: number }>(
    "/users/counts",
    {
      params: { institutionId },
    },
  );
}

export function listUsers(institutionId: string) {
  return api.get<UserProfileResponse[]>("/users", {
    params: { institutionId },
  }).then((response) => {
    response.data = response.data.map((user) => ({
      ...user,
      avatarUrl: user.hasAvatar ? getUserAvatarUrl(user.id, user.avatarUpdatedAt) : null,
    }));
    return response;
  });
}

export function listAdmins() {
  return api.get<UserProfileResponse[]>("/users/admins", {}).then((response) => {
    response.data = response.data.map((user) => ({
      ...user,
      avatarUrl: user.hasAvatar ? getUserAvatarUrl(user.id, user.avatarUpdatedAt) : null,
    }));
    return response;
  });
}

export function listNetworkUsers() {
  return api.get<UserProfileResponse[]>("/users/network", {}).then((response) => {
    response.data = response.data.map((user) => ({
      ...user,
      avatarUrl: user.hasAvatar ? getUserAvatarUrl(user.id, user.avatarUpdatedAt) : null,
    }));
    return response;
  });
}

export function updateUserStatus(
  id: string,
  accountState: "active" | "inactive" | "cancelled",
) {
  return api.patch<UserProfileResponse>(`/users/${id}/status`, {
    accountState,
  });
}

export function reassignContributor(id: string, targetInstitutionId: string) {
  return api.patch<UserProfileResponse>(`/users/${id}/institution`, {
    targetInstitutionId,
  });
}
export function uploadUserAvatar(id: string, file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return api.put<UserProfileResponse>(`/users/${id}/avatar`, formData);
}

export function getUserAvatarUrl(id: string, avatarUpdatedAt: string | null) {
  return api.getUri({
    url: `/users/${id}/avatar`,
    params: avatarUpdatedAt ? { v: avatarUpdatedAt } : undefined,
  });
}

export interface PendingInvitationResponse {
  id: string;
  recipientEmail: string;
  assignedRole: string;
  institutionId: string | null;
  expiresAt: string;
  createdAt: string;
}

export function listPendingInvitations(institutionId: string) {
  return api.get<PendingInvitationResponse[]>("/invitations/pending", {
    params: { institutionId },
  });
}

export function listPendingAdminInvitations() {
  return api.get<PendingInvitationResponse[]>("/invitations/pending/admins");
}

export function listPendingNetworkInvitations() {
  return api.get<PendingInvitationResponse[]>("/invitations/pending/network");
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
  institutionId: string | null;
  assignedRole: "contributor" | "moderator" | "admin";
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

export interface AdminTransferResponse {
  targetUserId: string;
  requestedByUserId: string;
  expiresAt: string;
  status: string;
}

export function requestAdminTransfer(id: string) {
  return api.post<AdminTransferResponse>(`/users/${id}/admin-transfer`);
}

export function confirmAdminTransfer() {
  return api.post<UserProfileResponse>("/users/admin-transfer/confirm");
}

export interface InvitationResponse {
  id: string;
  recipientEmail: string;
  assignedRole: string;
  institutionId: string | null;
  expiresAt: string;
  createdAt: string;
  emailDelivered: boolean;
  invitationUrl: string;
}
