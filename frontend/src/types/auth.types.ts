export type UserRole = 'admin' | 'validator' | 'contributor'

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
  inst: string
  initials: string
}
