import { useState } from 'react'
import type { UserProfileResponse } from '../../../api/authApi'
import type { User } from '../../../types/auth.types'
import ActionMenu from './ActionMenu'
import { InlineSpinner, SkeletonRows } from './LoadingPrimitives'

interface InstitutionUsersCardProps {
  currentUser: User | null
  users: UserProfileResponse[]
  loading: boolean
  updatingUserId: string | null
  onToggleUserStatus: (user: UserProfileResponse) => void
}

type RoleFilter = 'all' | 'validator' | 'contributor'
type StatusFilter = 'all' | 'active' | 'inactive' | 'pending_activation'

export default function InstitutionUsersCard({
  currentUser,
  users,
  loading,
  updatingUserId,
  onToggleUserStatus,
}: InstitutionUsersCardProps) {
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState<RoleFilter>('all')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all')

  const filtered = users.filter((user) => {
    if (search && !user.email.toLowerCase().includes(search.toLowerCase())) {
      return false
    }
    if (roleFilter !== 'all' && user.role.toLowerCase() !== roleFilter) {
      return false
    }
    if (statusFilter !== 'all' && user.accountState.toLowerCase() !== statusFilter) {
      return false
    }
    return true
  })

  const hasFilters = search !== '' || roleFilter !== 'all' || statusFilter !== 'all'

  const activeCount = users.filter((u) => u.accountState.toLowerCase() === 'active').length
  const validatorCount = users.filter((u) => u.role.toLowerCase() === 'validator').length
  const contributorCount = users.filter((u) => u.role.toLowerCase() === 'contributor').length

  return (
    <section className={`um-data-card${loading ? ' is-busy' : ''}`} aria-busy={loading}>
      <div className="um-data-card-header">
        <div className="um-data-card-title-group">
          <h2 className="um-data-card-title">Users</h2>
          <span className="um-data-card-count">{users.length}</span>
          {loading && users.length > 0 && (
            <span className="um-refresh-pill">
              <InlineSpinner /> Refreshing
            </span>
          )}
        </div>
        <div className="um-user-meta-pills">
          <span className="um-meta-pill">
            <i className="ti ti-user-check" aria-hidden="true"></i>
            {activeCount} active
          </span>
          <span className="um-meta-pill">
            <i className="ti ti-shield-check" aria-hidden="true"></i>
            {validatorCount} validator{validatorCount !== 1 ? 's' : ''}
          </span>
          <span className="um-meta-pill">
            <i className="ti ti-pencil" aria-hidden="true"></i>
            {contributorCount} contributor{contributorCount !== 1 ? 's' : ''}
          </span>
        </div>
      </div>

      <div className="um-filter-bar">
        <div className="um-search-wrap">
          <i className="ti ti-search um-search-icon" aria-hidden="true"></i>
          <input
            type="search"
            className="um-search-input"
            placeholder="Search by email…"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            aria-label="Search users"
          />
        </div>
        <div className="um-filter-pills" role="group" aria-label="Filter by role">
          {(['all', 'contributor', 'validator'] as RoleFilter[]).map((value) => (
            <button
              key={value}
              type="button"
              className={`um-filter-pill${roleFilter === value ? ' is-active' : ''}`}
              onClick={() => setRoleFilter(value)}
            >
              {value === 'all' ? 'All Roles' : value.charAt(0).toUpperCase() + value.slice(1)}
            </button>
          ))}
        </div>
        <div className="um-filter-pills" role="group" aria-label="Filter by status">
          {([
            { value: 'all', label: 'All Status' },
            { value: 'active', label: 'Active' },
            { value: 'inactive', label: 'Inactive' },
            { value: 'pending_activation', label: 'Pending' },
          ] as { value: StatusFilter; label: string }[]).map(({ value, label }) => (
            <button
              key={value}
              type="button"
              className={`um-filter-pill${statusFilter === value ? ' is-active' : ''}`}
              onClick={() => setStatusFilter(value)}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {loading && users.length === 0 ? (
        <UsersTableSkeleton />
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
                <th>User</th>
                <th>Role</th>
                <th>Institution</th>
                <th>Status</th>
                <th>Joined</th>
                <th aria-label="Actions"></th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((managedUser) => {
                const isUpdating = updatingUserId === managedUser.id
                const isActive = managedUser.accountState.toLowerCase() === 'active'
                const isInactive = managedUser.accountState.toLowerCase() === 'inactive'
                const canToggle = canToggleUserStatus(currentUser, managedUser)

                const menuItems = [
                  canToggle
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
                  {
                    label: 'Reset password',
                    icon: 'ti ti-key',
                    onClick: () => undefined,
                    disabled: true,
                  },
                ].filter((item): item is NonNullable<typeof item> => item !== null)

                return (
                  <tr key={managedUser.id} className={isInactive ? 'is-inactive-row' : ''}>
                    <td>
                      <div className="um-user-cell">
                        <span className="um-user-avatar">
                          {managedUser.email.charAt(0).toUpperCase()}
                        </span>
                        <div>
                          <strong>{managedUser.email}</strong>
                          {managedUser.institutionName && (
                            <span className="um-table-sub">{managedUser.institutionName}</span>
                          )}
                        </div>
                      </div>
                    </td>
                    <td>
                      <span className={`um-role-tag is-${managedUser.role.toLowerCase()}`}>
                        {formatRoleLabel(managedUser.role)}
                      </span>
                    </td>
                    <td>{managedUser.institutionName || '—'}</td>
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

function UsersTableSkeleton() {
  return (
    <div className="um-table-wrap">
      <table className="um-table">
        <thead>
          <tr>
            <th>User</th>
            <th>Role</th>
            <th>Institution</th>
            <th>Status</th>
            <th>Joined</th>
            <th aria-label="Actions"></th>
          </tr>
        </thead>
        <tbody>
          <SkeletonRows rows={5} columns={6} />
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
  if (currentUser.role === 'admin') return true
  return currentUser.role === 'validator' && managedUser.role.toLowerCase() === 'contributor'
}
