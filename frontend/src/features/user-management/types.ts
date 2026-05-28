import type { InstitutionResponse } from '../../api/authApi'

export type InviteRole = 'contributor' | 'validator' | null

export interface InstitutionOption {
  id: string
  name: string
  code: string
  emailDomain: string
  status: string
}

export interface InviteFailure {
  email: string
  reason: string
  invitationUrl?: string
}

export interface InviteResults {
  total: number
  success: string[]
  failed: InviteFailure[]
}

export function toInstitutionOption(item: InstitutionResponse): InstitutionOption {
  return {
    id: item.id,
    name: item.name,
    code: item.institutionCode,
    emailDomain: item.emailDomain,
    status: item.status,
  }
}
