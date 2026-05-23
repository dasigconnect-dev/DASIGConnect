import type { UserProfileResponse } from '../../api/authApi'
import type { User } from '../../types/auth.types'

interface UserManagementPanelProps {
  currentUser: User | null
  users: UserProfileResponse[]
  loading: boolean
  selectedUserId: string | null
  updatingUserId: string | null
  onSelectUser: (id: string | null) => void
  onToggleUserStatus: (user: UserProfileResponse) => void
}

export default function UserManagementPanel({
  currentUser,
  users,
  loading,
  selectedUserId,
  updatingUserId,
  onSelectUser,
  onToggleUserStatus,
}: UserManagementPanelProps) {
  const selectedUser = users.find((managedUser) => managedUser.id === selectedUserId) ?? null

  return (
    <section className="dash-management-panel">
      <div className="dash-management-head">
        <div>
          <div className="dash-management-title">Users</div>
          <div className="dash-management-sub">
            {users.length} account{users.length === 1 ? '' : 's'} in this institution
          </div>
        </div>
      </div>

      {loading ? (
        <div className="dash-empty-row">Loading users...</div>
      ) : users.length === 0 ? (
        <div className="dash-empty-row">No users found.</div>
      ) : (
        <div className="dash-compact-list">
          {users.map((managedUser) => (
            <button
              type="button"
              className={`dash-compact-row dash-user-row${
                selectedUserId === managedUser.id ? ' is-selected' : ''
              }`}
              key={managedUser.id}
              onClick={() => onSelectUser(selectedUserId === managedUser.id ? null : managedUser.id)}
              aria-expanded={selectedUserId === managedUser.id}
            >
              <div>
                <div className="dash-compact-primary">{managedUser.email}</div>
                <div className="dash-compact-meta">
                  {formatRoleLabel(managedUser.role)} - {formatAccountState(managedUser.accountState)}
                </div>
              </div>
              <span className={`dash-state-pill ${stateClass(managedUser.accountState)}`}>
                {formatAccountState(managedUser.accountState)}
              </span>
            </button>
          ))}
        </div>
      )}

      {selectedUser && (
        <div className="dash-user-action-panel">
          <div className="dash-user-action-head">
            <div>
              <div className="dash-user-action-title">{selectedUser.email}</div>
              <div className="dash-user-action-sub">
                {formatRoleLabel(selectedUser.role)} - {selectedUser.institutionName || 'Institution account'}
              </div>
            </div>
            <span className={`dash-state-pill ${stateClass(selectedUser.accountState)}`}>
              {formatAccountState(selectedUser.accountState)}
            </span>
          </div>

          <div className="dash-user-action-meta">
            <div>
              <span>Account</span>
              <strong>{formatAccountState(selectedUser.accountState)}</strong>
            </div>
            <div>
              <span>Created</span>
              <strong>{formatDate(selectedUser.createdAt)}</strong>
            </div>
          </div>

          {canToggleUserStatus(currentUser, selectedUser) ? (
            <button
              type="button"
              className={`dash-user-action-btn${
                selectedUser.accountState.toLowerCase() === 'inactive' ? ' is-reactivate' : ''
              }`}
              disabled={updatingUserId === selectedUser.id}
              onClick={() => onToggleUserStatus(selectedUser)}
            >
              <i
                className={
                  selectedUser.accountState.toLowerCase() === 'inactive'
                    ? 'ti ti-user-check'
                    : 'ti ti-user-off'
                }
              ></i>
              {updatingUserId === selectedUser.id
                ? 'Updating...'
                : selectedUser.accountState.toLowerCase() === 'inactive'
                  ? 'Reactivate user'
                  : 'Deactivate user'}
            </button>
          ) : (
            <div className="dash-user-action-note">No account action is available for your role.</div>
          )}
        </div>
      )}
    </section>
  )
}

function formatRoleLabel(value: string) {
  const normalized = value.toLowerCase()
  return normalized.charAt(0).toUpperCase() + normalized.slice(1)
}

function formatDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'soon'
  return date.toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
  })
}

function formatAccountState(value: string) {
  return value
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function stateClass(value: string) {
  const normalized = value.toLowerCase()
  if (normalized.includes('inactive')) return 'state-muted'
  if (normalized.includes('active')) return 'state-active'
  if (normalized.includes('undelivered')) return 'state-warning'
  if (normalized.includes('pending')) return 'state-pending'
  return 'state-muted'
}

function canToggleUserStatus(currentUser: User | null, managedUser: UserProfileResponse) {
  if (!currentUser) return false
  const state = managedUser.accountState.toLowerCase()
  if (state !== 'active' && state !== 'inactive') return false
  if (currentUser.role === 'admin') return true
  return currentUser.role === 'validator' && managedUser.role.toLowerCase() === 'contributor'
}
