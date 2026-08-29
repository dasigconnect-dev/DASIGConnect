import { useMemo, useRef, useState, type ReactNode } from 'react'
import type { UserProfileResponse } from '../../../api/authApi'
import type { User } from '../../../types/auth.types'
import { getUserDisplayName, getUserInitials } from '../../../lib/userIdentity'
import ActionMenu from './ActionMenu'
import { InlineSpinner, SkeletonRows } from './LoadingPrimitives'

interface InstitutionUsersCardProps {
  currentUser: User | null
  users: UserProfileResponse[]
  loading: boolean
  updatingUserId: string | null
  onToggleUserStatus: (user: UserProfileResponse) => void
  onDeleteUser: (user: UserProfileResponse) => void
  onCancelInvitation: (user: UserProfileResponse) => void
  onResendInvitation?: (user: UserProfileResponse) => void
  /** For pending rows: whether the current user may resend/cancel this invitation. Defaults to allowed. */
  canManageInvitation?: (user: UserProfileResponse) => boolean
  onReassign?: (user: UserProfileResponse) => void
  onChangeRole?: (user: UserProfileResponse) => void
  onEraseData?: (user: UserProfileResponse) => void
  onRequestSuperAdminTransfer?: (user: UserProfileResponse) => void
  resendingUserId?: string | null
  showRoleControls?: boolean
  showInstitutionColumn?: boolean
  title?: string
  description?: string
  headerAction?: ReactNode
  variant?: 'default' | 'directory'
  avatarUploadingUserId?: string | null
  onAvatarUpload?: (user: UserProfileResponse, file: File) => void
  showFilterPills?: boolean
  userColumnLabel?: string
  /** Render the title/count/description + headerAction row above the filter bar. */
  showHeader?: boolean
  /** Hide all per-row actions — renders the list as a read-only directory. */
  readOnly?: boolean
}

type RoleFilter = 'all' | 'moderator' | 'contributor'
type StatusFilter = 'all' | 'active' | 'pending' | 'cancelled' | 'inactive'

