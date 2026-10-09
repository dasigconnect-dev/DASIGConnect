import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { confirmPromotion, declinePromotion } from '../../api/authApi'
import { currentProfileQueryOptions, useCurrentProfile } from '../../hooks/useCurrentProfile'
import type { User } from '../../types/auth.types'
import { getPendingPromotion } from '../../lib/userIdentity'

export default function RolePromotionBanner({ user }: { user: User }) {
  const queryClient = useQueryClient()
  const profileQueryOptions = currentProfileQueryOptions(user)
  const profile = useCurrentProfile(user).data
  const [busy, setBusy] = useState<'confirm' | 'decline' | null>(null)
  const [dismissed, setDismissed] = useState(false)
  const [confirmed, setConfirmed] = useState(false)

  const promotion = getPendingPromotion(profile)
  const hasPromotion = !!promotion && !dismissed

  if (!hasPromotion) return null
  
  const roleName = promotion.role === 'admin' ? 'Administrator' : 'Moderator'

  async function handleConfirm() {
    setBusy('confirm')
    try {
      await confirmPromotion()
      setConfirmed(true)
    } catch {
      setBusy(null)
    }
  }

  async function handleDecline() {
    setBusy('decline')
    try {
      const response = await declinePromotion()
      queryClient.setQueryData(profileQueryOptions.queryKey, response.data)
      setDismissed(true)
    } catch {
      setBusy(null)
    }
  }

  return (
    <div id="role-promotion-banner">
      <div className="banner-msg">
        <i className="ti ti-shield-plus" aria-hidden="true"></i>
        <span>
          {confirmed
            ? `${roleName} access confirmed. Sign in again to continue with your new access.`
            : `You've been proposed for ${roleName} access. Confirm to accept, or decline to keep your current role.`}
        </span>
      </div>
      {!confirmed && (
        <div className="banner-actions">
          <button
            type="button"
            className="banner-btn banner-btn-stay"
            onClick={() => void handleConfirm()}
            disabled={busy !== null}
          >
            {busy === 'confirm' ? 'Confirming…' : 'Confirm'}
          </button>
          <button
            type="button"
            className="banner-btn banner-btn-dismiss"
            onClick={() => void handleDecline()}
            disabled={busy !== null}
          >
            {busy === 'decline' ? 'Declining…' : 'Decline'}
          </button>
        </div>
      )}
    </div>
  )
}
