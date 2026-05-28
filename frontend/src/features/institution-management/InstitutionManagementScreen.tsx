import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { createPortal } from 'react-dom'
import { useLocation, useNavigate } from 'react-router-dom'
import {
  cancelInvitation,
  createInstitution,
  deleteInstitution,
  deleteUser,
  getUserCounts,
  getPendingInvitationCount,
  listInstitutions,
  inviteUser,
  listPendingInvitations,
  listUsers,
  updateUserStatus,
} from '../../api/authApi'
import type { PendingInvitationResponse, UserProfileResponse } from '../../api/authApi'
import type { User } from '../../types/auth.types'
import ConfirmDialog from '../user-management/components/ConfirmDialog'
import DeliveryIssuesAlert from '../user-management/components/DeliveryIssuesAlert'
import InstitutionUsersCard from '../user-management/components/InstitutionUsersCard'
import InvitationComposer from '../user-management/components/InvitationComposer'
import { SkeletonBlock } from '../user-management/components/LoadingPrimitives'
import type { InviteResults, InviteRole } from '../user-management/types'
import { useToast } from '../../context/ToastContext'
import { getUserDisplayName } from '../../lib/userIdentity'

interface InstitutionWithStats {
  id: string
  name: string
  code: string
  emailDomain: string
  status: string
  contributors: number
  validators: number
  pendingInvitations: number
  statsLoading: boolean
}

interface AddFormState {
  name: string
  domain: string
  loading: boolean
  error: string
}

interface ConfirmDialogState {
  title: string
  message: string
  confirmLabel: string
  dangerous: boolean
  onConfirm: () => void
  onCancel?: () => void
}

interface InstitutionManagementScreenProps {
  user: User
}

interface InstitutionManagementLocationState {
  openAddInstitution?: boolean
}

