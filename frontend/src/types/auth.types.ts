export type UserRole = 'super_administrator' | 'administrator' | 'contributor'

export type ScreenId =
  | 'login'
  | 'forgot'
  | 'forgot-sent'
  | 'invite'
  | 'no-account'
  | 'dashboard'

export interface User {
  email: string
  pw: string
  role: UserRole
  name: string
  firstName?: string | null
  lastName?: string | null
  displayName?: string | null
  inst: string
  institutionId?: string | null
  initials: string
}
