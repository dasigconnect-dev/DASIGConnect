import { useEffect, useState } from 'react'
import { confirmAdminPromotion, declineAdminPromotion, getMe } from '../../api/authApi'
import type { UserProfileResponse } from '../../api/authApi'

/**
 * UC-1.1 — "the promoted person must accept". A Contributor or Moderator with
 * a live pending Administrator promotion sees this on every screen until they
 * confirm or decline. Self-contained: fetches its own profile snapshot rather
 * than threading the promotion fields through the app-wide `User` type.
 */
export default function AdminPromotionBanner() {
  const [profile, setProfile] = useState<UserProfileResponse | null>(null)
  const [busy, setBusy] = useState<'confirm' | 'decline' | null>(null)
  const [dismissed, setDismissed] = useState(false)
  const [confirmed, setConfirmed] = useState(false)

  useEffect(() => {
    let active = true
    getMe()
      .then((res) => {
        if (active) setProfile(res.data)
      })
      .catch(() => {
        // Silent — a fetch failure here just means no banner; other UI already
        // surfaces auth/network problems.
      })
    return () => {
      active = false
    }
  }, [])

  if (!profile?.adminPromotionPending || dismissed) return null

  async function handleConfirm() {
    setBusy('confirm')
    try {
      await confirmAdminPromotion()
      setConfirmed(true)
      // The backend invalidated this account's session tokens on confirm — the
      // next authenticated request will 401 and the app's own session-expired
      // handling takes over, so a manual redirect isn't needed here.
    } catch {
      setBusy(null)
    }
  }

  async function handleDecline() {
    setBusy('decline')
    try {
      await declineAdminPromotion()
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
