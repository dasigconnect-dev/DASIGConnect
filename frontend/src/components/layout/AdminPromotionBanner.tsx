import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { confirmAdminPromotion, declineAdminPromotion } from '../../api/authApi'
import { currentProfileQueryOptions, useCurrentProfile } from '../../hooks/useCurrentProfile'
import type { User } from '../../types/auth.types'

/**
 * UC-1.1 - "the promoted person must accept". A Contributor or Moderator with
 * a live pending Administrator promotion sees this on every screen until they
 * confirm or decline. It reuses the verified current-profile cache so mounting
 * the protected layout does not trigger another /me request.
 */
export default function AdminPromotionBanner({ user }: { user: User }) {
  const queryClient = useQueryClient()
  const profileQueryOptions = currentProfileQueryOptions(user)
  const profile = useCurrentProfile(user).data
  const [busy, setBusy] = useState<'confirm' | 'decline' | null>(null)
  const [dismissed, setDismissed] = useState(false)
  const [confirmed, setConfirmed] = useState(false)

  if (!profile?.adminPromotionPending || dismissed) return null

  async function handleConfirm() {
    setBusy('confirm')
    try {
      await confirmAdminPromotion()
      setConfirmed(true)
      // The backend invalidated this account's session tokens on confirm. The
      // next authenticated request will 401 and session-expired handling takes over.
    } catch {
      setBusy(null)
    }
  }

  async function handleDecline() {
    setBusy('decline')
    try {
      const response = await declineAdminPromotion()
      queryClient.setQueryData(profileQueryOptions.queryKey, response.data)
      setDismissed(true)
    } catch {
      setBusy(null)
    }
  }

  return (
    <div id="admin-promotion-banner">
      <div className="banner-msg">
        <i className="ti ti-shield-plus" aria-hidden="true"></i>
        <span>
          {confirmed
            ? 'Administrator access confirmed. Sign in again to continue with your new access.'
            : "You've been proposed for Administrator access. Confirm to accept, or decline to keep your current role."}
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
