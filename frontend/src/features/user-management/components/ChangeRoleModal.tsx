import { useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import type { UserProfileResponse } from '../../../api/authApi'
import BrandedSelect from '../../../components/ui/BrandedSelect'
import { getUserDisplayName } from '../../../lib/userIdentity'
import type { InstitutionOption } from '../types'

type TargetRole = 'contributor' | 'moderator' | 'admin'

interface ChangeRoleModalProps {
  user: UserProfileResponse
  institutions: InstitutionOption[]
  /** The acting admin is the Admin Owner — required to promote to / demote from admin. */
  isOwner: boolean
  /** Open admin slots (3 − active admins − pending admin invites). Promote-to-admin hidden at 0. */
  adminSlotsOpen: number
  busy: boolean
  error: string
  onConfirm: (role: TargetRole, institutionId: string | null) => void
  onClose: () => void
}

const ROLE_LABEL: Record<TargetRole, string> = {
  contributor: 'Contributor',
  moderator: 'Moderator',
  admin: 'Admin',
}

const ROLE_HINT: Record<TargetRole, string> = {
  contributor: 'Submits content for one institution.',
  moderator: 'Reviews submissions network-wide.',
  admin: 'Full network administration.',
}

export default function ChangeRoleModal({
  user,
  institutions,
  isOwner,
  adminSlotsOpen,
  busy,
  error,
  onConfirm,
  onClose,
}: ChangeRoleModalProps) {
  const currentRole = user.role.toLowerCase() as TargetRole

  const roleOptions = useMemo<TargetRole[]>(() => {
    const all: TargetRole[] = ['contributor', 'moderator']
    if (isOwner && (currentRole === 'admin' || adminSlotsOpen > 0)) {
      all.push('admin')
    }
    return all.filter((r) => r !== currentRole)
  }, [currentRole, isOwner, adminSlotsOpen])

  const [role, setRole] = useState<TargetRole | ''>('')
  const [institutionId, setInstitutionId] = useState('')

  const activeInstitutions = institutions.filter((inst) => inst.status === 'active')
  const needsInstitution = role === 'contributor'
  const canSubmit =
    role !== '' && (!needsInstitution || institutionId !== '') && !busy

  if (typeof document === 'undefined') return null

  return createPortal(
    <div className="dash-modal-backdrop im-modal-backdrop" onClick={onClose} role="presentation">
      <div
        className="dash-modal-card im-reassign-modal-card"
        onClick={(event) => event.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="um-change-role-title"
      >
        <div className="dash-modal-header im-modal-header">
          <div>
            <div id="um-change-role-title" className="dash-modal-title">
              Change Role
            </div>
            <div className="dash-modal-sub">
              <strong>{getUserDisplayName(user)}</strong> is currently a {ROLE_LABEL[currentRole]}.
            </div>
          </div>
          <button
            type="button"
            className="dash-modal-close"
            onClick={onClose}
            aria-label="Close change role modal"
            disabled={busy}
          >
            <i className="ti ti-x" aria-hidden="true"></i>
          </button>
        </div>

        <div className="dash-modal-body im-modal-body">
          <div className="dash-field">
            <label className="dash-field-label">New role</label>
            <BrandedSelect
              value={role}
              onChange={(value) => {
                setRole(value as TargetRole)
                if (value !== 'contributor') setInstitutionId('')
              }}
              disabled={busy}
              placeholder="— Select a role —"
              options={roleOptions.map((r) => ({ value: r, label: `${ROLE_LABEL[r]} — ${ROLE_HINT[r]}` }))}
            />
          </div>

          {needsInstitution && (
            <div className="dash-field">
              <label className="dash-field-label">Institution</label>
              <BrandedSelect
                value={institutionId}
                onChange={setInstitutionId}
                disabled={busy || activeInstitutions.length === 0}
                placeholder={
                  activeInstitutions.length === 0 ? 'No active institutions' : '— Select an institution —'
                }
                options={activeInstitutions.map((inst) => ({ value: inst.id, label: inst.name }))}
              />
              <div className="um-composer-label-hint">A contributor must belong to one institution.</div>
            </div>
          )}

          <div className="im-reassign-notice">
            <i className="ti ti-info-circle" aria-hidden="true"></i>
            <span>
              This person will be signed out and must sign in again.
              {currentRole === 'admin' && ' They will lose network-admin access.'}
            </span>
          </div>

          {error && (
            <div className="alert alert-err im-modal-alert" role="alert">
              <i className="ti ti-alert-circle" aria-hidden="true"></i>
              <div>{error}</div>
            </div>
          )}

          <div className="dash-modal-actions im-modal-actions">
            <button type="button" className="btn-ghost" onClick={onClose} disabled={busy}>
              Cancel
            </button>
            <button
              type="button"
              className="btn-primary"
              disabled={!canSubmit}
              onClick={() => onConfirm(role as TargetRole, needsInstitution ? institutionId : null)}
            >
              {busy ? (
                <>
                  <i className="ti ti-loader-2 im-spin" aria-hidden="true"></i>
                  Applying...
                </>
              ) : (
                'Change role'
              )}
            </button>
          </div>
        </div>
      </div>
    </div>,
    document.body,
  )
}