export default function InstitutionManagementScreen({ user }: InstitutionManagementScreenProps) {
  const toast = useToast()
  const location = useLocation()
  const navigate = useNavigate()

  // List view
  const [institutions, setInstitutions] = useState<InstitutionWithStats[]>([])
  const [listLoading, setListLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [searchQuery, setSearchQuery] = useState('')

  // Detail view
  const [selectedInstitution, setSelectedInstitution] = useState<InstitutionWithStats | null>(null)
  const [activeTab, setActiveTab] = useState<'invitations' | 'users'>('invitations')

  // Add institution modal
  const [showAddModal, setShowAddModal] = useState(false)
  const [addForm, setAddForm] = useState<AddFormState>({
    name: '',
    domain: '',
    loading: false,
    error: '',
  })

  // Invitation state (detail view)
  const [emailChips, setEmailChips] = useState<string[]>([])
  const [emailDraft, setEmailDraft] = useState('')
  const [inviteRole, setInviteRole] = useState<InviteRole>(null)
  const [inviteResults, setInviteResults] = useState<InviteResults | null>(null)
  const [sending, setSending] = useState(false)

  // User management state (detail view)
  const [pendingInvitations, setPendingInvitations] = useState<PendingInvitationResponse[]>([])
  const [managedUsers, setManagedUsers] = useState<UserProfileResponse[]>([])
  const [managementLoading, setManagementLoading] = useState(false)
  const [managementError, setManagementError] = useState('')
const [updatingUserId, setUpdatingUserId] = useState<string | null>(null)

  // Confirm dialog
  const [confirmDialog, setConfirmDialog] = useState<ConfirmDialogState | null>(null)

  useEffect(() => {
    const state = location.state as InstitutionManagementLocationState | null
    if (!state?.openAddInstitution) return

    setSelectedInstitution(null)
    setShowAddModal(true)
    navigate(location.pathname, { replace: true, state: null })
  }, [location.pathname, location.state, navigate])

  useEffect(() => {
    if (user.role !== 'admin') return
    setListLoading(true)
    setListError('')
    listInstitutions()
      .then((response) => {
        const base: InstitutionWithStats[] = response.data.map((item) => ({
          id: item.id,
          name: item.name,
          code: item.institutionCode,
          emailDomain: item.emailDomain,
          status: item.status,
          contributors: 0,
          validators: 0,
          pendingInvitations: 0,
          statsLoading: true,
        }))
        setInstitutions(base)
        base.forEach((inst) => {
          Promise.all([getUserCounts(inst.id), getPendingInvitationCount(inst.id)])
            .then(([countsRes, pendingRes]) => {
              setInstitutions((current) =>
                current.map((i) =>
                  i.id === inst.id
                    ? {
                        ...i,
                        contributors: countsRes.data.contributors,
                        validators: countsRes.data.validators,
                        pendingInvitations: pendingRes.data.pendingInvitations,
                        statsLoading: false,
                      }
                    : i,
                ),
              )
            })
            .catch(() => {
              setInstitutions((current) =>
                current.map((i) => (i.id === inst.id ? { ...i, statsLoading: false } : i)),
              )
            })
        })
      })
      .catch((err: unknown) => {
        setListError(getApiErrorMessage(err, 'Unable to load institutions.'))
      })
      .finally(() => setListLoading(false))
  }, [user.role])

  useEffect(() => {
    if (!selectedInstitution) return
    void loadManagementLists(selectedInstitution.id)
  }, [selectedInstitution?.id])

  const selectedInstitutionOption = useMemo(
    () =>
      selectedInstitution
        ? {
            id: selectedInstitution.id,
            name: selectedInstitution.name,
            code: selectedInstitution.code,
            emailDomain: selectedInstitution.emailDomain,
            status: selectedInstitution.status,
          }
        : null,
    [selectedInstitution],
  )

  const filteredInstitutions = useMemo(() => {
    if (!searchQuery.trim()) return institutions
    const q = searchQuery.toLowerCase()
    return institutions.filter(
      (i) =>
        i.name.toLowerCase().includes(q) ||
        i.code.toLowerCase().includes(q) ||
        i.emailDomain.toLowerCase().includes(q),
    )
  }, [institutions, searchQuery])

  const trimmedAddName = addForm.name.trim()
  const normalizedAddDomain = normalizeDomain(addForm.domain)
  const addNameIsValid = trimmedAddName.length > 1
  const addDomainIsValid = isValidDomain(normalizedAddDomain)
  const addFormIsValid = addNameIsValid && addDomainIsValid

  useEffect(() => {
    if (!showAddModal || typeof document === 'undefined') return

    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        handleCloseAddModal()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [showAddModal, addForm.loading])

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

  function handleSelectInstitution(inst: InstitutionWithStats) {
    setSelectedInstitution(inst)
    setActiveTab('invitations')
    setEmailChips([])
    setEmailDraft('')
    // Pre-select validator role for non-active institutions since contributors cannot be invited yet
    setInviteRole(inst.status === 'active' ? null : 'validator')
    setInviteResults(null)
    setPendingInvitations([])
    setManagedUsers([])
    setManagementError('')
  }

  function handleBackToList() {
    setSelectedInstitution(null)
    setManagementError('')
  }

  function handleDeleteInstitution(inst: InstitutionWithStats) {
    setConfirmDialog({
      title: 'Delete Institution',
      message: `Permanently delete "${inst.name}"? This cannot be undone. The institution must have no users and no submissions.`,
      confirmLabel: 'Delete',
      dangerous: true,
      onConfirm: () => {
        setConfirmDialog(null)
        void executeDeleteInstitution(inst)
      },
    })
  }

  async function executeDeleteInstitution(inst: InstitutionWithStats) {
    try {
      await deleteInstitution(inst.id)
      setInstitutions((current) => current.filter((i) => i.id !== inst.id))
      if (selectedInstitution?.id === inst.id) {
        setSelectedInstitution(null)
        setManagementError('')
      }
      toast.success(`"${inst.name}" has been deleted.`)
    } catch (err: unknown) {
      toast.error(getApiErrorMessage(err, 'Unable to delete institution.'))
    }
  }

  async function handleAddInstitution(e: FormEvent) {
    e.preventDefault()
    const name = trimmedAddName
    const domain = normalizedAddDomain
    const code = generateInstitutionCode(name, domain)

    if (!name || !domain) {
      setAddForm((f) => ({ ...f, error: 'All fields are required.' }))
      return
    }
    if (!addNameIsValid) {
      setAddForm((f) => ({ ...f, error: 'Institution name must be at least 2 characters.' }))
      return
    }
    if (!addDomainIsValid) {
      setAddForm((f) => ({ ...f, error: 'Enter a valid email domain (e.g. su.edu.ph).' }))
      return
    }

    setAddForm((f) => ({ ...f, loading: true, error: '' }))
    try {
      const response = await createInstitution(name, code, domain)
      const newInst: InstitutionWithStats = {
        id: response.data.id,
        name: response.data.name,
        code: response.data.institutionCode,
        emailDomain: response.data.emailDomain,
        status: response.data.status,
        contributors: 0,
        validators: 0,
        pendingInvitations: 0,
        statsLoading: false,
      }
      setInstitutions((current) => [...current, newInst])
      toast.success(`${newInst.name} has been provisioned.`)
      handleCloseAddModal({ force: true })
    } catch (err: unknown) {
      const message = getApiErrorMessage(err, 'An error occurred while provisioning the workspace.')
      setAddForm((f) => ({ ...f, error: message }))
      toast.error(message)
    } finally {
      setAddForm((f) => ({ ...f, loading: false }))
    }
  }

  function handleCloseAddModal(options?: { force?: boolean }) {
    if (addForm.loading && !options?.force) return
    setShowAddModal(false)
    setAddForm({ name: '', domain: '', loading: false, error: '' })
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
    if (!selectedInstitution) return
    if (emailChips.length > 15) {
      setInviteResults({
        total: emailChips.length,
        success: [],
        failed: [{ email: 'Batch', reason: 'Batch exceeds maximum of 15 invitations.' }],
      })
      return
    }
    if (!inviteRole) return

    if (inviteRole === 'validator') {
      const proceed = await confirmValidatorInvite()
      if (!proceed) return
    }

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
            institutionId: selectedInstitution.id,
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
        toast.success(
          `${success.length} invitation${success.length === 1 ? '' : 's'} sent successfully.`,
        )
      } else {
        if (success.length > 0) {
          toast.info(
            `${success.length} of ${emailChips.length} invitation${success.length === 1 ? '' : 's'} sent.`,
          )
        }
        setInviteResults({ total: emailChips.length, success, failed })
      }
      setEmailChips([])
      setEmailDraft('')
      setInviteRole(null)
      if (selectedInstitution) {
        await loadManagementLists(selectedInstitution.id)
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
        onCancel: () => resolve(false),
      })
    })
  }


  function handleToggleUserStatus(managedUser: UserProfileResponse) {
    const nextState =
      managedUser.accountState.toLowerCase() === 'inactive' ? 'active' : 'inactive'
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
      message: `Are you sure you want to permanently remove ${getUserDisplayName(managedUser)}? This cannot be undone.`,
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
      await deleteUser(managedUser.id)
      setManagedUsers((current) => current.filter((item) => item.id !== managedUser.id))
      toast.success('User removed.')
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
      const match = pendingInvitations.find(
        (inv) => inv.recipientEmail.toLowerCase() === managedUser.email.toLowerCase(),
      )
      if (match) {
        await cancelInvitation(match.id)
      }
      setManagedUsers((current) => current.filter((item) => item.id !== managedUser.id))
      if (selectedInstitution) {
        await loadManagementLists(selectedInstitution.id)
      }
      toast.success('Invitation cancelled.')
    } catch (error: unknown) {
      toast.error(getApiErrorMessage(error, 'Unable to cancel invitation.'))
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

  // ── Detail view ──────────────────────────────────────────────────────────────

  const addInstitutionModal =
    showAddModal && typeof document !== 'undefined'
      ? createPortal(
          <div
            className="dash-modal-backdrop im-modal-backdrop"
            onClick={() => handleCloseAddModal()}
            role="presentation"
          >
            <div
              className="dash-modal-card im-modal-card"
              onClick={(e) => e.stopPropagation()}
              role="dialog"
              aria-modal="true"
              aria-labelledby="im-modal-title"
              aria-describedby="im-modal-subtitle"
            >
              <div className="dash-modal-header im-modal-header">
                <div>
                  <div id="im-modal-title" className="dash-modal-title">
                    Add Institution
                  </div>
                  <div id="im-modal-subtitle" className="dash-modal-sub">
                    Provision a new HEI workspace and bind its email domain.
                  </div>
                </div>
                <button
                  type="button"
                  className="dash-modal-close"
                  onClick={() => handleCloseAddModal()}
                  aria-label="Close"
                  disabled={addForm.loading}
                >
                  <i className="ti ti-x" aria-hidden="true"></i>
                </button>
              </div>

              <form className="im-add-form" onSubmit={(e) => void handleAddInstitution(e)}>
                <div className="dash-field">
                  <label className="dash-field-label" htmlFor="im-inst-name">
                    Institution Name
                  </label>
                  <input
                    id="im-inst-name"
                    className={`dash-input${addForm.name && !addNameIsValid ? ' is-invalid' : ''}`}
                    placeholder="Silliman University"
                    value={addForm.name}
                    onChange={(e) =>
                      setAddForm((f) => ({ ...f, name: e.target.value, error: '' }))
                    }
                    disabled={addForm.loading}
                    autoFocus
                    aria-invalid={addForm.name ? !addNameIsValid : undefined}
                  />
                  {addForm.name && !addNameIsValid && (
                    <div className="im-field-error" role="alert">
                      Enter at least 2 characters.
                    </div>
                  )}
                </div>

                <div className="dash-field">
                  <label className="dash-field-label" htmlFor="im-inst-domain">
                    Associated Email Domain
                  </label>
                  <input
                    id="im-inst-domain"
                    className={`dash-input${
                      addForm.domain && !addDomainIsValid ? ' is-invalid' : ''
                    }`}
                    placeholder="su.edu.ph"
                    value={addForm.domain}
                    onChange={(e) =>
                      setAddForm((f) => ({ ...f, domain: e.target.value, error: '' }))
                    }
                    disabled={addForm.loading}
                    aria-invalid={addForm.domain ? !addDomainIsValid : undefined}
                  />
                  {addForm.domain && !addDomainIsValid ? (
                    <div className="im-field-error" role="alert">
                      Use a valid domain, such as su.edu.ph.
                    </div>
                  ) : (
                    <div className="dash-field-hint">
                      Used to auto-route contributors and apply institution branding.
                    </div>
                  )}
                </div>

                <div className="dash-inline-field im-code-preview">
                  <div>
                    <div className="dash-inline-label">Generated Institution Code</div>
                    <div className="dash-inline-sub">Based on name/domain.</div>
                  </div>
                  <div className="dash-pill">
                    {generateInstitutionCode(trimmedAddName, normalizedAddDomain) || 'AUTO'}
                  </div>
                </div>

                {addForm.error && (
                  <div className="alert alert-err im-modal-alert" role="alert">
                    <i className="ti ti-alert-circle" aria-hidden="true"></i>
                    <div>{addForm.error}</div>
                  </div>
                )}

                <div className="dash-modal-actions im-modal-actions">
                  <button
                    type="button"
                    className="btn-ghost"
                    onClick={() => handleCloseAddModal()}
                    disabled={addForm.loading}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn-primary"
                    disabled={!addFormIsValid || addForm.loading}
                  >
                    {addForm.loading ? (
                      <>
                        <i className="ti ti-loader-2 im-spin" aria-hidden="true"></i>
                        Adding...
                      </>
                    ) : (
                      'Add Institution'
                    )}
                  </button>
                </div>
              </form>
            </div>
          </div>,
          document.body,
        )
      : null

  if (selectedInstitution) {
    return (
      <div className="um-screen">
        <main className="um-body">
          <nav className="im-breadcrumb" aria-label="Breadcrumb">
            <button type="button" onClick={handleBackToList}>
              <i className="ti ti-building" aria-hidden="true"></i>
              Institution Management
            </button>
            <i className="ti ti-chevron-right" aria-hidden="true"></i>
            <span>{selectedInstitution.name}</span>
          </nav>

          <div className="im-detail-header">
            <div className="im-detail-icon">
              <i className="ti ti-building-community" aria-hidden="true"></i>
            </div>
            <div className="im-detail-info">
              <div className="im-detail-name-row">
                <h1 className="im-detail-name">{selectedInstitution.name}</h1>
                <InstitutionStatusBadge status={selectedInstitution.status} />
              </div>
              <div className="im-detail-meta">
                {selectedInstitution.code && (
                  <span className="im-meta-chip">
                    <i className="ti ti-hash" aria-hidden="true"></i>
                    {selectedInstitution.code}
                  </span>
                )}
                {selectedInstitution.emailDomain && (
                  <span className="im-meta-chip">
                    <i className="ti ti-at" aria-hidden="true"></i>
                    {selectedInstitution.emailDomain}
                  </span>
                )}
              </div>
            </div>
            <div className="im-detail-stats">
              <div className="im-detail-stat">
                <span className="im-detail-stat-val">
                  {selectedInstitution.contributors + selectedInstitution.validators}
                </span>
                <span className="im-detail-stat-lbl">Users</span>
              </div>
              <div className="im-detail-stat">
                <span
                  className={`im-detail-stat-val${selectedInstitution.pendingInvitations > 0 ? ' is-warn' : ''}`}
                >
                  {selectedInstitution.pendingInvitations}
                </span>
                <span className="im-detail-stat-lbl">Pending Invites</span>
              </div>
              <button
                type="button"
                className="im-delete-inst-btn"
                onClick={() => handleDeleteInstitution(selectedInstitution)}
                aria-label={`Delete ${selectedInstitution.name}`}
              >
                <i className="ti ti-trash" aria-hidden="true" />
                Delete
              </button>
            </div>
          </div>

          {managementError && (
            <div className="alert alert-err" role="alert">
              <i className="ti ti-alert-circle" aria-hidden="true"></i>
              <div>{managementError}</div>
            </div>
          )}

          <div className="um-tabs im-tabs-stretch" role="tablist" aria-label="Institution sections">
            <button
              type="button"
              role="tab"
              id="im-tab-invitations"
              aria-controls="im-panel-invitations"
              aria-selected={activeTab === 'invitations'}
              className={`um-tab${activeTab === 'invitations' ? ' is-active' : ''}`}
              onClick={() => setActiveTab('invitations')}
            >
              <i className="ti ti-send" aria-hidden="true"></i>
              Invitations
            </button>
            <button
              type="button"
              role="tab"
              id="im-tab-users"
              aria-controls="im-panel-users"
              aria-selected={activeTab === 'users'}
              className={`um-tab${activeTab === 'users' ? ' is-active' : ''}`}
              onClick={() => setActiveTab('users')}
            >
              <i className="ti ti-users" aria-hidden="true"></i>
              Manage Users
              {managedUsers.length > 0 && (
                <span className="um-tab-badge is-neutral">{managedUsers.length}</span>
              )}
            </button>
          </div>

          {activeTab === 'invitations' && (
            <div
              id="im-panel-invitations"
              role="tabpanel"
              aria-labelledby="im-tab-invitations"
              className="um-tab-panel"
            >
              {selectedInstitution?.status === 'inactive' && (
                <div className="alert alert-info im-status-banner" role="status">
                  <i className="ti ti-info-circle" aria-hidden="true"></i>
                  <div>
                    <strong>Institution is inactive.</strong> Invite a validator to activate this workspace. Contributors can only be invited once the institution is active.
                  </div>
                </div>
              )}
              {selectedInstitution?.status === 'pending' && (
                <div className="alert alert-warn im-status-banner" role="status">
                  <i className="ti ti-clock" aria-hidden="true"></i>
                  <div>
                    <strong>Awaiting validator activation.</strong> A validator invitation has been sent. Contributors can be invited once the validator activates their account.
                  </div>
                </div>
              )}
              <InvitationComposer
                chips={emailChips}
                emailDraft={emailDraft}
                role={inviteRole}
                selectedInstitution={selectedInstitutionOption}
                canChooseRole={selectedInstitution?.status === 'active'}
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
            </div>
          )}

          {activeTab === 'users' && (
            <div
              id="im-panel-users"
              role="tabpanel"
              aria-labelledby="im-tab-users"
              className="um-tab-panel"
            >
              {!managementLoading && (
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
                    value={
                      managedUsers.filter((u) => u.accountState.toLowerCase() === 'active').length
                    }
                    loading={managementLoading && managedUsers.length === 0}
                    accent="green"
                  />
                  <MetricCard
                    icon="ti ti-shield-check"
                    label="Validators"
                    value={
                      managedUsers.filter((u) => u.role.toLowerCase() === 'validator').length
                    }
                    loading={managementLoading && managedUsers.length === 0}
                    accent="purple"
                  />
                  <MetricCard
                    icon="ti ti-pencil"
                    label="Contributors"
                    value={
                      managedUsers.filter((u) => u.role.toLowerCase() === 'contributor').length
                    }
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
              />
            </div>
          )}

        </main>

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

  // ── List view ────────────────────────────────────────────────────────────────

  return (
    <div className="um-screen">
      <main className="um-body">
        <header className="im-page-header">
          <div>
            <h1>Institution Management</h1>
            <p>Manage member HEI workspaces and their users.</p>
          </div>
          <button
            type="button"
            className="im-add-btn"
            onClick={() => setShowAddModal(true)}
          >
            <i className="ti ti-building-plus" aria-hidden="true"></i>
            Add Institution
          </button>
        </header>

        {listError && (
          <div className="alert alert-err" role="alert">
            <i className="ti ti-alert-circle" aria-hidden="true"></i>
            <div>{listError}</div>
          </div>
        )}

        {!listLoading && institutions.length > 0 && (
          <div className="im-search-bar">
            <div className="im-search-wrap">
              <i className="ti ti-search im-search-icon" aria-hidden="true"></i>
              <input
                className="im-search-input"
                type="search"
                placeholder="Search by name, code, or domain…"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                aria-label="Search institutions"
              />
            </div>
          </div>
        )}

        {listLoading && (
          <div className="im-card-grid">
            {Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="im-skeleton-card">
                <div className="im-skeleton-card-top">
                  <div className="im-skeleton-icon"></div>
                  <div className="im-skeleton-info">
                    <SkeletonBlock className="um-skeleton-line is-wide" />
                    <div className="im-skeleton-chips">
                      <SkeletonBlock className="um-skeleton-line is-short" />
                      <SkeletonBlock className="um-skeleton-line is-short" />
                    </div>
                  </div>
                </div>
                <div className="im-skeleton-stats">
                  <SkeletonBlock className="um-skeleton-line is-medium" />
                  <SkeletonBlock className="um-skeleton-line is-medium" />
                </div>
                <div className="im-skeleton-footer">
                  <SkeletonBlock className="um-skeleton-line is-short" />
                </div>
              </div>
            ))}
          </div>
        )}

        {!listLoading && institutions.length === 0 && (
          <div className="im-empty-state">
            <div className="im-empty-icon-wrap">
              <i className="ti ti-building-off" aria-hidden="true"></i>
            </div>
            <strong className="im-empty-title">No institutions yet</strong>
            <p className="im-empty-sub">
              Provision the first HEI workspace to get started.
            </p>
            <button
              type="button"
              className="im-add-btn"
              onClick={() => setShowAddModal(true)}
            >
              <i className="ti ti-building-plus" aria-hidden="true"></i>
              Add First Institution
            </button>
          </div>
        )}

        {!listLoading && institutions.length > 0 && filteredInstitutions.length === 0 && (
          <div className="im-empty-state">
            <div className="im-empty-icon-wrap">
              <i className="ti ti-search-off" aria-hidden="true"></i>
            </div>
            <strong className="im-empty-title">No results</strong>
            <p className="im-empty-sub">No institutions match "{searchQuery}".</p>
            <button
              type="button"
              className="im-clear-btn"
              onClick={() => setSearchQuery('')}
            >
              Clear search
            </button>
          </div>
        )}

        {!listLoading && filteredInstitutions.length > 0 && (
          <div className="im-card-grid">
            {filteredInstitutions.map((inst) => (
              <InstitutionCard
                key={inst.id}
                institution={inst}
                onManage={() => handleSelectInstitution(inst)}
                onDelete={() => handleDeleteInstitution(inst)}
              />
            ))}
          </div>
        )}
      </main>

      {addInstitutionModal}

    </div>
  )
}

// ── Sub-components ────────────────────────────────────────────────────────────

interface InstitutionCardProps {
  institution: InstitutionWithStats
  onManage: () => void
  onDelete: () => void
}

function InstitutionCard({ institution, onManage, onDelete }: InstitutionCardProps) {
  const totalUsers = institution.contributors + institution.validators
  return (
    <div className="im-inst-card">
      <div className="im-inst-card-top">
        <div className="im-inst-card-icon">
          <i className="ti ti-building-community" aria-hidden="true"></i>
        </div>
        <div className="im-inst-card-info">
          <div className="im-inst-card-name-row">
            <h2 className="im-inst-card-name">{institution.name}</h2>
            <InstitutionStatusBadge status={institution.status} />
          </div>
          <div className="im-inst-card-meta">
            {institution.code && (
              <span className="im-inst-card-chip">
                <i className="ti ti-hash" aria-hidden="true"></i>
                {institution.code}
              </span>
            )}
            {institution.emailDomain && (
              <span className="im-inst-card-chip">
                <i className="ti ti-at" aria-hidden="true"></i>
                {institution.emailDomain}
              </span>
            )}
          </div>
        </div>
      </div>

      <div className="im-inst-card-stats">
        <div className="im-inst-stat">
          <i className="ti ti-users" aria-hidden="true"></i>
          {institution.statsLoading ? (
            <SkeletonBlock className="um-skeleton-line is-short" />
          ) : (
            <span>
              {totalUsers} user{totalUsers !== 1 ? 's' : ''}
            </span>
          )}
        </div>
        <div className="im-inst-stat">
          <i className="ti ti-shield-check" aria-hidden="true"></i>
          {institution.statsLoading ? (
            <SkeletonBlock className="um-skeleton-line is-short" />
          ) : (
            <span>
              {institution.validators} validator{institution.validators !== 1 ? 's' : ''}
            </span>
          )}
        </div>
        {!institution.statsLoading && institution.pendingInvitations > 0 && (
          <div className="im-inst-stat is-warn">
            <i className="ti ti-clock-pause" aria-hidden="true"></i>
            <span>
              {institution.pendingInvitations} pending invite
              {institution.pendingInvitations !== 1 ? 's' : ''}
            </span>
          </div>
        )}
      </div>

      <div className="im-inst-card-footer">
        <button type="button" className="im-manage-btn" onClick={onManage}>
          <i className="ti ti-settings" aria-hidden="true"></i>
          Manage Users
        </button>
        <button
          type="button"
          className="im-delete-btn"
          onClick={(e) => { e.stopPropagation(); onDelete(); }}
          aria-label={`Delete ${institution.name}`}
        >
          <i className="ti ti-trash" aria-hidden="true"></i>
        </button>
      </div>
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

// ── InstitutionStatusBadge ────────────────────────────────────────────────────

function InstitutionStatusBadge({ status }: { status: string }) {
  if (status === 'active') {
    return (
      <span className="im-status-badge is-active">
        <i className="ti ti-circle-check-filled" aria-hidden="true"></i>
        Active
      </span>
    )
  }
  if (status === 'pending') {
    return (
      <span className="im-status-badge is-pending">
        <i className="ti ti-clock" aria-hidden="true"></i>
        Pending
      </span>
    )
  }
  return (
    <span className="im-status-badge is-inactive">
      <i className="ti ti-circle-x" aria-hidden="true"></i>
      Inactive
    </span>
  )
}

// ── Utilities ─────────────────────────────────────────────────────────────────

function normalizeDomain(domain: string) {
  return domain.trim().toLowerCase().replace(/^@/, '')
}

function isValidDomain(domain: string) {
  return /^[a-z0-9.-]+\.[a-z]{2,}$/.test(domain)
}

function generateInstitutionCode(name: string, domain: string) {
  const domainPrefix = domain.split('.')[0] || ''
  if (domainPrefix.length > 1) return domainPrefix.toUpperCase()
  const parts = name
    .replace(/[^A-Za-z0-9 ]/g, ' ')
    .split(' ')
    .filter(Boolean)
  return parts.map((p) => p.charAt(0)).join('').toUpperCase() || 'INST'
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
