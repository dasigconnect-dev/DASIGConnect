import type { UserProfileResponse } from '../api/authApi'
import type { User } from '../types/auth.types'

type IdentityUser = Pick<User, 'email' | 'firstName' | 'displayName' | 'name'> | null | undefined

const GREETING_NAME_OVERRIDES: Record<string, string> = {
  markanton: 'Mark',
}

export function getGreetingName(user: IdentityUser): string {
  const candidates = [
    firstWord(user?.firstName),
    firstWord(user?.displayName),
    firstWord(user?.name),
    firstEmailSegment(user?.email),
  ]
  const rawName = candidates.find(Boolean)
  if (!rawName) return 'there'
  const normalized = rawName.toLowerCase()
  return GREETING_NAME_OVERRIDES[normalized] || capitalizeName(rawName)
}

export function getUserDisplayName(user: UserProfileResponse | User | null | undefined): string {
  if (!user) return 'Unknown user'
  const displayName = 'displayName' in user ? user.displayName?.trim() : ''
  if (displayName) return displayName

  const firstName = user.firstName?.trim() || ''
  const lastName = 'lastName' in user ? user.lastName?.trim() || '' : ''
  const fullName = `${firstName} ${lastName}`.trim()
  if (fullName) return fullName

  return user.email || 'Unknown user'
}

export function getUserInitials(user: UserProfileResponse | User | null | undefined): string {
  const displayName = getUserDisplayName(user)
  if (displayName && displayName !== user?.email) {
    const parts = displayName.split(/\s+/).filter(Boolean)
    if (parts.length >= 2) return `${parts[0][0]}${parts[1][0]}`.toUpperCase()
    return parts[0]?.slice(0, 2).toUpperCase() || 'U'
  }
  return initialsFromEmail(user?.email || '')
}

export function fallbackDisplayNameFromEmail(email: string): string {
  const prefix = email.split('@')[0] || 'User'
  return prefix
    .split(/[._\-\d]+/)
    .filter(Boolean)
    .map(capitalizeName)
    .join(' ') || 'User'
}

export function initialsFromEmail(email: string): string {
  const prefix = email.split('@')[0] || 'U'
  const parts = prefix.split(/[._\-\d]+/).filter(Boolean)
  if (parts.length >= 2) return `${parts[0][0]}${parts[1][0]}`.toUpperCase()
  return prefix.substring(0, 2).toUpperCase()
}

function firstWord(value?: string | null): string {
  const trimmed = value?.trim() || ''
  if (!trimmed || trimmed.includes('@')) return ''
  return trimmed.split(/[\s._\-\d]+/)[0] || ''
}

function firstEmailSegment(email?: string | null): string {
  const prefix = email?.split('@')[0]?.trim() || ''
  return prefix.split(/[._\-\d]/)[0] || ''
}

function capitalizeName(value: string): string {
  const lettersOnly = value.replace(/[^A-Za-z]/g, '').toLowerCase()
  if (!lettersOnly) return ''
  return lettersOnly.charAt(0).toUpperCase() + lettersOnly.slice(1)
}

export type PendingPromotion = {
  role: 'admin' | 'moderator'
  requestedBy: string | null
  expiresAt: string | null
}

export function getPendingPromotion(user: UserProfileResponse | null | undefined): PendingPromotion | null {
  if (!user || !user.pendingPromotionRole) return null;
  const role = user.pendingPromotionRole.toLowerCase();
  if (role !== 'admin' && role !== 'moderator') return null;
  
  return {
    role: role as 'admin' | 'moderator',
    requestedBy: user.pendingPromotionRequestedBy || null,
    expiresAt: user.pendingPromotionExpiresAt || null,
  }
}
