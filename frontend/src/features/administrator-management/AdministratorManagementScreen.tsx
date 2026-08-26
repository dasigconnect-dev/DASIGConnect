import { useEffect, useMemo, useState, type FormEvent } from 'react'
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
import { SkeletonBlock } from '../user-management/components/LoadingPrimitives'
import PendingInvitationsCard from '../user-management/components/PendingInvitationsCard'
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

export default function AdministratorManagementScreen({
  user,
  onProfileUpdated,
}: AdministratorManagementScreenProps) {
  const toast = useToast()
  const [administrators, setAdministrators] = useState<UserProfileResponse[]>([])
  const [pendingInvitations, setPendingInvitations] = useState<PendingInvitationResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [email, setEmail] = useState('')
  const [sending, setSending] = useState(false)
  const [inviteResults, setInviteResults] = useState<InviteResults | null>(null)
  const [updatingUserId, setUpdatingUserId] = useState<string | null>(null)
  const [resendingInvitationId, setResendingInvitationId] = useState<string | null>(null)
  const [cancellingInvitationId, setCancellingInvitationId] = useState<string | null>(null)
  const [confirmDialog, setConfirmDialog] = useState<ConfirmDialogState | null>(null)

  const currentAdminRecord = useMemo(
    () => administrators.find((item) => item.email.toLowerCase() === user.email.toLowerCase()) ?? null,
    [administrators, user.email],
  )
  const pendingTransfer = Boolean(currentAdminRecord?.superAdminTransferRequestedBy && currentAdminRecord.superAdminTransferExpiresAt)

  useEffect(() => {
    void loadAdministratorManagement()
  }, [])

  async function loadAdministratorManagement() {
    setLoading(true)
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

  async function handleInviteAdministrator(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const recipientEmail = email.trim().toLowerCase()
    setInviteResults(null)

    if (!isValidEmail(recipientEmail)) {
      setInviteResults({
        total: 1,
        success: [],
        failed: [{ email: recipientEmail || 'Invite', reason: 'Enter a valid administrator email.' }],
      })
      return
    }

    setSending(true)
    try {
      const response = await inviteUser({
        recipientEmail,
        institutionId: null,
        assignedRole: 'administrator',
      })
      setEmail('')
      if (response.data.emailDelivered) {
        toast.success('Administrator invitation sent.')
      } else {
        setInviteResults({
          total: 1,
          success: [],
          failed: [{
            email: recipientEmail,
            reason: 'Invitation created, but email delivery failed.',
            invitationUrl: response.data.invitationUrl,
          }],
        })
      }
      await loadAdministratorManagement()
    } catch (err: unknown) {
      setInviteResults({
        total: 1,
        success: [],
        failed: [{ email: recipientEmail, reason: getApiErrorMessage(err, 'Invitation failed.') }],
      })
    } finally {
      setSending(false)
    }
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
    setResendingInvitationId(id)
    try {
      await resendInvitation(id)
      toast.success('Invitation resent.')
      await loadAdministratorManagement()
    } catch (err: unknown) {
      toast.error(getApiErrorMessage(err, 'Unable to resend invitation.'))
    } finally {
      setResendingInvitationId(null)
    }
  }

  async function handleCancelInvitation(id: string) {
    setCancellingInvitationId(id)
    try {
      await cancelInvitation(id)
      toast.success('Invitation cancelled.')
      await loadAdministratorManagement()
    } catch (err: unknown) {
      toast.error(getApiErrorMessage(err, 'Unable to cancel invitation.'))
    } finally {
      setCancellingInvitationId(null)
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

  return (
    <div className="um-screen">
      <main className="um-body">
        <header className="um-page-header">
          <div>
            <h1>Administrator Management</h1>
            <p>Invite administrators and manage network-level administrator accounts.</p>
          </div>
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

        <section className="um-data-card">
          <div className="um-data-card-header">
            <div className="um-data-card-heading">
              <div className="um-data-card-title-group">
                <h2 className="um-data-card-title">Invite New Administrator</h2>
              </div>
              <p className="um-data-card-description">Administrator invitations are network-wide and expire after 72 hours.</p>
            </div>
          </div>
          <form className="um-composer-form" onSubmit={(event) => void handleInviteAdministrator(event)}>
            <div className="um-composer-section um-recipient-section">
              <div className="um-section-head">
                <div>
                  <label className="um-composer-label" htmlFor="administrator-invite-email">
                    Administrator Email
                  </label>
                  <div className="um-composer-label-hint">
                    Send a single-use activation link for a network-wide Administrator account.
                  </div>
                </div>
              </div>
              <input
                id="administrator-invite-email"
                className="um-chip-input"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="administrator@example.edu.ph"
                disabled={sending}
                aria-label="Administrator email"
              />
            </div>
            <div className="um-composer-footer">
              <button type="submit" className="um-send-btn" disabled={sending || !email.trim()}>
                <i className={sending ? 'ti ti-loader-2 um-spin' : 'ti ti-send'} aria-hidden="true"></i>
                {sending ? 'Sending...' : 'Send Invitation'}
              </button>
            </div>
          </form>
          {inviteResults && (
            <DeliveryIssuesAlert
              results={inviteResults}
              onResubmitFailed={() => {
                const failed = inviteResults.failed.find((item) => isValidEmail(item.email))
                if (failed) setEmail(failed.email)
                setInviteResults(null)
              }}
            />
          )}
        </section>

        {loading && administrators.length === 0 ? (
          <SkeletonBlock className="um-skeleton-line is-wide" />
        ) : (
          <>
            <InstitutionUsersCard
              currentUser={user}
              users={administrators}
              loading={loading}
              updatingUserId={updatingUserId}
              onToggleUserStatus={handleToggleUserStatus}
              onDeleteUser={handleDeleteUser}
              onCancelInvitation={handleCancelInvitationFromUser}
              onResendInvitation={handleResendInvitationFromUser}
              onRequestSuperAdminTransfer={handleRequestTransfer}
              showRoleControls
              showInstitutionColumn={false}
              title="Administrator Accounts"
              description="Network administrators can invite users and manage content workflows. Only the Super Administrator can remove or reactivate Administrator accounts."
              userColumnLabel="Administrator"
            />

            <PendingInvitationsCard
              invitations={pendingInvitations}
              institutions={[]}
              loading={loading}
              resendingInvitationId={resendingInvitationId}
              cancellingInvitationId={cancellingInvitationId}
              onResend={(id) => void handleResendInvitation(id)}
              onCancelInvitation={(id) => void handleCancelInvitation(id)}
              showRoleControls={false}
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
