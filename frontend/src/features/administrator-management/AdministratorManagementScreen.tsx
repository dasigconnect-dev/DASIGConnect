import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { createPortal } from 'react-dom'
import {
  cancelInvitation,
  confirmSuperAdministratorTransfer,
  deleteUser,
  inviteUser,
  listAdministrators,
  listPendingAdministratorInvitations,
  requestSuperAdministratorTransfer,
  resendInvitation,
  updateUserStatus,
} from '../../api/authApi'
import type { PendingInvitationResponse, UserProfileResponse } from '../../api/authApi'
import type { User } from '../../types/auth.types'
import { useToast } from '../../context/ToastContext'
import { getUserDisplayName } from '../../lib/userIdentity'
import ConfirmDialog from '../user-management/components/ConfirmDialog'
import DeliveryIssuesAlert from '../user-management/components/DeliveryIssuesAlert'
import InstitutionUsersCard from '../user-management/components/InstitutionUsersCard'
import InvitationComposer from '../user-management/components/InvitationComposer'
import { SkeletonBlock } from '../user-management/components/LoadingPrimitives'
import type { InviteResults } from '../user-management/types'

interface AdministratorManagementScreenProps {
  user: User
  onProfileUpdated?: () => Promise<void> | void
}

interface ConfirmDialogState {
  title: string
  message: string
  confirmLabel: string
  dangerous: boolean
  onConfirm: () => void
}

// Survives route unmount so switching screens shows cached data instead of a
// full reload; the mount effect still refetches quietly in the background.
const memoryCache: {
  administrators: UserProfileResponse[] | null
  pendingInvitations: PendingInvitationResponse[]
} = { administrators: null, pendingInvitations: [] }

