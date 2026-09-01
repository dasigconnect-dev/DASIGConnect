import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { createPortal } from 'react-dom'
import { Navigate } from 'react-router-dom'
import {
  cancelInvitationByUser,
  changeUserRole,
  deleteUser,
  eraseUserData,
  inviteUser,
  listAdmins,
  listInstitutions,
  listNetworkUsers,
  listPendingNetworkInvitations,
  reassignContributor,
  resendInvitation,
  updateUserStatus,
} from '../../api/authApi'
import type { PendingInvitationResponse, UserProfileResponse } from '../../api/authApi'
import type { User } from '../../types/auth.types'
import BrandedSelect from '../../components/ui/BrandedSelect'
import ChangeRoleModal from './components/ChangeRoleModal'
import ConfirmDialog from './components/ConfirmDialog'
import DeliveryIssuesAlert from './components/DeliveryIssuesAlert'
import InstitutionUsersCard from './components/InstitutionUsersCard'
import InvitationComposer from './components/InvitationComposer'
import { SkeletonBlock } from './components/LoadingPrimitives'
import type { InstitutionOption, InviteResults, InviteRole } from './types'
import { toInstitutionOption } from './types'
import { useToast } from '../../context/ToastContext'
import { getUserDisplayName } from '../../lib/userIdentity'

interface ConfirmDialogState {
  title: string
  message: string
  confirmLabel: string
  dangerous: boolean
  requireTypedConfirmation?: string
  onConfirm: () => void
  onCancel?: () => void
}

interface UserManagementScreenProps {
  user: User
}

