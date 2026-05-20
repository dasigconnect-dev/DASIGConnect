import axios from 'axios'

const BASE_URL = import.meta.env.VITE_API_URL || '/api/v1'

export const api = axios.create({
  baseURL: BASE_URL,
})

export function setAuthToken(token: string | null) {
  if (token) {
    api.defaults.headers.common.Authorization = `Bearer ${token}`
  } else {
    delete api.defaults.headers.common.Authorization
  }
}

export interface LoginResponse {
  accessToken: string
  role: string
  institutionId: string
}

export function login(email: string, password: string) {
  return api.post<LoginResponse>('/auth/login', { email, password })
}

export function logout() {
  return api.post('/auth/logout')
}

export function requestPasswordReset(email: string) {
  return api.post('/auth/forgot-password', { email })
}

export function resetPassword(token: string, newPassword: string) {
  return api.post('/auth/reset-password', { token, newPassword })
}

export interface InvitationValidateResponse {
  recipientEmail: string
  assignedRole: string
  institutionName: string
  expiresAt: string
}

export function validateInvitation(token: string) {
  return api.get<InvitationValidateResponse>('/invitations/validate', {
    params: { token },
  })
}

export function acceptInvitation(token: string, password: string) {
  return api.post<LoginResponse>('/invitations/accept', { token, password })
}

export interface InstitutionResponse {
  id: string
  name: string
  institutionCode: string
  status: string
  emailDomain: string
}

export function createInstitution(
  name: string,
  institutionCode: string,
  emailDomain: string,
) {
  return api.post<InstitutionResponse>('/admin/institutions', {
    name,
    institutionCode,
    emailDomain,
  })
}

export function listInstitutions() {
  return api.get<InstitutionResponse[]>('/admin/institutions')
}

export interface InviteUserRequest {
  recipientEmail: string
  institutionId: string
  assignedRole: 'contributor' | 'validator'
}

export function inviteUser(data: InviteUserRequest) {
  return api.post<InvitationResponse>('/invitations', data)
}

export interface InvitationResponse {
  id: string
  recipientEmail: string
  assignedRole: string
  institutionId: string
  expiresAt: string
  createdAt: string
  emailDelivered: boolean
  invitationUrl: string
}

