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
  onReassign?: (user: UserProfileResponse) => void
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
}

type RoleFilter = 'all' | 'administrator' | 'contributor'
type StatusFilter = 'all' | 'active' | 'pending' | 'cancelled' | 'inactive'

export default function InstitutionUsersCard({
  currentUser,
  users,
  loading,
  updatingUserId,
  onToggleUserStatus,
  onDeleteUser,
  onCancelInvitation,
  onReassign,
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
}: InstitutionUsersCardProps) {
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState<RoleFilter>('all')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all')

  const statusCounts = useMemo(() => ({
    all: users.length,
    active: users.filter((u) => u.accountState.toLowerCase() === 'active').length,
    pending: users.filter((u) => u.accountState.toLowerCase().includes('pending')).length,
    cancelled: users.filter((u) => {
      const s = u.accountState.toLowerCase()
      return s === 'inactive' || s === 'expired' || s === 'cancelled'
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
        if (!state.includes('pending')) return false
      } else if (statusFilter === 'cancelled' || statusFilter === 'inactive') {
        if (state !== 'inactive' && state !== 'expired' && state !== 'cancelled') return false
      }
    }
    return true
  })

  const hasFilters = search !== '' || (showFilterPills && ((showRoleControls && roleFilter !== 'all') || statusFilter !== 'all'))

  return (
    <section
      className={`um-data-card${variant === 'directory' ? ' is-directory' : ''}${loading ? ' is-busy' : ''}`}
      aria-busy={loading}
    >
      <div className="um-data-card-header">
        <div className="um-data-card-heading">
          <div className="um-data-card-title-group">
            <h2 className="um-data-card-title">{title}</h2>
            <span className="um-data-card-count">{users.length}</span>
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

      <div className={`um-filter-bar um-users-filter-bar${showFilterPills ? '' : ' is-search-only'}`}>
        <div className="um-filter-group">
          <span className="um-filter-label">Search</span>
          <div className="um-search-wrap">
            <i className="ti ti-search um-search-icon" aria-hidden="true"></i>
            <input
              type="search"
              className="um-search-input"
              placeholder="Name or email…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              aria-label="Search users"
            />
          </div>
        </div>
        {showFilterPills && showRoleControls && (
          <>
            <div className="um-filter-divider" role="separator" aria-hidden="true"></div>
            <div className="um-filter-group">
              <span className="um-filter-label">Role</span>
              <div className="um-filter-pills" role="group" aria-label="Filter by role">
                {(['all', 'contributor', 'administrator'] as RoleFilter[]).map((value) => (
                  <button
                    key={value}
                    type="button"
                    className={`um-filter-pill${roleFilter === value ? ' is-active' : ''}`}
                    onClick={() => setRoleFilter(value)}
                  >
                    {value === 'all'
                      ? variant === 'directory' ? 'All' : 'All Roles'
                      : value.charAt(0).toUpperCase() + value.slice(1)}
                  </button>
                ))}
              </div>
            </div>
          </>
        )}
        {showFilterPills && (
          <>
            <div className="um-filter-divider" role="separator" aria-hidden="true"></div>
            <div className="um-filter-group">
              <span className="um-filter-label">Status</span>
              <div className="um-filter-pills" role="group" aria-label="Filter by status">
                {([
                  { value: 'all', label: variant === 'directory' ? 'All' : 'All Status', count: statusCounts.all },
                  { value: 'active', label: 'Active', count: statusCounts.active },
                  { value: 'pending', label: 'Pending', count: statusCounts.pending },
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
          </>
        )}
      </div>

      {loading && users.length === 0 ? (
        <UsersTableSkeleton
          showRoleControls={showRoleControls}
          showInstitutionColumn={showInstitutionColumn}
          userColumnLabel={userColumnLabel}
        />
      ) : filtered.length === 0 ? (
        <div className="um-empty-state">
          {hasFilters ? (
            <>
              <i className="ti ti-filter-off"></i>
              <span>No users match your filters.</span>
              <button
                type="button"
                className="um-empty-clear"
                onClick={() => { setSearch(''); setRoleFilter('all'); setStatusFilter('all') }}
              >
                Clear filters
              </button>
            </>
          ) : (
            <>
              <i className="ti ti-users-off"></i>
              <span>No users found for this institution.</span>
            </>
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
                const isActive = managedUser.accountState.toLowerCase() === 'active'
                const isInactive = managedUser.accountState.toLowerCase() === 'inactive'
                const isPending = managedUser.accountState.toLowerCase().includes('pending')
                const canManage = canToggleUserStatus(currentUser, managedUser)
                const canRemove = canRemoveUser(currentUser, managedUser)
                const displayName = getUserDisplayName(managedUser)
                const initials = getUserInitials(managedUser)

                const menuItems = isPending
                  ? [
                      {
                        label: 'Cancel invitation',
                        icon: 'ti ti-ban',
                        onClick: () => onCancelInvitation(managedUser),
                        dangerous: true,
                      },
                      canRemove ? {
                        label: 'Remove user',
                        icon: 'ti ti-trash',
                        onClick: () => onDeleteUser(managedUser),
                        dangerous: true,
                      } : null,
                    ].filter((item): item is NonNullable<typeof item> => item !== null)
                  : [
                      {
                        label: 'Reset password',
                        icon: 'ti ti-key',
                        onClick: () => undefined,
                        disabled: true,
                      },
                      canManage
                        ? {
                            label: isUpdating
                              ? 'Updating…'
                              : isActive
                                ? 'Deactivate account'
                                : 'Reactivate account',
                            icon: isActive ? 'ti ti-user-off' : 'ti ti-user-check',
                            onClick: () => onToggleUserStatus(managedUser),
                            disabled: isUpdating,
                            dangerous: isActive,
                          }
                        : null,
                      onReassign && managedUser.role.toLowerCase() === 'contributor'
                        ? {
                            label: 'Reassign institution',
                            icon: 'ti ti-transfer',
                            onClick: () => onReassign(managedUser),
                          }
                        : null,
                      canRemove ? {
                        label: 'Remove user',
                        icon: 'ti ti-trash',
                        onClick: () => onDeleteUser(managedUser),
                        dangerous: true,
                      } : null,
                    ].filter((item): item is NonNullable<typeof item> => item !== null)

                return (
                  <tr key={managedUser.id} className={isInactive ? 'is-inactive-row' : ''}>
                    <td>
                      <div className="um-user-cell">
                        <UserAvatarEditor
                          user={managedUser}
                          initials={initials}
                          uploading={avatarUploadingUserId === managedUser.id}
                          onUpload={onAvatarUpload}
                        />
                        <div>
                          <strong>{displayName}</strong>
                          <span className="um-user-email">{managedUser.email}</span>
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
                      <span className={`um-badge ${stateClass(managedUser.accountState)}`}>
                        {isUpdating ? (
                          <><InlineSpinner /> Updating</>
                        ) : (
                          formatAccountState(managedUser.accountState)
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
    </section>
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
      {editable && !hasImage && (
        <span className="um-user-avatar-pencil" aria-hidden="true">
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

function formatAccountState(value: string) {
  return value
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function formatDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}

function stateClass(value: string) {
  const n = value.toLowerCase()
  if (n.includes('inactive')) return 'is-muted'
  if (n.includes('active')) return 'is-active'
  if (n.includes('pending')) return 'is-pending'
  return 'is-muted'
}

function canToggleUserStatus(currentUser: User | null, managedUser: UserProfileResponse) {
  if (!currentUser) return false
  const state = managedUser.accountState.toLowerCase()
  if (state !== 'active' && state !== 'inactive') return false
  return currentUser.role === 'super_administrator' || currentUser.role === 'administrator'
}

function canRemoveUser(currentUser: User | null, managedUser: UserProfileResponse) {
  if (!currentUser) return false
  const managedRole = managedUser.role.toLowerCase()
  if (managedRole === 'administrator' || managedRole === 'super_administrator') {
    return currentUser.role === 'super_administrator'
  }
  return currentUser.role === 'super_administrator' || currentUser.role === 'administrator'
}