export default function AdministratorManagementScreen({
  user,
  onProfileUpdated,
}: AdministratorManagementScreenProps) {
  const toast = useToast()
  const [administrators, setAdministrators] = useState<UserProfileResponse[]>(
    () => memoryCache.administrators ?? [],
  )
  const [pendingInvitations, setPendingInvitations] = useState<PendingInvitationResponse[]>(
    () => memoryCache.pendingInvitations,
  )
  const [loading, setLoading] = useState(() => memoryCache.administrators === null)
  const [error, setError] = useState('')

  // Invitation modal state (mirrors Institution Management contributor invite)
  const [showInviteModal, setShowInviteModal] = useState(false)
  const [emailChips, setEmailChips] = useState<string[]>([])
  const [emailDraft, setEmailDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [inviteResults, setInviteResults] = useState<InviteResults | null>(null)

  const [updatingUserId, setUpdatingUserId] = useState<string | null>(null)
  const [confirmDialog, setConfirmDialog] = useState<ConfirmDialogState | null>(null)

  const currentAdminRecord = useMemo(
    () => administrators.find((item) => item.email.toLowerCase() === user.email.toLowerCase()) ?? null,
    [administrators, user.email],
  )
  const pendingTransfer = Boolean(currentAdminRecord?.superAdminTransferRequestedBy && currentAdminRecord.superAdminTransferExpiresAt)

  useEffect(() => {
    void loadAdministratorManagement()
  }, [])

  // Keep the cross-route cache in sync with the latest lists (incl. mutations).
  useEffect(() => {
    if (loading) return
    memoryCache.administrators = administrators
    memoryCache.pendingInvitations = pendingInvitations
  }, [loading, administrators, pendingInvitations])

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

  async function loadAdministratorManagement() {
    if (memoryCache.administrators === null) {
      setLoading(true)
    }
    setError('')
    const [adminsResult, pendingResult] = await Promise.allSettled([
      listAdministrators(),
      listPendingAdministratorInvitations(),
    ])

    if (adminsResult.status === 'fulfilled') {
      setAdministrators(adminsResult.value.data)
    } else {
      setAdministrators([])
    }

    if (pendingResult.status === 'fulfilled') {
      setPendingInvitations(pendingResult.value.data)
    } else {
      setPendingInvitations([])
    }

    const loadErrors = [
      adminsResult.status === 'rejected'
        ? `Administrator accounts: ${getApiErrorMessage(adminsResult.reason, 'Unable to load accounts.')}`
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
        failed: [{ email: 'Batch', reason: 'Add at least one administrator email.' }],
      })
      return
    }

    if (inviteEmails.length > 15) {
      setInviteResults({
        total: inviteEmails.length,
        success: [],
        failed: [{ email: 'Batch', reason: 'Batch exceeds maximum of 15 invitations.' }],
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
          failed.push({ email: recipientEmail || 'Invite', reason: 'Enter a valid administrator email.' })
          continue
        }
        try {
          const response = await inviteUser({
            recipientEmail,
            institutionId: null,
            assignedRole: 'administrator',
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
          `${success.length} administrator invitation${success.length === 1 ? '' : 's'} sent.`,
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
      await loadAdministratorManagement()
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
      title: `${verb} Administrator`,
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
      setAdministrators((current) =>
        current.map((item) => (item.id === managedUser.id ? response.data : item)),
      )
      toast.success(nextState === 'inactive' ? 'Administrator deactivated.' : 'Administrator reactivated.')
    } catch (err: unknown) {
      toast.error(getApiErrorMessage(err, 'Unable to update administrator.'))
    } finally {
      setUpdatingUserId(null)
    }
  }

  function handleDeleteUser(managedUser: UserProfileResponse) {
    setConfirmDialog({
      title: 'Remove Administrator',
      message: `Remove ${getUserDisplayName(managedUser)}? Accounts with historical records will be kept inactive for audit integrity.`,
      confirmLabel: 'Remove',
      dangerous: true,
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
        setAdministrators((current) => current.filter((item) => item.id !== managedUser.id))
        toast.success('Administrator removed.')
      } else {
        setAdministrators((current) =>
          current.map((item) =>
            item.id === managedUser.id ? { ...item, accountState: 'inactive' } : item,
          ),
        )
        toast.info('Administrator account remains inactive because it has historical records.')
      }
    } catch (err: unknown) {
      toast.error(getApiErrorMessage(err, 'Unable to remove administrator.'))
    } finally {
      setUpdatingUserId(null)
    }
  }

  function handleRequestTransfer(managedUser: UserProfileResponse) {
    setConfirmDialog({
      title: 'Transfer Super Administrator',
      message: `Request transfer of Super Administrator status to ${getUserDisplayName(managedUser)}? They must explicitly confirm before the transfer takes effect.`,
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
      await requestSuperAdministratorTransfer(managedUser.id)
      toast.success('Super Administrator transfer requested.')
      await loadAdministratorManagement()
    } catch (err: unknown) {
      toast.error(getApiErrorMessage(err, 'Unable to request transfer.'))
    } finally {
      setUpdatingUserId(null)
    }
  }

  async function handleConfirmTransfer() {
    try {
      await confirmSuperAdministratorTransfer()
      toast.success('Super Administrator transfer confirmed.')
      await loadAdministratorManagement()
      await onProfileUpdated?.()
    } catch (err: unknown) {
      toast.error(getApiErrorMessage(err, 'Unable to confirm transfer.'))
    }
  }

  async function handleResendInvitation(id: string) {
    try {
      await resendInvitation(id)
      toast.success('Invitation resent.')
      await loadAdministratorManagement()
    } catch (err: unknown) {
      toast.error(getApiErrorMessage(err, 'Unable to resend invitation.'))
    }
  }

  async function handleCancelInvitation(id: string) {
    try {
      await cancelInvitation(id)
      toast.success('Invitation cancelled.')
      await loadAdministratorManagement()
    } catch (err: unknown) {
      toast.error(getApiErrorMessage(err, 'Unable to cancel invitation.'))
    }
  }

  function handleCancelInvitationFromUser(managedUser: UserProfileResponse) {
    const match = pendingInvitations.find(
      (item) => item.recipientEmail.toLowerCase() === managedUser.email.toLowerCase(),
    )
    if (match) {
      void handleCancelInvitation(match.id)
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

  const initializing = loading && administrators.length === 0 && pendingInvitations.length === 0

  const inviteAdministratorModal =
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
                    Invite Administrator
                  </div>
                  <div id="am-invite-modal-subtitle" className="dash-modal-sub">
                    Send a single-use activation link for a network-wide Administrator account.
                  </div>
                </div>
                <button
                  type="button"
                  className="dash-modal-close"
                  onClick={handleCloseInviteModal}
                  aria-label="Close invite administrator modal"
                  disabled={sending}
                >
                  <i className="ti ti-x" aria-hidden="true"></i>
                </button>
              </div>

              <InvitationComposer
                chips={emailChips}
                emailDraft={emailDraft}
                role="administrator"
                selectedInstitution={null}
                canChooseRole={false}
                networkWide
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
            <h1>Administrator Management</h1>
            <p>Invite and manage network-level administrator accounts.</p>
          </div>
          <button type="button" className="im-add-btn" onClick={handleOpenInviteModal}>
            <i className="ti ti-user-plus" aria-hidden="true"></i>
            Invite Administrator
          </button>
        </header>

        {pendingTransfer && (
          <div className="alert alert-info" role="status">
            <i className="ti ti-shield-up" aria-hidden="true"></i>
            <div>
              <strong>Super Administrator transfer pending.</strong>
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
              users={administrators}
              loading={loading}
              updatingUserId={updatingUserId}
              resendingUserId={updatingUserId}
              onToggleUserStatus={handleToggleUserStatus}
              onDeleteUser={handleDeleteUser}
              onCancelInvitation={handleCancelInvitationFromUser}
              onResendInvitation={handleResendInvitationFromUser}
              onRequestSuperAdminTransfer={handleRequestTransfer}
              showRoleControls={false}
              showInstitutionColumn={false}
              showFilterPills
              showHeader={false}
              variant="directory"
              userColumnLabel="Administrator"
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
          onConfirm={confirmDialog.onConfirm}
          onCancel={() => setConfirmDialog(null)}
        />
      )}
      {inviteAdministratorModal}
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
