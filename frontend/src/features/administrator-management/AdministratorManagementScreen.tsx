import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { createPortal } from 'react-dom'
import {
  cancelInvitationByUser,
  changeUserRole,
  confirmAdminTransfer,
  deleteUser,
  eraseUserData,
  inviteUser,
  listAdmins,
  listInstitutions,
  listPendingAdminInvitations,
  requestAdminTransfer,
  resendInvitation,
  updateUserStatus,
} from '../../api/authApi'
import type { PendingInvitationResponse, UserProfileResponse } from '../../api/authApi'
import type { User } from '../../types/auth.types'
import { useToast } from '../../context/ToastContext'
import { registerAppCacheReset } from '../../lib/appCache'
import { getUserDisplayName } from '../../lib/userIdentity'
import ChangeRoleModal from '../user-management/components/ChangeRoleModal'
import ConfirmDialog from '../user-management/components/ConfirmDialog'
import DeliveryIssuesAlert from '../user-management/components/DeliveryIssuesAlert'
import InstitutionUsersCard from '../user-management/components/InstitutionUsersCard'
import InvitationComposer from '../user-management/components/InvitationComposer'
import { SkeletonBlock } from '../user-management/components/LoadingPrimitives'
import type { InstitutionOption, InviteResults } from '../user-management/types'
import { toInstitutionOption } from '../user-management/types'

interface AdminManagementScreenProps {
  user: User
  onProfileUpdated?: () => Promise<void> | void
}

interface ConfirmDialogState {
  title: string
  message: string
  confirmLabel: string
  dangerous: boolean
  requireTypedConfirmation?: string
  onConfirm: () => void
}

// Survives route unmount so switching screens shows cached data instead of a
// full reload; the mount effect still refetches quietly in the background.
const memoryCache: {
  admins: UserProfileResponse[] | null
  pendingInvitations: PendingInvitationResponse[]
} = { admins: null, pendingInvitations: [] }
registerAppCacheReset(() => {
  memoryCache.admins = null
  memoryCache.pendingInvitations = []
})

