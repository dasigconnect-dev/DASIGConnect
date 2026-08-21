import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { createPortal } from 'react-dom'
import { useLocation, useNavigate } from 'react-router-dom'
import {
  cancelInvitation,
  createInstitution,
  deleteInstitution,
  deleteUser,
  getInstitutionLogoUrl,
  getUserAvatarUrl,
  getUserCounts,
  getPendingInvitationCount,
  listInstitutions,
  inviteUser,
  listPendingInvitations,
  listUsers,
  updateUserStatus,
  uploadInstitutionLogo,
  uploadUserAvatar,
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
  logoUrl: string | null
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

type InstitutionStatusFilter = 'all' | 'active' | 'pending'

export default function InstitutionManagementScreen({ user }: InstitutionManagementScreenProps) {
  const toast = useToast()
  const location = useLocation()
  const navigate = useNavigate()
  const searchInputRef = useRef<HTMLInputElement>(null)

  // List view
  const [institutions, setInstitutions] = useState<InstitutionWithStats[]>([])
  const [listLoading, setListLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [institutionStatusFilter, setInstitutionStatusFilter] =
    useState<InstitutionStatusFilter>('all')
  const [logoUploadingId, setLogoUploadingId] = useState<string | null>(null)

  // Detail view
  const [selectedInstitution, setSelectedInstitution] = useState<InstitutionWithStats | null>(null)
  const [showInviteModal, setShowInviteModal] = useState(false)

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
  const [avatarUploadingUserId, setAvatarUploadingUserId] = useState<string | null>(null)

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
          logoUrl: item.hasLogo ? getInstitutionLogoUrl(item.id, item.logoUpdatedAt) : null,
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
    const q = searchQuery.trim().toLowerCase()
    return institutions.filter((institution) => {
      if (institutionStatusFilter !== 'all' && institution.status !== institutionStatusFilter) {
        return false
      }
      if (!q) return true
      return (
        institution.name.toLowerCase().includes(q) ||
        institution.code.toLowerCase().includes(q) ||
        institution.emailDomain.toLowerCase().includes(q)
      )
    })
  }, [institutionStatusFilter, institutions, searchQuery])

  const institutionStatusCounts = useMemo(
    () => ({
      all: institutions.length,
      active: institutions.filter((institution) => institution.status === 'active').length,
      pending: institutions.filter((institution) => institution.status === 'pending').length,
    }),
    [institutions],
  )

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

  useEffect(() => {
    if (!showInviteModal || typeof document === 'undefined') return

    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !sending) {
        setShowInviteModal(false)
        setEmailChips([])
        setEmailDraft('')
        setInviteRole('contributor')
        setInviteResults(null)
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [showInviteModal, sending])

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
    setEmailChips([])
    setEmailDraft('')
    setInviteRole('contributor')
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

  async function handleLogoUpload(inst: InstitutionWithStats, file: File) {
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      toast.error('Choose a JPEG, PNG, or WebP logo.')
      return
    }
    if (file.size > 2 * 1024 * 1024) {
      toast.error('Institution logo must be 2 MB or smaller.')
      return
    }

    setLogoUploadingId(inst.id)
    try {
      const response = await uploadInstitutionLogo(inst.id, file)
      const logoUrl = getInstitutionLogoUrl(inst.id, response.data.logoUpdatedAt)
      setInstitutions((current) =>
        current.map((item) => (item.id === inst.id ? { ...item, logoUrl } : item)),
      )
      toast.success(`${inst.name} logo updated.`)
    } catch (error: unknown) {
      toast.error(getApiErrorMessage(error, 'Unable to upload the institution logo.'))
    } finally {
      setLogoUploadingId(null)
    }
  }

  async function handleUserAvatarUpload(managedUser: UserProfileResponse, file: File) {
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      toast.error('Choose a JPEG, PNG, or WebP profile image.')
      return
    }
    if (file.size > 2 * 1024 * 1024) {
      toast.error('Profile image must be 2 MB or smaller.')
      return
    }

    setAvatarUploadingUserId(managedUser.id)
    try {
      const response = await uploadUserAvatar(managedUser.id, file)
      const avatarUrl = getUserAvatarUrl(managedUser.id, response.data.avatarUpdatedAt)
      setManagedUsers((current) =>
        current.map((item) =>
          item.id === managedUser.id
            ? { ...item, ...response.data, avatarUrl }
            : item,
        ),
      )
      toast.success(`${getUserDisplayName(managedUser)} profile image updated.`)
    } catch (error: unknown) {
      toast.error(getApiErrorMessage(error, 'Unable to upload the profile image.'))
    } finally {
      setAvatarUploadingUserId(null)
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
        logoUrl: null,
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

  function handleOpenInviteModal() {
    setEmailChips([])
    setEmailDraft('')
    setInviteRole('contributor')
    setInviteResults(null)
    setShowInviteModal(true)
  }

  function handleCloseInviteModal() {
    if (sending) return
    setShowInviteModal(false)
    setEmailChips([])
    setEmailDraft('')
    setInviteRole('contributor')
    setInviteResults(null)
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
      setInviteRole('contributor')
      if (selectedInstitution) {
        await loadManagementLists(selectedInstitution.id)
      }
      if (failed.length === 0) {
        setShowInviteModal(false)
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

  const inviteContributorModal =
    showInviteModal && selectedInstitution && typeof document !== 'undefined'
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
              aria-labelledby="im-invite-modal-title"
              aria-describedby="im-invite-modal-subtitle"
            >
              <div className="dash-modal-header im-modal-header">
                <div>
                  <div id="im-invite-modal-title" className="dash-modal-title">
                    Invite Contributor
                  </div>
                  <div id="im-invite-modal-subtitle" className="dash-modal-sub">
                    Send an invitation to join {selectedInstitution.name}.
                  </div>
                </div>
                <button
                  type="button"
                  className="dash-modal-close"
                  onClick={handleCloseInviteModal}
                  aria-label="Close invite contributor modal"
                  disabled={sending}
                >
                  <i className="ti ti-x" aria-hidden="true"></i>
                </button>
              </div>

              <InvitationComposer
                chips={emailChips}
                emailDraft={emailDraft}
                role={inviteRole}
                selectedInstitution={selectedInstitutionOption}
                canChooseRole={false}
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
          </div>,
          document.body,
        )
      : null

  if (selectedInstitution) {
    return (
      <div className="um-screen">
        <main className="um-body" key={selectedInstitution.id}>
          <div className="im-detail-topbar">
            <nav className="im-breadcrumb" aria-label="Breadcrumb">
              <button type="button" onClick={handleBackToList}>
                <i className="ti ti-building" aria-hidden="true"></i>
                Institution Management
              </button>
              <i className="ti ti-chevron-right" aria-hidden="true"></i>
              <span>{selectedInstitution.name}</span>
            </nav>
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

          <div className={`im-detail-header${selectedInstitution.logoUrl ? ' has-logo' : ''}`}>
            {selectedInstitution.logoUrl && (
              <div className="im-detail-logo-watermark" aria-hidden="true">
                <img
                  src={selectedInstitution.logoUrl}
                  alt=""
                  onError={(event) => {
                    event.currentTarget.parentElement?.classList.add('is-unavailable')
                  }}
                />
              </div>
            )}
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
                  {selectedInstitution.contributors}
                </span>
                <span className="im-detail-stat-lbl">Contributors</span>
              </div>
              <div className="im-detail-stat">
                <span
                  className={`im-detail-stat-val${selectedInstitution.pendingInvitations > 0 ? ' is-warn' : ''}`}
                >
                  {selectedInstitution.pendingInvitations}
                </span>
                <span className="im-detail-stat-lbl">Pending Invites</span>
              </div>
            </div>
          </div>

          {managementError && (
            <div className="alert alert-err" role="alert">
              <i className="ti ti-alert-circle" aria-hidden="true"></i>
              <div>{managementError}</div>
            </div>
          )}

          <InstitutionUsersCard
            currentUser={user}
            users={managedUsers.filter((managedUser) => managedUser.role.toLowerCase() === 'contributor')}
            loading={managementLoading}
            updatingUserId={updatingUserId}
            onToggleUserStatus={handleToggleUserStatus}
            onDeleteUser={handleDeleteUser}
            onCancelInvitation={handleCancelInvitationFromUsers}
            showRoleControls={false}
            showInstitutionColumn={false}
            showFilterPills={false}
            userColumnLabel="Contributor"
            title="Contributors"
            description={`Contributor accounts assigned to ${selectedInstitution.name}.`}
            variant="directory"
            headerAction={(
              <button
                type="button"
                className="im-invite-contributor-btn"
                onClick={handleOpenInviteModal}
              >
                <i className="ti ti-user-plus" aria-hidden="true"></i>
                Invite Contributor
              </button>
            )}
            avatarUploadingUserId={avatarUploadingUserId}
            onAvatarUpload={(managedUser, file) => void handleUserAvatarUpload(managedUser, file)}
          />

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
        {inviteContributorModal}
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
            Add institution
          </button>
        </header>

        {listError && (
          <div className="alert alert-err" role="alert">
            <i className="ti ti-alert-circle" aria-hidden="true"></i>
            <div>{listError}</div>
          </div>
        )}

        {!listLoading && institutions.length > 0 && (
          <section className="im-registry-controls" aria-labelledby="institution-registry-title">
            <div className="im-registry-toolbar">

              <div className="im-registry-toolbar-row">
                <div className="im-status-tabs" role="group" aria-label="Filter institutions by status">
                  {(['all', 'active', 'pending'] as InstitutionStatusFilter[]).map((status) => (
                    <button
                      key={status}
                      type="button"
                      className={`im-status-tab im-${status}${institutionStatusFilter === status ? ' is-active' : ''}`}
                      onClick={() => setInstitutionStatusFilter(status)}
                      aria-pressed={institutionStatusFilter === status}
                    >
                      {status.charAt(0).toUpperCase() + status.slice(1)}
                      <span className="im-status-tab-count">{institutionStatusCounts[status]}</span>
                    </button>
                  ))}
                </div>
                <div className="im-search-wrap">
                  <i className="ti ti-search im-search-icon" aria-hidden="true"></i>
                  <input
                    ref={searchInputRef}
                    className="im-search-input"
                    type="search"
                    placeholder="Search institutions..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    aria-label="Search institutions"
                  />

                </div>
              </div>
            </div>
          </section>
        )}

        {listLoading && (
          <div className="im-registry" aria-label="Loading institutions" aria-busy="true">
            <InstitutionRegistryHeader />
            {Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="im-skeleton-row">
                <div className="im-skeleton-institution">
                  <div className="im-skeleton-icon"></div>
                  <SkeletonBlock className="um-skeleton-line is-wide" />
                </div>
                <SkeletonBlock className="um-skeleton-line is-short" />
                <SkeletonBlock className="um-skeleton-line is-wide" />
                <SkeletonBlock className="um-skeleton-line is-short" />
                <SkeletonBlock className="um-skeleton-line is-short" />
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
              Add first institution
            </button>
          </div>
        )}

        {!listLoading && institutions.length > 0 && filteredInstitutions.length === 0 && (
          <div className="im-empty-state">
            <div className="im-empty-icon-wrap">
              <i className="ti ti-search-off" aria-hidden="true"></i>
            </div>
            <strong className="im-empty-title">No results</strong>
            <p className="im-empty-sub">
              No {institutionStatusFilter === 'all' ? '' : `${institutionStatusFilter} `}institutions
              {searchQuery.trim() ? ` match "${searchQuery}"` : ' to show'}.
            </p>
            <button
              type="button"
              className="im-clear-btn"
              onClick={() => {
                setSearchQuery('')
                setInstitutionStatusFilter('all')
              }}
            >
              Clear filters
            </button>
          </div>
        )}

        {!listLoading && filteredInstitutions.length > 0 && (
          <div className="im-registry" key={institutionStatusFilter}>
            <InstitutionRegistryHeader />
            {filteredInstitutions.map((inst) => (
              <InstitutionRow
                key={inst.id}
                institution={inst}
                onSelect={() => handleSelectInstitution(inst)}
                onLogoUpload={(file) => void handleLogoUpload(inst, file)}
                logoUploading={logoUploadingId === inst.id}
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

function InstitutionRegistryHeader() {
  return (
    <div className="im-registry-header" aria-hidden="true">
      <span>Institution</span>
      <span>Code</span>
      <span>Domain</span>
      <span>Status</span>
      <span>Action</span>
    </div>
  )
}

interface InstitutionRowProps {
  institution: InstitutionWithStats
  onSelect: () => void
  onLogoUpload: (file: File) => void
  logoUploading: boolean
}

function InstitutionRow({
  institution,
  onSelect,
  onLogoUpload,
  logoUploading,
}: InstitutionRowProps) {
  const fileInputRef = useRef<HTMLInputElement>(null)

  return (
    <div className={`im-inst-row is-${institution.status.toLowerCase()}`}>
      <button
        type="button"
        className="im-inst-row-open"
        onClick={onSelect}
        aria-label={`Open ${institution.name} workspace`}
      />

      <div className="im-inst-cell">
        <div className="im-inst-logo-wrap">
          <div className={`im-inst-card-icon${institution.logoUrl ? ' has-logo' : ''}`}>
            <span className="im-inst-logo-fallback" aria-hidden="true">
              <i className="ti ti-building-community"></i>
            </span>
            {institution.logoUrl && (
              <img
                key={institution.logoUrl}
                src={institution.logoUrl}
                alt={`${institution.name} logo`}
                onError={(event) => {
                  event.currentTarget.style.display = 'none'
                }}
              />
            )}
          </div>
          <button
            type="button"
            className={`im-logo-edit-btn${institution.logoUrl ? ' is-overlay' : ''}`}
            onClick={() => fileInputRef.current?.click()}
            disabled={logoUploading}
            aria-label={`${institution.logoUrl ? 'Replace' : 'Add'} ${institution.name} logo`}
            title={`${institution.logoUrl ? 'Replace' : 'Add'} institution logo`}
          >
            <i
              className={logoUploading ? 'ti ti-loader-2 im-spin' : 'ti ti-pencil'}
              aria-hidden="true"
            ></i>
          </button>
          <input
            ref={fileInputRef}
            className="im-logo-file-input"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            onChange={(event) => {
              const file = event.target.files?.[0]
              if (file) onLogoUpload(file)
              event.target.value = ''
            }}
          />
        </div>
        <div className="im-inst-primary">
          <h2 className="im-inst-card-name">{institution.name}</h2>
          <div className="im-inst-mobile-meta">
            <span>{institution.code || '—'}</span>
            <span>{institution.emailDomain || '—'}</span>
          </div>
        </div>
      </div>
      <span className="im-inst-code">{institution.code || '—'}</span>
      <span className="im-inst-domain">{institution.emailDomain || '—'}</span>
      <div className="im-inst-status">
        <InstitutionStatusBadge status={institution.status} />
      </div>
      <span className="im-inst-open-cue" aria-hidden="true">
        Open
        <i className="ti ti-chevron-right"></i>
      </span>
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
