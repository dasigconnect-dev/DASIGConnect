import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Navigate } from 'react-router-dom'
import {
  cancelInvitation,
  deleteUser,
  inviteUser,
  listInstitutions,
  listPendingInvitations,
  listUsers,
  resendInvitation,
  updateUserStatus,
} from '../../api/authApi'
import type { PendingInvitationResponse, UserProfileResponse } from '../../api/authApi'
import type { User } from '../../types/auth.types'
import ConfirmDialog from './components/ConfirmDialog'
import DeliveryIssuesAlert from './components/DeliveryIssuesAlert'
import InstitutionUsersCard from './components/InstitutionUsersCard'
import InvitationComposer from './components/InvitationComposer'
import { SkeletonBlock } from './components/LoadingPrimitives'
import PendingInvitationsCard from './components/PendingInvitationsCard'
import BrandedSelect from '../../components/ui/BrandedSelect'
import type { InstitutionOption, InviteResults, InviteRole } from './types'
import { toInstitutionOption } from './types'
import { useToast } from '../../context/ToastContext'
import { getUserDisplayName } from '../../lib/userIdentity'

type ActiveTab = 'invitations' | 'users'

interface ConfirmDialogState {
  title: string
  message: string
  confirmLabel: string
  dangerous: boolean
  onConfirm: () => void
  onCancel?: () => void
}

interface UserInvitationsScreenProps {
  user: User
}