export default function InstitutionUsersCard({
  currentUser,
  users,
  loading,
  updatingUserId,
  onToggleUserStatus,
  onDeleteUser,
  onCancelInvitation,
  onResendInvitation,
  canManageInvitation,
  onReassign,
  onChangeRole,
  onEraseData,
  onRequestSuperAdminTransfer,
  resendingUserId = null,
  showRoleControls = true,
  showInstitutionColumn = true,
  title = 'Manage Users',
  description,
  headerAction,
  variant = 'default',
  avatarUploadingUserId = null,
  onAvatarUpload,
  showFilterPills = true,
  userColumnLabel = 'User',
  showHeader = true,
  readOnly = false,
}: InstitutionUsersCardProps) {
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState<RoleFilter>('all')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all')

  const statusCounts = useMemo(() => ({
    all: users.length,
    active: users.filter((u) => u.accountState.toLowerCase() === 'active').length,
    pending: users.filter((u) => u.accountState.toLowerCase().startsWith('pending')).length,
    inactive: users.filter((u) => u.accountState.toLowerCase() === 'inactive').length,
    cancelled: users.filter((u) => {
      const s = u.accountState.toLowerCase()
      return s === 'cancelled' || s === 'expired'
    }).length,
  }), [users])

  const filtered = users.filter((user) => {
    const searchValue = `${getUserDisplayName(user)} ${user.email}`.toLowerCase()
    if (search && !searchValue.includes(search.toLowerCase())) {
      return false
    }
    if (showFilterPills && showRoleControls && roleFilter !== 'all' && user.role.toLowerCase() !== roleFilter) {
      return false
    }
    if (showFilterPills && statusFilter !== 'all') {
      const state = user.accountState.toLowerCase()
      if (statusFilter === 'active') {
        if (state !== 'active') return false
      } else if (statusFilter === 'pending') {
        if (!state.startsWith('pending')) return false
      } else if (statusFilter === 'inactive') {
        if (state !== 'inactive') return false
      } else if (statusFilter === 'cancelled') {
        if (state !== 'cancelled' && state !== 'expired') return false
      }
    }
    return true
  })

  const hasFilters = search !== '' || (showFilterPills && ((showRoleControls && roleFilter !== 'all') || statusFilter !== 'all'))

  const activeUsersCount = useMemo(
    () => users.filter((u) => u.accountState.toLowerCase() === 'active').length,
    [users],
  )

  return (
    <>
      <section
        className={`um-data-card${variant === 'directory' ? ' is-directory' : ''}${showHeader ? '' : ' is-headerless'}${loading ? ' is-busy' : ''}`}
        aria-busy={loading}
        style={{ marginBottom: '16px' }}
      >
      {showHeader && (
        <div className="um-data-card-header">
          <div className="um-data-card-heading">
            <div className="um-data-card-title-group">
              <h2 className="um-data-card-title">{title}</h2>
              <span className="um-data-card-count">{activeUsersCount}</span>
              {loading && users.length > 0 && (
                <span className="um-refresh-pill">
                  <InlineSpinner /> Refreshing
                </span>
              )}
            </div>
            {description && <p className="um-data-card-description">{description}</p>}
          </div>
          {headerAction && <div className="um-data-card-action">{headerAction}</div>}
        </div>
      )}

      <div className={`um-filter-bar um-users-filter-bar${showFilterPills ? '' : ' is-search-only'}`}>
        {showFilterPills && (
          <div className="um-filter-pills-wrap">
            {showRoleControls && (
              <div className="um-filter-pills" role="group" aria-label="Filter by role">
                {(['all', 'moderator', 'contributor'] as RoleFilter[]).map((r) => (
                  <button
                    key={r}
                    type="button"
                    className={`um-filter-pill${roleFilter === r ? ' is-active' : ''}`}
                    onClick={() => setRoleFilter(r)}
                  >
                    {r === 'all' ? (variant === 'directory' ? 'All' : 'All Roles') : formatRoleLabel(r)}
                  </button>
                ))}
              </div>
            )}
            <div className="um-filter-pills" role="group" aria-label="Filter by status">
              {([
                { value: 'all', label: variant === 'directory' ? 'All' : 'All Status', count: statusCounts.all },
                { value: 'active', label: 'Active', count: statusCounts.active },
                { value: 'pending', label: 'Pending', count: statusCounts.pending },
                { value: 'inactive', label: 'Inactive', count: statusCounts.inactive },
                { value: 'cancelled', label: 'Cancelled', count: statusCounts.cancelled },
              ] as { value: StatusFilter; label: string; count: number }[]).map(({ value, label, count }) => (
                <button
                  key={value}
                  type="button"
                  className={`um-filter-pill${statusFilter === value ? ' is-active' : ''}`}
                  onClick={() => setStatusFilter(value)}
                >
                  <span>{label}</span>
                  <span className="um-filter-pill-count">{count}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        <div className="um-search-wrap">
          <i className="ti ti-search um-search-icon" aria-hidden="true"></i>
          <input
            type="search"
            className="um-search-input"
            placeholder={variant === 'directory' ? 'Name or email...' : 'Search by name or email...'}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            aria-label="Search users"
          />
          {search && (
            <button
              type="button"
              className="um-search-clear"
              onClick={() => setSearch('')}
              aria-label="Clear search"
            >
              <i className="ti ti-x" aria-hidden="true"></i>
            </button>
          )}
        </div>
      </div>
    </section>

    <div className={`um-data-card um-table-card${variant === 'directory' ? ' is-directory' : ''}`}>
      {loading && users.length === 0 ? (
        <UsersTableSkeleton
          showRoleControls={showRoleControls}
          showInstitutionColumn={showInstitutionColumn}
          userColumnLabel={userColumnLabel}
        />
      ) : filtered.length === 0 ? (
          <div className="um-empty-state">
            <div className="um-empty-icon" aria-hidden="true">
              <i className="ti ti-users"></i>
            </div>
            <p className="um-empty-title">
              {hasFilters ? 'No matching users found' : 'No users found'}
            </p>
            <p className="um-empty-sub">
              {hasFilters
                ? 'Try changing your search terms or filters.'
                : 'Users will appear here once they are added.'}
            </p>
            {hasFilters && (
              <button
                type="button"
                className="um-empty-clear"
                onClick={() => {
                  setSearch('')
                  setRoleFilter('all')
                  setStatusFilter('all')
                }}
              >
                Clear filters
              </button>
            )}
          </div>
        ) : (
          <div className="um-table-wrap">
            <table className="um-table">
              <thead>
                <tr>
                  <th>{userColumnLabel}</th>
                  {showRoleControls && <th>Role</th>}
                  {showInstitutionColumn && <th>Institution</th>}
                  <th>Status</th>
                  <th>{statusFilter === 'pending' ? 'Expires' : 'Joined'}</th>
                  <th aria-label="Actions"></th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((managedUser) => {
                  const isUpdating = updatingUserId === managedUser.id
                  const stateLower = managedUser.accountState.toLowerCase()
                  const isActive = stateLower === 'active'
                  const isCancelled = stateLower === 'cancelled' || stateLower === 'expired'
                  const isInactive = stateLower === 'inactive'
                  const isPending = stateLower.startsWith('pending')
                  const isResending = resendingUserId === managedUser.id
                  const canManage = canToggleUserStatus(currentUser, managedUser)
                  const displayName = getUserDisplayName(managedUser)
                  const initials = getUserInitials(managedUser)

                  const canManageThisInvite = canManageInvitation
                    ? canManageInvitation(managedUser)
                    : true

                  const menuItems = readOnly
                    ? []
                    : isPending
                    ? (canManageThisInvite
                        ? [
                            onResendInvitation
                              ? {
                                  label: isResending ? 'Resending…' : 'Resend invitation',
                                  icon: 'ti ti-send',
                                  onClick: () => onResendInvitation(managedUser),
                                  disabled: isResending,
                                }
                              : null,
                            {
                              label: 'Cancel invitation',
                              icon: 'ti ti-ban',
                              onClick: () => onCancelInvitation(managedUser),
                              dangerous: true,
                            },
                          ]
                        : []
                      ).filter((item): item is NonNullable<typeof item> => item !== null)
                    : [
                        canManage && (isActive || isInactive)
                          ? {
                              label: isUpdating
                                ? 'Updating…'
                                : isActive
                                ? 'Deactivate user'
                                : 'Activate user',
                              icon: isActive ? 'ti ti-user-x' : 'ti ti-user-check',
                              onClick: () => onToggleUserStatus(managedUser),
                              disabled: isUpdating,
                              dangerous: isActive,
                            }
                          : null,
                        canRemove(currentUser, managedUser)
                          ? {
                              label: 'Delete user',
                              icon: 'ti ti-trash',
                              onClick: () => onDeleteUser(managedUser),
                              dangerous: true,
                            }
                          : null,
                        onEraseData && canEraseData(currentUser, managedUser)
                          ? {
                              label: 'Erase personal data',
                              icon: 'ti ti-eraser',
                              onClick: () => onEraseData(managedUser),
                              dangerous: true,
                            }
                          : null,
                        onChangeRole && canChangeRole(currentUser, managedUser)
                          ? {
                              label: 'Change role',
                              icon: 'ti ti-user-cog',
                              onClick: () => onChangeRole(managedUser),
                            }
                          : null,
                        onReassign && canManage && isActive
                          ? {
                              label: 'Reassign institution',
                              icon: 'ti ti-building-community',
                              onClick: () => onReassign(managedUser),
                            }
                          : null,
                        onRequestSuperAdminTransfer && canRequestSuperAdminTransfer(currentUser, managedUser)
                          ? {
                              label: 'Transfer Admin',
                              icon: 'ti ti-shield-up',
                              onClick: () => onRequestSuperAdminTransfer(managedUser),
                            }
                          : null,
                      ].filter((item): item is NonNullable<typeof item> => item !== null)

                  return (
                    <tr
                      key={managedUser.id}
                      className={`${isInactive ? 'is-inactive-row' : ''}${isCancelled ? 'is-cancelled-row' : ''}`}
                    >
                      <td>
                        <div className="um-user-cell">
                          <UserAvatarEditor
                            user={managedUser}
                            initials={initials}
                            uploading={avatarUploadingUserId === managedUser.id}
                            onUpload={onAvatarUpload}
                          />
                          <div>
                            <strong>
                              {displayName}
                              {currentUser?.email.toLowerCase() === managedUser.email.toLowerCase() && (
                                <span className="um-you-pill">You</span>
                              )}
                              {managedUser.purgedAt && (
                                <span className="um-erased-pill">Erased</span>
                              )}
                            </strong>
                            <span className="um-user-email">
                              {managedUser.purgedAt ? 'personal data removed' : managedUser.email}
                            </span>
                          </div>
                        </div>
                      </td>
                      {showRoleControls && (
                        <td>
                          <span className={`um-role-tag is-${managedUser.role.toLowerCase()}`}>
                            {formatRoleLabel(managedUser.role)}
                          </span>
                        </td>
                      )}
                      {showInstitutionColumn && <td>{managedUser.institutionName || '—'}</td>}
                      <td>
                        <span className={`um-badge ${stateClass(managedUser)}`}>
                          {isUpdating ? (
                            <><InlineSpinner /> Updating</>
                          ) : (
                            formatAccountState(managedUser)
                          )}
                        </span>
                      </td>
                      <td className="um-date-cell">{formatDate(managedUser.createdAt)}</td>
                      <td className="um-table-actions-cell">
                        {menuItems.length > 0 && (
                          <ActionMenu align="right" items={menuItems} />
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  )
}

function UserAvatarEditor({
  user,
  initials,
  uploading,
  onUpload,
}: {
  user: UserProfileResponse
  initials: string
  uploading: boolean
  onUpload?: (user: UserProfileResponse, file: File) => void
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const editable = Boolean(onUpload)
  const hasImage = Boolean(user.avatarUrl)

  const visual = (
    <>
      <span className="um-user-avatar-fallback">{initials}</span>
      {user.avatarUrl && (
        <img
          key={user.avatarUrl}
          src={user.avatarUrl}
          alt=""
          onError={(event) => {
            event.currentTarget.style.display = 'none'
          }}
        />
      )}
      {editable && hasImage && (
        <span className="um-user-avatar-hover" aria-hidden="true">
          <i className={uploading ? 'ti ti-loader-2 um-spin' : 'ti ti-pencil'}></i>
        </span>
      )}
    </>
  )

  return (
    <div className={`um-user-avatar-editor${hasImage ? ' has-image' : ''}`}>
      {editable ? (
        <button
          type="button"
          className="um-user-avatar"
          onClick={() => inputRef.current?.click()}
          disabled={uploading}
          aria-label={`${hasImage ? 'Replace' : 'Add'} ${getUserDisplayName(user)} profile image`}
          title={`${hasImage ? 'Replace' : 'Add'} profile image`}
        >
          {visual}
        </button>
      ) : (
        <span className="um-user-avatar">{visual}</span>
      )}
      {editable && !hasImage && (
        <span className="um-user-avatar-pencil" aria-hidden="true">
          <i className={uploading ? 'ti ti-loader-2 um-spin' : 'ti ti-pencil'}></i>
        </span>
      )}
      {editable && (
        <input
          ref={inputRef}
          className="um-avatar-file-input"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          onChange={(event) => {
            const file = event.target.files?.[0]
            if (file) onUpload?.(user, file)
            event.target.value = ''
          }}
        />
      )}
    </div>
  )
}

function UsersTableSkeleton({
  showRoleControls,
  showInstitutionColumn,
  userColumnLabel,
}: {
  showRoleControls: boolean
  showInstitutionColumn: boolean
  userColumnLabel: string
}) {
  const columns = 4 + Number(showRoleControls) + Number(showInstitutionColumn)

  return (
    <div className="um-table-wrap">
      <table className="um-table">
        <thead>
          <tr>
            <th>{userColumnLabel}</th>
            {showRoleControls && <th>Role</th>}
            {showInstitutionColumn && <th>Institution</th>}
            <th>Status</th>
            <th>Joined</th>
            <th aria-label="Actions"></th>
          </tr>
        </thead>
        <tbody>
          <SkeletonRows rows={5} columns={columns} />
        </tbody>
      </table>
    </div>
  )
}

function formatRoleLabel(value: string) {
  const n = value.toLowerCase()
  return n.charAt(0).toUpperCase() + n.slice(1)
}

function formatAccountState(user: UserProfileResponse) {
  const n = user.accountState.toLowerCase()
  // `inactive` always means an account that was activated and later deactivated
  // — a pending invite can't be deactivated — so never relabel it "Cancelled",
  // even when its name is missing (legacy / imported rows).
  if (n === 'cancelled') {
    return 'Cancelled'
  }
  return user.accountState
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function formatDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}

function stateClass(user: UserProfileResponse) {
  const n = user.accountState.toLowerCase()
  if (n === 'cancelled') {
    return 'is-cancelled'
  }
  if (n.includes('inactive')) return 'is-muted'
  if (n.includes('active')) return 'is-active'
  if (n.includes('pending')) return 'is-pending'
  return 'is-muted'
}

function canToggleUserStatus(currentUser: User | null, managedUser: UserProfileResponse) {
  if (!currentUser) return false
  const state = managedUser.accountState.toLowerCase()
  if (state !== 'active' && state !== 'inactive') return false
  const targetRole = managedUser.role.toLowerCase()
  if (targetRole === 'moderator' || targetRole === 'admin') {
    return currentUser.role === 'admin' && currentUser.email.toLowerCase() !== managedUser.email.toLowerCase()
  }
  // Activate/deactivate is an admin-only mutation. Moderators may view the
  // contributor roster but only manage invitations (invite / resend / cancel).
  return currentUser.role === 'admin'
}

/** Delete is allowed once an account is deactivated, cancelled, or expired — the states `removeUser` accepts. */
function canRemove(currentUser: User | null, managedUser: UserProfileResponse) {
  if (!currentUser) return false
  const state = managedUser.accountState.toLowerCase()
  if (state !== 'inactive' && state !== 'cancelled' && state !== 'expired') return false
  const targetRole = managedUser.role.toLowerCase()
  if (targetRole === 'moderator' || targetRole === 'admin') {
    return currentUser.role === 'admin' && currentUser.email.toLowerCase() !== managedUser.email.toLowerCase()
  }
  return currentUser.role === 'admin'
}

function canChangeRole(currentUser: User | null, managedUser: UserProfileResponse) {
  return Boolean(
    currentUser &&
      currentUser.role === 'admin' &&
      managedUser.accountState.toLowerCase() === 'active' &&
      currentUser.email.toLowerCase() !== managedUser.email.toLowerCase(),
  )
}

function canEraseData(currentUser: User | null, managedUser: UserProfileResponse) {
  // `inactive` = a real account that was activated then deactivated (a pending
  // invite can't be deactivated), so it's the one state where a GDPR erase
  // applies — and the only one the backend accepts alongside `cancelled`.
  // Withdrawn / expired invites have no footprint, so "Delete user" removes
  // them outright and erase there would just be noise.
  return Boolean(
    currentUser &&
      currentUser.role === 'admin' &&
      !managedUser.purgedAt &&
      managedUser.accountState.toLowerCase() === 'inactive' &&
      currentUser.email.toLowerCase() !== managedUser.email.toLowerCase(),
  )
}

function canRequestSuperAdminTransfer(currentUser: User | null, managedUser: UserProfileResponse) {
  const targetRole = managedUser.role.toLowerCase()
  return Boolean(
    currentUser &&
      currentUser.role === 'admin' &&
      (targetRole === 'moderator' || targetRole === 'admin') &&
      managedUser.accountState.toLowerCase() === 'active' &&
      currentUser.email.toLowerCase() !== managedUser.email.toLowerCase(),
  )
}