export default function AdminManagementScreen({
  user,
  onProfileUpdated,
}: AdminManagementScreenProps) {
  const toast = useToast()
  const [admins, setAdmins] = useState<UserProfileResponse[]>(
    () => memoryCache.admins ?? [],
  )
  const [pendingInvitations, setPendingInvitations] = useState<PendingInvitationResponse[]>(
    () => memoryCache.pendingInvitations,
  )
  const [loading, setLoading] = useState(() => memoryCache.admins === null)
  const [error, setError] = useState('')

  // Invitation modal state (mirrors Institution Management contributor invite)
  const [showInviteModal, setShowInviteModal] = useState(false)
  const [emailChips, setEmailChips] = useState<string[]>([])
  const [emailDraft, setEmailDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [inviteResults, setInviteResults] = useState<InviteResults | null>(null)

  const [updatingUserId, setUpdatingUserId] = useState<string | null>(null)
  const [confirmDialog, setConfirmDialog] = useState<ConfirmDialogState | null>(null)

  // Change role modal (demote a peer admin to moderator/contributor).
  const [institutions, setInstitutions] = useState<InstitutionOption[]>([])
  const [roleUser, setRoleUser] = useState<UserProfileResponse | null>(null)
  const [roleBusy, setRoleBusy] = useState(false)
  const [roleError, setRoleError] = useState('')

  const currentAdminRecord = useMemo(
    () => admins.find((item) => item.email.toLowerCase() === user.email.toLowerCase()) ?? null,
    [admins, user.email],
  )
  const pendingTransfer = Boolean(currentAdminRecord?.superAdminTransferRequestedBy && currentAdminRecord.superAdminTransferExpiresAt)

  // Only the Admin Owner can invite, remove, or transfer admin accounts. Peer
  // admins get a read-only view of the roster.
  const isOwner = currentAdminRecord?.adminOwner === true
  const ADMIN_LIMIT = 3
  const activeAdminCount = useMemo(
    () => admins.filter((item) => item.accountState.toLowerCase() === 'active').length,
    [admins],
  )
  const atAdminLimit = activeAdminCount >= ADMIN_LIMIT
  // A batch can only fill the admin slots that are still open (active + already-pending count against the cap).
  const remainingAdminSlots = Math.max(0, ADMIN_LIMIT - activeAdminCount - pendingInvitations.length)

  useEffect(() => {
    void loadAdminManagement()
  }, [])

  useEffect(() => {
    if (!isOwner) return
    listInstitutions()
      .then((response) => setInstitutions(response.data.map(toInstitutionOption)))
      .catch(() => setInstitutions([]))
  }, [isOwner])

  // Keep the cross-route cache in sync with the latest lists (incl. mutations).
  useEffect(() => {
    if (loading) return
    memoryCache.admins = admins
    memoryCache.pendingInvitations = pendingInvitations
  }, [loading, admins, pendingInvitations])

  useEffect(() => {
    if (!showInviteModal || typeof document === 'undefined') return

    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !sending) {
        handleCloseInviteModal()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [showInviteModal, sending])

  async function loadAdminManagement() {
    if (memoryCache.admins === null) {
      setLoading(true)
    }
      setError('')
    const [adminsResult, pendingResult] = await Promise.allSettled([
      listAdmins(),
      listPendingAdminInvitations(),
    ])

    if (adminsResult.status === 'fulfilled') {
      setAdmins(adminsResult.value.data)
    } else {
      setAdmins([])
    }

    if (pendingResult.status === 'fulfilled') {
      setPendingInvitations(pendingResult.value.data)
    } else {
      setPendingInvitations([])
    }

    const loadErrors = [
      adminsResult.status === 'rejected'
        ? `Admin accounts: ${getApiErrorMessage(adminsResult.reason, 'Unable to load accounts.')}`
        : null,
      pendingResult.status === 'rejected'
        ? `Pending invitations: ${getApiErrorMessage(pendingResult.reason, 'Unable to load invitations.')}`
        : null,
    ].filter(Boolean)

    setError(loadErrors.join(' '))
    setLoading(false)
  }

  function handleOpenInviteModal() {
    setEmailChips([])
    setEmailDraft('')
    setInviteResults(null)
    setShowInviteModal(true)
  }

  function handleCloseInviteModal() {
    if (sending) return
    setShowInviteModal(false)
    setEmailChips([])
    setEmailDraft('')
    setInviteResults(null)
  }

  async function handleSendInvitations(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setInviteResults(null)

    const inviteEmails = emailChips
    if (inviteEmails.length === 0) {
      setInviteResults({
        total: 0,
        success: [],
        failed: [{ email: 'Batch', reason: 'Add at least one admin email.' }],
      })
      return
    }

    if (inviteEmails.length > remainingAdminSlots) {
      setInviteResults({
        total: inviteEmails.length,
        success: [],
        failed: [
          {
            email: 'Batch',
            reason:
              remainingAdminSlots === 0
                ? `The network is at its ${ADMIN_LIMIT}-admin limit. Remove or transfer an admin first.`
                : `Only ${remainingAdminSlots} admin slot${remainingAdminSlots === 1 ? '' : 's'} remain (limit ${ADMIN_LIMIT}).`,
          },
        ],
      })
      return
    }

    setSending(true)
    const success: string[] = []
    const failed: InviteResults['failed'] = []
    try {
      for (const rawEmail of inviteEmails) {
        const recipientEmail = rawEmail.trim().toLowerCase()
        if (!isValidEmail(recipientEmail)) {
          failed.push({ email: recipientEmail || 'Invite', reason: 'Enter a valid admin email.' })
          continue
        }
        try {
          const response = await inviteUser({
            recipientEmail,
            institutionId: null,
            assignedRole: 'admin',
          })
          if (response.data.emailDelivered) {
            success.push(recipientEmail)
          } else {
            failed.push({
              email: recipientEmail,
              reason: 'Invitation created, but email delivery failed.',
              invitationUrl: response.data.invitationUrl,
            })
          }
        } catch (err: unknown) {
          failed.push({ email: recipientEmail, reason: getApiErrorMessage(err, 'Invitation failed.') })
        }
      }

      if (failed.length === 0) {
        toast.success(
          `${success.length} admin invitation${success.length === 1 ? '' : 's'} sent.`,
        )
      } else {
        if (success.length > 0) {
          toast.info(
            `${success.length} of ${inviteEmails.length} invitation${inviteEmails.length === 1 ? '' : 's'} sent.`,
          )
        }
        setInviteResults({ total: inviteEmails.length, success, failed })
      }
      setEmailChips([])
      setEmailDraft('')
      await loadAdminManagement()
      if (failed.length === 0) {
        setShowInviteModal(false)
      }
    } finally {
      setSending(false)
    }
  }

  function handleResubmitFailed() {
    if (!inviteResults) return
    setEmailChips(inviteResults.failed.map((item) => item.email).filter(isValidEmail))
    setEmailDraft('')
    setInviteResults(null)
  }

  function handleToggleUserStatus(managedUser: UserProfileResponse) {
    const nextState = managedUser.accountState.toLowerCase() === 'inactive' ? 'active' : 'inactive'
    const verb = nextState === 'inactive' ? 'Deactivate' : 'Reactivate'
    setConfirmDialog({
      title: `${verb} Admin`,
      message: `${verb} ${getUserDisplayName(managedUser)}? Active sessions are revoked when an account is deactivated.`,
      confirmLabel: verb,
      dangerous: nextState === 'inactive',
      onConfirm: () => {
        setConfirmDialog(null)
        void executeToggleUserStatus(managedUser, nextState)
      },
    })
  }

  async function executeToggleUserStatus(
    managedUser: UserProfileResponse,
    nextState: 'active' | 'inactive',
  ) {
    setUpdatingUserId(managedUser.id)
    try {
      const response = await updateUserStatus(managedUser.id, nextState)
      setAdmins((current) =>
        current.map((item) => (item.id === managedUser.id ? response.data : item)),
      )
      toast.success(nextState === 'inactive' ? 'Admin deactivated.' : 'Admin reactivated.')
    } catch (err: unknown) {
      toast.error(getApiErrorMessage(err, 'Unable to update admin.'))
    } finally {
      setUpdatingUserId(null)
    }
  }

  function handleDeleteUser(managedUser: UserProfileResponse) {
    const isErased = Boolean(managedUser.purgedAt)
    setConfirmDialog({
      title: isErased ? 'Remove Record' : 'Remove Admin',
      message: isErased
        ? `This account's personal data has already been erased. Removing the record permanently deletes it if nothing references it; if it has historical records it stays as an anonymised inactive row.`
        : `Remove ${getUserDisplayName(managedUser)}? Accounts with historical records are kept inactive for audit integrity; otherwise the account is permanently deleted and cannot be recovered.`,
      confirmLabel: isErased ? 'Remove record' : 'Remove account',
      dangerous: true,
      requireTypedConfirmation: isErased ? 'DELETE' : managedUser.email,
      onConfirm: () => {
        setConfirmDialog(null)
        void executeDeleteUser(managedUser)
      },
    })
  }

  async function executeDeleteUser(managedUser: UserProfileResponse) {
    setUpdatingUserId(managedUser.id)
    try {
      const response = await deleteUser(managedUser.id)
      if (response.data.action === 'deleted') {
        setAdmins((current) => current.filter((item) => item.id !== managedUser.id))
        toast.success(managedUser.purgedAt ? 'Record removed.' : 'Admin removed.')
      } else {
        setAdmins((current) =>
          current.map((item) =>
            item.id === managedUser.id ? { ...item, accountState: 'inactive' } : item,
          ),
        )
        toast.info(
          managedUser.purgedAt
            ? 'This record has activity history and is kept as an anonymised tombstone for the audit trail — it cannot be removed.'
            : 'Admin account remains inactive because it has historical records.',
        )
      }
    } catch (err: unknown) {
      toast.error(getApiErrorMessage(err, 'Unable to remove admin.'))
    } finally {
      setUpdatingUserId(null)
    }
  }

  function handleEraseData(managedUser: UserProfileResponse) {
    setConfirmDialog({
      title: 'Erase personal data',
      message: `Permanently scrub ${getUserDisplayName(managedUser)}'s name, email, avatar, credentials, and unpublished media uploads. Their submissions and review history remain but no longer identify them. This cannot be undone.`,
      confirmLabel: 'Erase personal data',
      dangerous: true,
      requireTypedConfirmation: managedUser.email,
      onConfirm: () => {
        setConfirmDialog(null)
        void executeEraseData(managedUser)
      },
    })
  }

  async function executeEraseData(managedUser: UserProfileResponse) {
    setUpdatingUserId(managedUser.id)
    try {
      const response = await eraseUserData(managedUser.id)
      const purged = response.data.mediaAssetsPurged
      toast.success(
        `Personal data erased.${purged > 0 ? ` ${purged} media file${purged === 1 ? '' : 's'} queued for purge.` : ''}`,
      )
      await loadAdminManagement()
    } catch (err: unknown) {
      toast.error(getApiErrorMessage(err, 'Unable to erase personal data.'))
    } finally {
      setUpdatingUserId(null)
    }
  }

  function handleRequestTransfer(managedUser: UserProfileResponse) {
    setConfirmDialog({
      title: 'Transfer Admin',
      message: `Request transfer of Admin status to ${getUserDisplayName(managedUser)}? They must explicitly confirm before the transfer takes effect.`,
      confirmLabel: 'Request transfer',
      dangerous: false,
      onConfirm: () => {
        setConfirmDialog(null)
        void executeRequestTransfer(managedUser)
      },
    })
  }

  async function executeRequestTransfer(managedUser: UserProfileResponse) {
    setUpdatingUserId(managedUser.id)
    try {
      await requestAdminTransfer(managedUser.id)
      toast.success('Admin transfer requested.')
      await loadAdminManagement()
    } catch (err: unknown) {
      toast.error(getApiErrorMessage(err, 'Unable to request transfer.'))
    } finally {
      setUpdatingUserId(null)
    }
  }

  async function handleConfirmTransfer() {
    try {
      await confirmAdminTransfer()
      toast.success('Admin transfer confirmed.')
      await loadAdminManagement()
      await onProfileUpdated?.()
    } catch (err: unknown) {
      toast.error(getApiErrorMessage(err, 'Unable to confirm transfer.'))
    }
  }

  async function handleResendInvitation(id: string) {
    try {
      await resendInvitation(id)
      toast.success('Invitation resent.')
      await loadAdminManagement()
    } catch (err: unknown) {
      toast.error(getApiErrorMessage(err, 'Unable to resend invitation.'))
    }
  }

  async function handleCancelInvitationFromUser(managedUser: UserProfileResponse) {
    setUpdatingUserId(managedUser.id)
    try {
      await cancelInvitationByUser(managedUser.id)
      toast.success('Invitation cancelled.')
      await loadAdminManagement()
    } catch (err: unknown) {
      toast.error(getApiErrorMessage(err, 'Unable to cancel invitation.'))
    } finally {
      setUpdatingUserId(null)
    }
  }

  function handleResendInvitationFromUser(managedUser: UserProfileResponse) {
    const match = pendingInvitations.find(
      (item) => item.recipientEmail.toLowerCase() === managedUser.email.toLowerCase(),
    )
    if (match) {
      void handleResendInvitation(match.id)
    }
  }

  function handleOpenChangeRole(managedUser: UserProfileResponse) {
    setRoleUser(managedUser)
    setRoleError('')
  }

  function handleCloseChangeRole() {
    if (roleBusy) return
    setRoleUser(null)
    setRoleError('')
  }

  async function handleConfirmChangeRole(
    role: 'contributor' | 'moderator' | 'admin',
    institutionId: string | null,
  ) {
    if (!roleUser) return
    setRoleBusy(true)
    setRoleError('')
    try {
      await changeUserRole(roleUser.id, role, institutionId)
      toast.success(`${getUserDisplayName(roleUser)} is now a ${role}.`)
      await loadAdminManagement()
      setRoleUser(null)
    } catch (err: unknown) {
      setRoleError(getApiErrorMessage(err, 'Unable to change role.'))
    } finally {
      setRoleBusy(false)
    }
  }

  const initializing = loading && admins.length === 0 && pendingInvitations.length === 0

  const inviteAdminModal =
    showInviteModal && typeof document !== 'undefined'
      ? createPortal(
          <div
            className="dash-modal-backdrop im-modal-backdrop"
            onClick={handleCloseInviteModal}
            role="presentation"
          >
            <div
              className="dash-modal-card im-invite-modal-card"
              onClick={(event) => event.stopPropagation()}
              role="dialog"
              aria-modal="true"
              aria-labelledby="am-invite-modal-title"
              aria-describedby="am-invite-modal-subtitle"
            >
              <div className="dash-modal-header im-modal-header">
                <div>
                  <div id="am-invite-modal-title" className="dash-modal-title">
                    Invite Admin
                  </div>
                  <div id="am-invite-modal-subtitle" className="dash-modal-sub">
                    Send a single-use activation link for a network-wide Admin account.
                    {' '}The network allows {ADMIN_LIMIT} admins&nbsp;&mdash;{' '}
                    <strong>
                      {remainingAdminSlots} slot{remainingAdminSlots === 1 ? '' : 's'} open
                    </strong>
                    .
                  </div>
                </div>
                <button
                  type="button"
                  className="dash-modal-close"
                  onClick={handleCloseInviteModal}
                  aria-label="Close invite admin modal"
                  disabled={sending}
                >
                  <i className="ti ti-x" aria-hidden="true"></i>
                </button>
              </div>

              <InvitationComposer
                chips={emailChips}
                emailDraft={emailDraft}
                role="admin"
                selectedInstitution={null}
                canChooseRole={false}
                networkWide
                embedded
                maxRecipients={remainingAdminSlots}
                sending={sending}
                onDraftChange={setEmailDraft}
                onAddChip={(email) => {
                  if (!emailChips.includes(email.toLowerCase())) {
                    setEmailChips((prev) => [...prev, email.toLowerCase()])
                  }
                }}
                onRemoveChip={(index) =>
                  setEmailChips((prev) => prev.filter((_, i) => i !== index))
                }
                onRoleChange={() => {}}
                onSubmit={(event) => void handleSendInvitations(event)}
              />

              {inviteResults && (
                <DeliveryIssuesAlert results={inviteResults} onResubmitFailed={handleResubmitFailed} />
              )}
            </div>
          </div>,
          document.body,
        )
      : null

  return (
    <div className="um-screen">
      <main className="um-body">
        <header className="im-page-header">
          <div>
            <h1>Admin Management</h1>
            <p>
              {isOwner
                ? 'Invite, remove, and transfer network-level admin accounts.'
                : 'View network-level admin accounts. Only the Admin Owner can make changes.'}
              {' '}
              <strong>{activeAdminCount} / {ADMIN_LIMIT}</strong> admin
              {activeAdminCount === 1 ? '' : 's'} in use.
            </p>
          </div>
          {isOwner && (
            <button
              type="button"
              className="im-add-btn"
              onClick={handleOpenInviteModal}
              disabled={atAdminLimit}
              title={atAdminLimit ? `Admin limit of ${ADMIN_LIMIT} reached` : undefined}
            >
              <i className="ti ti-user-plus" aria-hidden="true"></i>
              Invite Admin
            </button>
          )}
        </header>

        {pendingTransfer && (
          <div className="alert alert-info" role="status">
            <i className="ti ti-shield-up" aria-hidden="true"></i>
            <div>
              <strong>Admin transfer pending.</strong>
              <span> Confirm to accept network ownership before the request expires.</span>
            </div>
            <button type="button" className="btn-primary" onClick={() => void handleConfirmTransfer()}>
              Confirm Transfer
            </button>
          </div>
        )}

        {error && (
          <div className="alert alert-err" role="alert">
            <i className="ti ti-alert-circle" aria-hidden="true"></i>
            <div>{error}</div>
          </div>
        )}

        {initializing ? (
          <SkeletonBlock className="um-skeleton-line is-wide" />
        ) : (
          <>
            <InstitutionUsersCard
              currentUser={user}
              users={admins}
              loading={loading}
              updatingUserId={updatingUserId}
              resendingUserId={updatingUserId}
              readOnly={!isOwner}
              onToggleUserStatus={handleToggleUserStatus}
              onDeleteUser={handleDeleteUser}
              onCancelInvitation={handleCancelInvitationFromUser}
              onResendInvitation={handleResendInvitationFromUser}
              onRequestSuperAdminTransfer={handleRequestTransfer}
              onChangeRole={isOwner ? handleOpenChangeRole : undefined}
              onEraseData={isOwner ? handleEraseData : undefined}
              showRoleControls={false}
              showInstitutionColumn={false}
              showFilterPills
              showHeader={false}
              variant="directory"
              userColumnLabel="Admin"
            />
          </>
        )}
      </main>

      {confirmDialog && (
        <ConfirmDialog
          title={confirmDialog.title}
          message={confirmDialog.message}
          confirmLabel={confirmDialog.confirmLabel}
          dangerous={confirmDialog.dangerous}
          requireTypedConfirmation={confirmDialog.requireTypedConfirmation}
          onConfirm={confirmDialog.onConfirm}
          onCancel={() => setConfirmDialog(null)}
        />
      )}
      {inviteAdminModal}

      {roleUser && (
        <ChangeRoleModal
          user={roleUser}
          institutions={institutions}
          isOwner={isOwner}
          adminSlotsOpen={remainingAdminSlots}
          busy={roleBusy}
          error={roleError}
          onConfirm={(role, institutionId) => void handleConfirmChangeRole(role, institutionId)}
          onClose={handleCloseChangeRole}
        />
      )}
    </div>
  )
}

function isValidEmail(email: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}

function getApiErrorMessage(error: unknown, fallback: string) {
  if (!isRecord(error)) return fallback
  const response = error.response
  if (isRecord(response)) {
    const data = response.data
    if (isRecord(data)) {
      if (typeof data.error === 'string') return data.error
      if (typeof data.message === 'string') return data.message
    }
  }
  return typeof error.message === 'string' ? error.message : fallback
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