export default function UserInvitationsScreen({ user }: UserInvitationsScreenProps) {
  const toast = useToast()

  // Institution context (shared across tabs)
  const [institutions, setInstitutions] = useState<InstitutionOption[]>([])
  const [institutionsLoading, setInstitutionsLoading] = useState(false)
  const [selectedInstitutionId, setSelectedInstitutionId] = useState('')
  const [institutionError, setInstitutionError] = useState('')

  // Tab navigation
  const [activeTab, setActiveTab] = useState<ActiveTab>('invitations')

  // Invitation composer
  const [emailChips, setEmailChips] = useState<string[]>([])
  const [emailDraft, setEmailDraft] = useState('')
  const [inviteRole, setInviteRole] = useState<InviteRole>(
    user.role === 'validator' ? 'contributor' : null,
  )
  const [inviteResults, setInviteResults] = useState<InviteResults | null>(null)
  const [sending, setSending] = useState(false)

  // Management data
  const [pendingInvitations, setPendingInvitations] = useState<PendingInvitationResponse[]>([])
  const [managedUsers, setManagedUsers] = useState<UserProfileResponse[]>([])
  const [managementLoading, setManagementLoading] = useState(false)
  const [managementError, setManagementError] = useState('')
  const [resendingInvitationId, setResendingInvitationId] = useState<string | null>(null)
  const [cancellingInvitationId, setCancellingInvitationId] = useState<string | null>(null)
  const [updatingUserId, setUpdatingUserId] = useState<string | null>(null)

  // Confirm dialog (replaces window.confirm)
  const [confirmDialog, setConfirmDialog] = useState<ConfirmDialogState | null>(null)

  const selectedInstitution = useMemo(
    () => institutions.find((inst) => inst.id === selectedInstitutionId) ?? null,
    [institutions, selectedInstitutionId],
  )
  const isValidatorWorkspace = user.role === 'validator'
  const visibleManagedUsers = useMemo(
    () =>
      isValidatorWorkspace
        ? managedUsers.filter((managedUser) => managedUser.role.toLowerCase() === 'contributor')
        : managedUsers,
    [isValidatorWorkspace, managedUsers],
  )
  const visiblePendingInvitations = useMemo(
    () =>
      isValidatorWorkspace
        ? pendingInvitations.filter((invite) => invite.assignedRole.toLowerCase() === 'contributor')
        : pendingInvitations,
    [isValidatorWorkspace, pendingInvitations],
  )

  const initializing =
    institutionsLoading ||
    (managementLoading && managedUsers.length === 0 && pendingInvitations.length === 0)

  // Load institutions on mount
  useEffect(() => {
    if (user.role === 'contributor') return

    if (user.role === 'validator' && user.institutionId) {
      setInstitutions([
        {
          id: user.institutionId,
          name: user.inst?.trim() || 'Institution',
          code: '',
          emailDomain: '',
        },
      ])
      setSelectedInstitutionId(user.institutionId)
      setInviteRole('contributor')
      return
    }

    setInstitutionsLoading(true)
    setInstitutionError('')
    listInstitutions()
      .then((response) => {
        const nextInstitutions = response.data.map(toInstitutionOption)
        setInstitutions(nextInstitutions)
        setSelectedInstitutionId((current) => current || nextInstitutions[0]?.id || '')
      })
      .catch((error: unknown) => {
        setInstitutions([])
        setInstitutionError(getApiErrorMessage(error, 'Unable to load institutions.'))
      })
      .finally(() => setInstitutionsLoading(false))
  }, [user])

  // Load management data when institution changes
  useEffect(() => {
    if (!selectedInstitutionId) return
    void loadManagementLists(selectedInstitutionId)
  }, [selectedInstitutionId])

  if (user.role === 'contributor') {
    return <Navigate to="/dashboard" replace />
  }

  async function loadManagementLists(institutionId: string) {
    setManagementLoading(true)
    setManagementError('')
    try {
      const [usersResponse, pendingResponse] = await Promise.all([
        listUsers(institutionId),
        listPendingInvitations(institutionId),
      ])
      setManagedUsers(usersResponse.data)
      setPendingInvitations(pendingResponse.data)
    } catch (error: unknown) {
      setManagedUsers([])
      setPendingInvitations([])
      setManagementError(getApiErrorMessage(error, 'Unable to load users and invitations.'))
    } finally {
      setManagementLoading(false)
    }
  }

  async function handleSendInvitations(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setInviteResults(null)

    const inviteEmails = emailChips
    if (inviteEmails.length === 0) {
      setInviteResults({
        total: 0,
        success: [],
        failed: [{ email: 'Batch', reason: 'Add at least one recipient email.' }],
      })
      return
    }

    if (!selectedInstitutionId) {
      setInviteResults({
        total: inviteEmails.length,
        success: [],
        failed: [{ email: 'Batch', reason: 'Select an institution before sending.' }],
      })
      return
    }

    if (inviteEmails.length > 15) {
      setInviteResults({
        total: inviteEmails.length,
        success: [],
        failed: [
          { email: 'Batch', reason: 'Batch exceeds maximum of 15 invitations.' },
        ],
      })
      return
    }

    // Button is disabled when role is null, but guard here for type safety
    if (!inviteRole) return
    if (user.role === 'validator' && inviteRole !== 'contributor') {
      setInviteResults({
        total: inviteEmails.length,
        success: [],
        failed: [{ email: 'Batch', reason: 'Validators can only invite contributors.' }],
      })
      return
    }

    if (inviteRole === 'validator') {
      const proceed = await confirmValidatorInvite()
      if (!proceed) return
    }

    setSending(true)
    const success: string[] = []
    const failed: InviteResults['failed'] = []
    try {
      for (const email of inviteEmails) {
        if (!isValidEmail(email)) {
          failed.push({ email, reason: 'Invalid email address.' })
          continue
        }
        try {
          const response = await inviteUser({
            recipientEmail: email,
            institutionId: selectedInstitutionId,
            assignedRole: inviteRole,
          })
          if (response.data.emailDelivered) {
            success.push(email)
          } else {
            failed.push({
              email,
              reason: 'Invitation created, but email delivery failed.',
              invitationUrl: response.data.invitationUrl,
            })
          }
        } catch (error: unknown) {
          failed.push({ email, reason: getApiErrorMessage(error, 'Invitation failed.') })
        }
      }

      if (failed.length === 0) {
        // All delivered — use toast, no inline banner needed
        toast.success(
          `${success.length} invitation${success.length === 1 ? '' : 's'} sent successfully.`,
        )
      } else {
        // Partial failures — show inline banner so admin can review details and retry
        if (success.length > 0) {
          toast.info(`${success.length} of ${inviteEmails.length} invitation${success.length === 1 ? '' : 's'} sent.`)
        }
        setInviteResults({ total: inviteEmails.length, success, failed })
      }
      setEmailChips([])
      setEmailDraft('')
      setInviteRole(user.role === 'validator' ? 'contributor' : null)
      if (selectedInstitutionId) {
        await loadManagementLists(selectedInstitutionId)
      }
    } finally {
      setSending(false)
    }
  }

  function confirmValidatorInvite(): Promise<boolean> {
    const activeValidators = managedUsers.filter(
      (u) =>
        u.role.toLowerCase() === 'validator' && u.accountState.toLowerCase() === 'active',
    )
    if (activeValidators.length === 0) return Promise.resolve(true)

    const name = selectedInstitution?.name || 'this institution'
    return new Promise((resolve) => {
      setConfirmDialog({
        title: 'Invite Additional Validator?',
        message: `${name} already has ${activeValidators.length} active validator${activeValidators.length === 1 ? '' : 's'}. Do you still want to send this invitation?`,
        confirmLabel: 'Yes, invite validator',
        dangerous: false,
        onConfirm: () => {
          setConfirmDialog(null)
          resolve(true)
        },
        onCancel: () => {
          resolve(false)
        },
      })
    })
  }

  async function handleResendInvitation(id: string) {
    setResendingInvitationId(id)
    try {
      await resendInvitation(id)
      toast.success('Invitation resent.')
      if (selectedInstitutionId) {
        await loadManagementLists(selectedInstitutionId)
      }
    } catch (error: unknown) {
      toast.error(getApiErrorMessage(error, 'Unable to resend invitation.'))
    } finally {
      setResendingInvitationId(null)
    }
  }

  function handleToggleUserStatus(managedUser: UserProfileResponse) {
    const nextState = managedUser.accountState.toLowerCase() === 'inactive' ? 'active' : 'inactive'
    const verb = nextState === 'inactive' ? 'Deactivate' : 'Reactivate'

    setConfirmDialog({
      title: `${verb} User`,
      message: `Are you sure you want to ${verb.toLowerCase()} ${getUserDisplayName(managedUser)}?`,
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
      setManagedUsers((current) =>
        current.map((item) => (item.id === managedUser.id ? response.data : item)),
      )
      toast.success(nextState === 'inactive' ? 'Account deactivated.' : 'Account reactivated.')
    } catch (error: unknown) {
      toast.error(getApiErrorMessage(error, 'Unable to update account status.'))
    } finally {
      setUpdatingUserId(null)
    }
  }

  function handleDeleteUser(managedUser: UserProfileResponse) {
    setConfirmDialog({
      title: 'Remove User',
      message: `Remove ${getUserDisplayName(managedUser)}? If they have existing content or media, their account will be deactivated to preserve data integrity. Otherwise it will be permanently deleted.`,
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
      if (response.data.action === 'deactivated') {
        setManagedUsers((current) =>
          current.map((item) =>
            item.id === managedUser.id ? { ...item, accountState: 'inactive' } : item,
          ),
        )
        toast.info('Account deactivated. Their content and media have been preserved.')
      } else {
        setManagedUsers((current) => current.filter((item) => item.id !== managedUser.id))
        toast.success('User permanently removed.')
      }
    } catch (error: unknown) {
      toast.error(getApiErrorMessage(error, 'Unable to remove user.'))
    } finally {
      setUpdatingUserId(null)
    }
  }

  function handleCancelInvitationFromUsers(managedUser: UserProfileResponse) {
    setConfirmDialog({
      title: 'Cancel Invitation',
      message: `Cancel the pending invitation for ${managedUser.email}?`,
      confirmLabel: 'Cancel invitation',
      dangerous: true,
      onConfirm: () => {
        setConfirmDialog(null)
        void executeCancelInvitationByEmail(managedUser)
      },
    })
  }

  async function executeCancelInvitationByEmail(managedUser: UserProfileResponse) {
    setUpdatingUserId(managedUser.id)
    try {
      // Find the matching pending invitation by email and cancel it
      const match = pendingInvitations.find(
        (inv) => inv.recipientEmail.toLowerCase() === managedUser.email.toLowerCase(),
      )
      if (match) {
        await cancelInvitation(match.id)
      }
      setManagedUsers((current) => current.filter((item) => item.id !== managedUser.id))
      if (selectedInstitutionId) {
        await loadManagementLists(selectedInstitutionId)
      }
      toast.success('Invitation cancelled.')
    } catch (error: unknown) {
      toast.error(getApiErrorMessage(error, 'Unable to cancel invitation.'))
    } finally {
      setUpdatingUserId(null)
    }
  }

  async function handleCancelInvitation(id: string) {
    setCancellingInvitationId(id)
    try {
      await cancelInvitation(id)
      setPendingInvitations((current) => current.filter((item) => item.id !== id))
      toast.success('Invitation cancelled.')
    } catch (error: unknown) {
      toast.error(getApiErrorMessage(error, 'Unable to cancel invitation.'))
    } finally {
      setCancellingInvitationId(null)
    }
  }

  function handleResubmitFailed() {
    if (!inviteResults) return
    const failedEmails = inviteResults.failed.map((f) => f.email).filter(isValidEmail)
    setEmailChips(failedEmails)
    setEmailDraft('')
    setInviteResults(null)
  }

  return (
    <div className="um-screen">
      <main className="um-body">
        {/* Page header */}
        <header className="um-page-header">
          <div>
            <h1>User Management</h1>
            <p>Manage invitations and user accounts across institutions.</p>
          </div>
        </header>

        {/* Institution context bar */}
        <div className="um-context-bar">
          <div className="um-context-label">
            <i className="ti ti-building" aria-hidden="true"></i>
            <span>Institution</span>
          </div>

          {institutionsLoading ? (
            <SkeletonBlock className="um-skeleton-line is-wide" />
          ) : user.role === 'validator' ? (
            <span className="um-inst-name-pill">
              {selectedInstitution?.name || 'Institution'}
            </span>
          ) : (
            <BrandedSelect
              className="um-inst-select"
              value={selectedInstitutionId}
              onChange={setSelectedInstitutionId}
              disabled={institutions.length <= 1}
              ariaLabel="Select institution"
              placeholder="Select institution"
              options={
                institutions.length === 0
                  ? [{ value: '', label: 'No institutions available', disabled: true }]
                  : institutions.map((inst) => ({ value: inst.id, label: inst.name }))
              }
            />
          )}
        </div>

        {institutionError && (
          <div className="alert alert-err" role="alert">
            <i className="ti ti-alert-circle" aria-hidden="true"></i>
            <div>{institutionError}</div>
          </div>
        )}

        {managementError && (
          <div className="alert alert-err" role="alert">
            <i className="ti ti-alert-circle" aria-hidden="true"></i>
            <div>{managementError}</div>
          </div>
        )}

        {/* Tab navigation */}
        <div className="um-tabs" role="tablist" aria-label="User Management sections">
          <button
            type="button"
            role="tab"
            id="tab-invitations"
            aria-controls="panel-invitations"
            aria-selected={activeTab === 'invitations'}
            className={`um-tab${activeTab === 'invitations' ? ' is-active' : ''}`}
            onClick={() => setActiveTab('invitations')}
          >
            <i className="ti ti-send" aria-hidden="true"></i>
            Invitations
            {visiblePendingInvitations.length > 0 && (
              <span className="um-tab-badge">{visiblePendingInvitations.length}</span>
            )}
          </button>
          <button
            type="button"
            role="tab"
            id="tab-users"
            aria-controls="panel-users"
            aria-selected={activeTab === 'users'}
            className={`um-tab${activeTab === 'users' ? ' is-active' : ''}`}
            onClick={() => setActiveTab('users')}
          >
            <i className="ti ti-users" aria-hidden="true"></i>
            Users
            {visibleManagedUsers.length > 0 && (
              <span className="um-tab-badge is-neutral">{visibleManagedUsers.length}</span>
            )}
          </button>
        </div>

        {/* Invitations tab */}
        {activeTab === 'invitations' && (
          <div
            id="panel-invitations"
            role="tabpanel"
            aria-labelledby="tab-invitations"
            className="um-tab-panel"
          >
            <InvitationComposer
              chips={emailChips}
              emailDraft={emailDraft}
              role={inviteRole}
              selectedInstitution={selectedInstitution}
              canChooseRole={user.role === 'admin'}
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
              onRoleChange={setInviteRole}
              onSubmit={(event) => void handleSendInvitations(event)}
            />

            {inviteResults && (
              <DeliveryIssuesAlert
                results={inviteResults}
                onResubmitFailed={handleResubmitFailed}
              />
            )}

            <PendingInvitationsCard
              invitations={visiblePendingInvitations}
              institutions={institutions}
              loading={managementLoading}
              resendingInvitationId={resendingInvitationId}
              cancellingInvitationId={cancellingInvitationId}
              onResend={(id) => void handleResendInvitation(id)}
              onCancelInvitation={(id) => void handleCancelInvitation(id)}
              showRoleControls={!isValidatorWorkspace}
            />
          </div>
        )}

        {/* Users tab */}
        {activeTab === 'users' && (
          <div
            id="panel-users"
            role="tabpanel"
            aria-labelledby="tab-users"
            className="um-tab-panel"
          >
            {!initializing && (
              <div className="um-metrics-row">
                <MetricCard
                  icon="ti ti-users"
                  label={isValidatorWorkspace ? 'Total Contributors' : 'Total Users'}
                  value={visibleManagedUsers.length}
                  loading={managementLoading && managedUsers.length === 0}
                />
                <MetricCard
                  icon="ti ti-user-check"
                  label="Active"
                  value={visibleManagedUsers.filter((u) => u.accountState.toLowerCase() === 'active').length}
                  loading={managementLoading && managedUsers.length === 0}
                  accent="green"
                />
                {!isValidatorWorkspace && (
                  <MetricCard
                    icon="ti ti-shield-check"
                    label="Validators"
                    value={managedUsers.filter((u) => u.role.toLowerCase() === 'validator').length}
                    loading={managementLoading && managedUsers.length === 0}
                    accent="purple"
                  />
                )}
                <MetricCard
                  icon="ti ti-pencil"
                  label="Contributors"
                  value={visibleManagedUsers.filter((u) => u.role.toLowerCase() === 'contributor').length}
                  loading={managementLoading && managedUsers.length === 0}
                  accent="blue"
                />
                <MetricCard
                  icon="ti ti-clock-pause"
                  label="Pending Invites"
                  value={visiblePendingInvitations.length}
                  loading={managementLoading && pendingInvitations.length === 0}
                  accent={visiblePendingInvitations.length > 0 ? 'gold' : undefined}
                />
              </div>
            )}

            <InstitutionUsersCard
              currentUser={user}
              users={visibleManagedUsers}
              loading={managementLoading}
              updatingUserId={updatingUserId}
              onToggleUserStatus={handleToggleUserStatus}
              onDeleteUser={handleDeleteUser}
              onCancelInvitation={handleCancelInvitationFromUsers}
              showRoleControls={!isValidatorWorkspace}
            />
          </div>
        )}
      </main>

      {/* Confirm dialog */}
      {confirmDialog && (
        <ConfirmDialog
          title={confirmDialog.title}
          message={confirmDialog.message}
          confirmLabel={confirmDialog.confirmLabel}
          dangerous={confirmDialog.dangerous}
          onConfirm={confirmDialog.onConfirm}
          onCancel={() => {
            confirmDialog.onCancel?.()
            setConfirmDialog(null)
          }}
        />
      )}
    </div>
  )
}

interface MetricCardProps {
  icon: string
  label: string
  value: number
  loading: boolean
  accent?: 'blue' | 'green' | 'gold' | 'purple'
}

function MetricCard({ icon, label, value, loading, accent }: MetricCardProps) {
  return (
    <div className={`um-metric${accent ? ` accent-${accent}` : ''}`}>
      <div className="um-metric-icon">
        <i className={icon} aria-hidden="true"></i>
      </div>
      <div className="um-metric-body">
        <span className="um-metric-label">{label}</span>
        {loading ? (
          <SkeletonBlock className="um-skeleton-number" />
        ) : (
          <strong className="um-metric-value">{value}</strong>
        )}
      </div>
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