export default function UserManagementScreen({ user }: UserManagementScreenProps) {
  const toast = useToast()

  const [institutions, setInstitutions] = useState<InstitutionOption[]>([])
  const [institutionsLoading, setInstitutionsLoading] = useState(false)
  const [institutionError, setInstitutionError] = useState('')

  // Invitation composer, shown in a modal opened from the header button.
  const [showInviteModal, setShowInviteModal] = useState(false)
  // Destination institution must be chosen explicitly since this view spans all institutions.
  const [inviteInstitutionId, setInviteInstitutionId] = useState('')
  const [emailChips, setEmailChips] = useState<string[]>([])
  const [emailDraft, setEmailDraft] = useState('')
  const [inviteRole, setInviteRole] = useState<InviteRole>(null)
  const [inviteResults, setInviteResults] = useState<InviteResults | null>(null)
  const [sending, setSending] = useState(false)

  // Network-wide data
  const [pendingInvitations, setPendingInvitations] = useState<PendingInvitationResponse[]>([])
  const [managedUsers, setManagedUsers] = useState<UserProfileResponse[]>([])
  const [managementLoading, setManagementLoading] = useState(false)
  const [managementError, setManagementError] = useState('')
  const [updatingUserId, setUpdatingUserId] = useState<string | null>(null)

  const [confirmDialog, setConfirmDialog] = useState<ConfirmDialogState | null>(null)

  // Reassign contributor modal
  const [reassignUser, setReassignUser] = useState<UserProfileResponse | null>(null)
  const [reassignTargetId, setReassignTargetId] = useState('')
  const [reassignLoading, setReassignLoading] = useState(false)
  const [reassignError, setReassignError] = useState('')

  // Change role modal
  const [roleUser, setRoleUser] = useState<UserProfileResponse | null>(null)
  const [roleBusy, setRoleBusy] = useState(false)
  const [roleError, setRoleError] = useState('')
  // Owner status + open admin slots, from the admins roster (admin-only endpoint).
  const [isOwner, setIsOwner] = useState(false)
  const [adminSlotsOpen, setAdminSlotsOpen] = useState(0)

  const selectedInviteInstitution = useMemo(
    () => institutions.find((inst) => inst.id === inviteInstitutionId) ?? null,
    [institutions, inviteInstitutionId],
  )

  const initializing = institutionsLoading || (managementLoading && managedUsers.length === 0 && pendingInvitations.length === 0)

  useEffect(() => {
    if (user.role !== 'admin') return
    setInstitutionsLoading(true)
    setInstitutionError('')
    listInstitutions()
      .then((response) => {
        const nextInstitutions = response.data.map(toInstitutionOption)
        setInstitutions(nextInstitutions)
        setInviteInstitutionId((current) => current || nextInstitutions[0]?.id || '')
      })
      .catch((error: unknown) => {
        setInstitutions([])
        setInstitutionError(getApiErrorMessage(error, 'Unable to load institutions.'))
      })
      .finally(() => setInstitutionsLoading(false))
  }, [user.role])

  useEffect(() => {
    if (user.role !== 'admin') return
    void loadManagementLists()
  }, [user.role])

  useEffect(() => {
    if (!showInviteModal || typeof document === 'undefined') return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !sending) handleCloseInviteModal()
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [showInviteModal, sending])

  if (user.role !== 'admin') {
    return <Navigate to="/dashboard" replace />
  }

  function handleOpenInviteModal() {
    setEmailChips([])
    setEmailDraft('')
    setInviteRole(null)
    setInviteResults(null)
    setShowInviteModal(true)
  }

  function handleCloseInviteModal() {
    if (sending) return
    setShowInviteModal(false)
    setEmailChips([])
    setEmailDraft('')
    setInviteRole(null)
    setInviteResults(null)
  }

  async function loadManagementLists() {
    setManagementLoading(true)
    setManagementError('')
    try {
      const [usersResponse, pendingResponse, adminsResponse] = await Promise.all([
        listNetworkUsers(),
        listPendingNetworkInvitations(),
        listAdmins(),
      ])
      setManagedUsers(usersResponse.data)
      setPendingInvitations(pendingResponse.data)

      const admins = adminsResponse.data
      const me = admins.find((a) => a.email.toLowerCase() === user.email.toLowerCase())
      setIsOwner(me?.adminOwner === true)
      const activeAdmins = admins.filter((a) => a.accountState.toLowerCase() === 'active').length
      setAdminSlotsOpen(Math.max(0, 3 - activeAdmins))
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

    if (emailChips.length === 0) {
      setInviteResults({
        total: 0,
        success: [],
        failed: [{ email: 'Batch', reason: 'Add at least one recipient email.' }],
      })
      return
    }
    if (inviteRole !== 'moderator' && !inviteInstitutionId) {
      setInviteResults({
        total: emailChips.length,
        success: [],
        failed: [{ email: 'Batch', reason: 'Select a destination institution before sending.' }],
      })
      return
    }
    if (emailChips.length > 15) {
      setInviteResults({
        total: emailChips.length,
        success: [],
        failed: [{ email: 'Batch', reason: 'Batch exceeds maximum of 15 invitations.' }],
      })
      return
    }
    if (!inviteRole) return

    setSending(true)
    const success: string[] = []
    const failed: InviteResults['failed'] = []
    try {
      for (const email of emailChips) {
        if (!isValidEmail(email)) {
          failed.push({ email, reason: 'Invalid email address.' })
          continue
        }
        try {
          const response = await inviteUser({
            recipientEmail: email,
            // Moderators are network-wide — no destination institution.
            institutionId: inviteRole === 'moderator' ? null : inviteInstitutionId,
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
        toast.success(`${success.length} invitation${success.length === 1 ? '' : 's'} sent successfully.`)
      } else {
        if (success.length > 0) {
          toast.info(`${success.length} of ${emailChips.length} invitation${success.length === 1 ? '' : 's'} sent.`)
        }
        setInviteResults({ total: emailChips.length, success, failed })
      }
      setEmailChips([])
      setEmailDraft('')
      setInviteRole(null)
      await loadManagementLists()
      if (failed.length === 0) {
        setShowInviteModal(false)
      }
    } finally {
      setSending(false)
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

  async function executeToggleUserStatus(managedUser: UserProfileResponse, nextState: 'active' | 'inactive') {
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
    const isErased = Boolean(managedUser.purgedAt)
    setConfirmDialog({
      title: isErased ? 'Remove Record' : 'Remove User',
      message: isErased
        ? `This account's personal data has already been erased. If it has any activity history (submissions, media, or audit records) it stays as an anonymised inactive row for the audit trail; only a footprint-free record is permanently deleted.`
        : `Remove ${getUserDisplayName(managedUser)}? If they have existing content or media, their account is deactivated to preserve data integrity. Otherwise it is permanently deleted and cannot be recovered.`,
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
      if (response.data.action === 'deactivated') {
        setManagedUsers((current) =>
          current.map((item) => (item.id === managedUser.id ? { ...item, accountState: 'inactive' } : item)),
        )
        toast.info(
          managedUser.purgedAt
            ? 'This record has activity history and is kept as an anonymised tombstone for the audit trail — it cannot be removed.'
            : 'Account deactivated. Their content and media have been preserved.',
        )
      } else {
        setManagedUsers((current) => current.filter((item) => item.id !== managedUser.id))
        toast.success(managedUser.purgedAt ? 'Record removed.' : 'User permanently removed.')
      }
    } catch (error: unknown) {
      toast.error(getApiErrorMessage(error, 'Unable to remove user.'))
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
      await loadManagementLists()
    } catch (error: unknown) {
      toast.error(getApiErrorMessage(error, 'Unable to erase personal data.'))
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
      await cancelInvitationByUser(managedUser.id)
      setManagedUsers((current) =>
        current.map((item) => (item.id === managedUser.id ? { ...item, accountState: 'cancelled' } : item)),
      )
      await loadManagementLists()
      toast.success('Invitation cancelled.')
    } catch (error: unknown) {
      toast.error(getApiErrorMessage(error, 'Unable to cancel invitation.'))
    } finally {
      setUpdatingUserId(null)
    }
  }

  async function handleResendInvitationFromUsers(managedUser: UserProfileResponse) {
    setUpdatingUserId(managedUser.id)
    try {
      const match = pendingInvitations.find(
        (inv) => inv.recipientEmail.toLowerCase() === managedUser.email.toLowerCase(),
      )
      const role = managedUser.role.toLowerCase()
      if (match) {
        await resendInvitation(match.id)
      } else if (role === 'moderator' || managedUser.institutionId) {
        // Moderators are network-wide, so a null institutionId is expected for them.
        await inviteUser({
          recipientEmail: managedUser.email,
          institutionId: role === 'moderator' ? null : managedUser.institutionId,
          assignedRole: role as 'contributor' | 'moderator',
        })
      }
      await loadManagementLists()
      toast.success(`Invitation resent to ${managedUser.email}.`)
    } catch (error: unknown) {
      toast.error(getApiErrorMessage(error, 'Unable to resend invitation.'))
    } finally {
      setUpdatingUserId(null)
    }
  }

  function handleResubmitFailed() {
    if (!inviteResults) return
    const failedEmails = inviteResults.failed.map((f) => f.email).filter(isValidEmail)
    setEmailChips(failedEmails)
    setEmailDraft('')
    setInviteResults(null)
  }

  // ── Reassign contributor ────────────────────────────────────────────────

  function handleOpenReassign(managedUser: UserProfileResponse) {
    if (managedUser.role.toLowerCase() !== 'contributor') {
      toast.error('Only contributor accounts can be reassigned. Moderators are managed per-institution.')
      return
    }
    setReassignUser(managedUser)
    setReassignTargetId('')
    setReassignError('')
  }

  function handleCloseReassign() {
    if (reassignLoading) return
    setReassignUser(null)
    setReassignTargetId('')
    setReassignError('')
  }

  async function handleConfirmReassign() {
    if (!reassignUser || !reassignTargetId) return
    setReassignLoading(true)
    setReassignError('')
    try {
      await reassignContributor(reassignUser.id, reassignTargetId)
      const targetInst = institutions.find((i) => i.id === reassignTargetId)
      toast.success(
        `${getUserDisplayName(reassignUser)} has been reassigned to ${targetInst?.name ?? 'the selected institution'}.`,
      )
      await loadManagementLists()
      handleCloseReassign()
    } catch (err: unknown) {
      setReassignError(getApiErrorMessage(err, 'Unable to reassign contributor.'))
    } finally {
      setReassignLoading(false)
    }
  }

  // ── Change role ─────────────────────────────────────────────────────────

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
      await loadManagementLists()
      setRoleUser(null)
    } catch (err: unknown) {
      setRoleError(getApiErrorMessage(err, 'Unable to change role.'))
    } finally {
      setRoleBusy(false)
    }
  }

  return (
    <div className="um-screen">
      <main className="um-body">
        <header className="im-page-header">
          <div>
            <h1>User Management</h1>
            <p>Manage moderator and contributor accounts across every institution.</p>
          </div>
          <button type="button" className="im-add-btn" onClick={handleOpenInviteModal}>
            <i className="ti ti-user-plus" aria-hidden="true"></i>
            Invite User
          </button>
        </header>

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

        {!initializing && (
          <div className="um-metrics-row">
            <MetricCard
              icon="ti ti-users"
              label="Total Users"
              value={managedUsers.length}
              loading={managementLoading && managedUsers.length === 0}
            />
            <MetricCard
              icon="ti ti-user-check"
              label="Active"
              value={managedUsers.filter((u) => u.accountState.toLowerCase() === 'active').length}
              loading={managementLoading && managedUsers.length === 0}
              accent="green"
            />
            <MetricCard
              icon="ti ti-shield-check"
              label="Moderators"
              value={managedUsers.filter((u) => u.role.toLowerCase() === 'moderator').length}
              loading={managementLoading && managedUsers.length === 0}
              accent="purple"
            />
            <MetricCard
              icon="ti ti-pencil"
              label="Contributors"
              value={managedUsers.filter((u) => u.role.toLowerCase() === 'contributor').length}
              loading={managementLoading && managedUsers.length === 0}
              accent="blue"
            />
            <MetricCard
              icon="ti ti-clock-pause"
              label="Pending Invites"
              value={pendingInvitations.length}
              loading={managementLoading && pendingInvitations.length === 0}
              accent={pendingInvitations.length > 0 ? 'gold' : undefined}
            />
          </div>
        )}

        <InstitutionUsersCard
          currentUser={user}
          users={managedUsers}
          loading={managementLoading}
          updatingUserId={updatingUserId}
          onToggleUserStatus={handleToggleUserStatus}
          onDeleteUser={handleDeleteUser}
          onCancelInvitation={handleCancelInvitationFromUsers}
          onResendInvitation={handleResendInvitationFromUsers}
          onReassign={handleOpenReassign}
          onChangeRole={handleOpenChangeRole}
          onEraseData={isOwner ? handleEraseData : undefined}
          showRoleControls
          showInstitutionColumn
          title="All Users"
          description="Moderator and contributor accounts across every institution."
        />
      </main>

      {showInviteModal &&
        typeof document !== 'undefined' &&
        createPortal(
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
              aria-labelledby="um-invite-modal-title"
              aria-describedby="um-invite-modal-subtitle"
            >
              <div className="dash-modal-header im-modal-header">
                <div>
                  <div id="um-invite-modal-title" className="dash-modal-title">
                    Invite User
                  </div>
                  <div id="um-invite-modal-subtitle" className="dash-modal-sub">
                    Send single-use activation links for moderator or contributor accounts.
                  </div>
                </div>
                <button
                  type="button"
                  className="dash-modal-close"
                  onClick={handleCloseInviteModal}
                  aria-label="Close invite user modal"
                  disabled={sending}
                >
                  <i className="ti ti-x" aria-hidden="true"></i>
                </button>
              </div>

              {inviteRole !== 'moderator' && (
                <div className="um-context-bar">
                  <div className="um-context-label">
                    <i className="ti ti-building" aria-hidden="true"></i>
                    <span>Invite to institution</span>
                  </div>
                  {institutionsLoading ? (
                    <SkeletonBlock className="um-skeleton-line is-wide" />
                  ) : (
                    <BrandedSelect
                      className="um-inst-select"
                      value={inviteInstitutionId}
                      onChange={setInviteInstitutionId}
                      disabled={institutions.length === 0}
                      ariaLabel="Select destination institution"
                      placeholder="Select institution"
                      options={
                        institutions.length === 0
                          ? [{ value: '', label: 'No institutions available', disabled: true }]
                          : institutions.map((inst) => ({ value: inst.id, label: inst.name }))
                      }
                    />
                  )}
                </div>
              )}

              <InvitationComposer
                chips={emailChips}
                emailDraft={emailDraft}
                role={inviteRole}
                selectedInstitution={inviteRole === 'moderator' ? null : selectedInviteInstitution}
                canChooseRole
                networkWide={inviteRole === 'moderator'}
                embedded
                sending={sending}
                onDraftChange={setEmailDraft}
                onAddChip={(email) => {
                  if (!emailChips.includes(email.toLowerCase())) {
                    setEmailChips((prev) => [...prev, email.toLowerCase()])
                  }
                }}
                onRemoveChip={(index) => setEmailChips((prev) => prev.filter((_, i) => i !== index))}
                onRoleChange={setInviteRole}
                onSubmit={(event) => void handleSendInvitations(event)}
              />

              {inviteResults && (
                <DeliveryIssuesAlert results={inviteResults} onResubmitFailed={handleResubmitFailed} />
              )}
            </div>
          </div>,
          document.body,
        )}

      {confirmDialog && (
        <ConfirmDialog
          title={confirmDialog.title}
          message={confirmDialog.message}
          confirmLabel={confirmDialog.confirmLabel}
          dangerous={confirmDialog.dangerous}
          requireTypedConfirmation={confirmDialog.requireTypedConfirmation}
          onConfirm={confirmDialog.onConfirm}
          onCancel={() => {
            confirmDialog.onCancel?.()
            setConfirmDialog(null)
          }}
        />
      )}

      {reassignUser &&
        typeof document !== 'undefined' &&
        createPortal(
          <div className="dash-modal-backdrop im-modal-backdrop" onClick={handleCloseReassign} role="presentation">
            <div
              className="dash-modal-card im-reassign-modal-card"
              onClick={(event) => event.stopPropagation()}
              role="dialog"
              aria-modal="true"
              aria-labelledby="um-reassign-modal-title"
            >
              <div className="dash-modal-header im-modal-header">
                <div>
                  <div id="um-reassign-modal-title" className="dash-modal-title">
                    Reassign Contributor
                  </div>
                  <div className="dash-modal-sub">
                    Move <strong>{getUserDisplayName(reassignUser)}</strong> to a different institution.
                  </div>
                </div>
                <button
                  type="button"
                  className="dash-modal-close"
                  onClick={handleCloseReassign}
                  aria-label="Close reassign modal"
                  disabled={reassignLoading}
                >
                  <i className="ti ti-x" aria-hidden="true"></i>
                </button>
              </div>

              <div className="dash-modal-body im-modal-body">
                <div className="im-reassign-notice">
                  <i className="ti ti-info-circle" aria-hidden="true"></i>
                  <span>
                    The contributor's Row-Level Security scoping will be updated to the new institution. Historical
                    submissions remain attributed to their original institution.
                  </span>
                </div>

                <div className="dash-field">
                  <label className="dash-field-label">Target Institution</label>
                  <BrandedSelect
                    value={reassignTargetId}
                    onChange={(value) => {
                      setReassignTargetId(value)
                      setReassignError('')
                    }}
                    disabled={reassignLoading}
                    placeholder="— Select an institution —"
                    options={institutions
                      .filter((inst) => inst.status === 'active' && inst.id !== reassignUser.institutionId)
                      .map((inst) => ({ value: inst.id, label: inst.name }))}
                  />
                </div>

                {reassignError && (
                  <div className="alert alert-err im-modal-alert" role="alert">
                    <i className="ti ti-alert-circle" aria-hidden="true"></i>
                    <div>{reassignError}</div>
                  </div>
                )}

                <div className="dash-modal-actions im-modal-actions">
                  <button type="button" className="btn-ghost" onClick={handleCloseReassign} disabled={reassignLoading}>
                    Cancel
                  </button>
                  <button
                    type="button"
                    className="btn-primary"
                    disabled={!reassignTargetId || reassignLoading}
                    onClick={() => void handleConfirmReassign()}
                  >
                    {reassignLoading ? (
                      <>
                        <i className="ti ti-loader-2 im-spin" aria-hidden="true"></i>
                        Reassigning...
                      </>
                    ) : (
                      'Reassign'
                    )}
                  </button>
                </div>
              </div>
            </div>
          </div>,
          document.body,
        )}

      {roleUser && (
        <ChangeRoleModal
          user={roleUser}
          institutions={institutions}
          isOwner={isOwner}
          adminSlotsOpen={adminSlotsOpen}
          busy={roleBusy}
          error={roleError}
          onConfirm={(role, institutionId) => void handleConfirmChangeRole(role, institutionId)}
          onClose={handleCloseChangeRole}
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
        {loading ? <SkeletonBlock className="um-skeleton-number" /> : <strong className="um-metric-value">{value}</strong>}
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
