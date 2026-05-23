import { useState } from 'react'
import type { PendingInvitationResponse } from '../../../api/authApi'
import type { InstitutionOption } from '../types'
import ActionMenu from './ActionMenu'
import { InlineSpinner, SkeletonRows } from './LoadingPrimitives'

interface PendingInvitationsCardProps {
  invitations: PendingInvitationResponse[]
  institutions: InstitutionOption[]
  loading: boolean
  resendingInvitationId: string | null
  onResend: (id: string) => void
}

type RoleFilter = 'all' | 'validator' | 'contributor'
type ExpiryFilter = 'all' | 'soon' | 'expired'

export default function PendingInvitationsCard({
  invitations,
  institutions,
  loading,
  resendingInvitationId,
  onResend,
}: PendingInvitationsCardProps) {
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState<RoleFilter>('all')
  const [expiryFilter, setExpiryFilter] = useState<ExpiryFilter>('all')

  const filtered = invitations.filter((invite) => {
    if (search && !invite.recipientEmail.toLowerCase().includes(search.toLowerCase())) {
      return false
    }
    if (roleFilter !== 'all' && invite.assignedRole.toLowerCase() !== roleFilter) {
      return false
    }
    if (expiryFilter !== 'all') {
      const expiresMs = new Date(invite.expiresAt).getTime()
      const now = Date.now()
      if (expiryFilter === 'expired' && expiresMs > now) return false
      if (expiryFilter === 'soon' && (expiresMs <= now || expiresMs - now > 24 * 60 * 60 * 1000)) {
        return false
      }
    }
    return true
  })

  const hasFilters = search !== '' || roleFilter !== 'all' || expiryFilter !== 'all'

  return (
    <section className={`um-data-card${loading ? ' is-busy' : ''}`} aria-busy={loading}>
      <div className="um-data-card-header">
        <div className="um-data-card-title-group">
          <h2 className="um-data-card-title">Pending Invitations</h2>
          <span className="um-data-card-count">{invitations.length}</span>
          {loading && invitations.length > 0 && (
            <span className="um-refresh-pill">
              <InlineSpinner /> Refreshing
            </span>
          )}
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
            aria-label="Search invitations"
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
        <div className="um-filter-pills" role="group" aria-label="Filter by expiry">
          {([
            { value: 'all', label: 'All' },
            { value: 'soon', label: 'Expiring soon' },
            { value: 'expired', label: 'Expired' },
          ] as { value: ExpiryFilter; label: string }[]).map(({ value, label }) => (
            <button
              key={value}
              type="button"
              className={`um-filter-pill${expiryFilter === value ? ' is-active' : ''}`}
              onClick={() => setExpiryFilter(value)}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {loading && invitations.length === 0 ? (
        <InvitationTableSkeleton />
      ) : filtered.length === 0 ? (
        <div className="um-empty-state">
          {hasFilters ? (
            <>
              <i className="ti ti-filter-off"></i>
              <span>No invitations match your filters.</span>
              <button
                type="button"
                className="um-empty-clear"
                onClick={() => { setSearch(''); setRoleFilter('all'); setExpiryFilter('all') }}
              >
                Clear filters
              </button>
            </>
          ) : (
            <>
              <i className="ti ti-mail-off"></i>
              <span>No pending invitations.</span>
            </>
          )}
        </div>
      ) : (
        <div className="um-table-wrap">
          <table className="um-table">
            <thead>
              <tr>
                <th>Recipient</th>
                <th>Role</th>
                <th>Institution</th>
                <th>Expires</th>
                <th>Status</th>
                <th aria-label="Actions"></th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((invite) => {
                const isResending = resendingInvitationId === invite.id
                return (
                  <tr key={invite.id}>
                    <td>
                      <strong>{invite.recipientEmail}</strong>
                    </td>
                    <td>
                      <span className="um-role-tag">{formatRoleLabel(invite.assignedRole)}</span>
                    </td>
                    <td>{institutionName(invite.institutionId, institutions)}</td>
                    <td>
                      <span className={expiryClass(invite.expiresAt)}>
                        {formatDate(invite.expiresAt)}
                      </span>
                    </td>
                    <td>
                      <span className="um-badge is-pending">
                        {isResending ? (
                          <><InlineSpinner /> Resending</>
                        ) : (
                          'Pending'
                        )}
                      </span>
                    </td>
                    <td className="um-table-actions-cell">
                      <ActionMenu
                        align="right"
                        items={[
                          {
                            label: isResending ? 'Resending…' : 'Resend invitation',
                            icon: 'ti ti-send',
                            onClick: () => onResend(invite.id),
                            disabled: isResending,
                          },
                          {
                            label: 'Revoke invitation',
                            icon: 'ti ti-ban',
                            onClick: () => undefined,
                            disabled: true,
                            dangerous: true,
                          },
                        ]}
                      />
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

function InvitationTableSkeleton() {
  return (
    <div className="um-table-wrap">
      <table className="um-table">
        <thead>
          <tr>
            <th>Recipient</th>
            <th>Role</th>
            <th>Institution</th>
            <th>Expires</th>
            <th>Status</th>
            <th aria-label="Actions"></th>
          </tr>
        </thead>
        <tbody>
          <SkeletonRows rows={4} columns={6} />
        </tbody>
      </table>
    </div>
  )
}

function institutionName(id: string, institutions: InstitutionOption[]) {
  return institutions.find((inst) => inst.id === id)?.name || 'Institution'
}

function formatRoleLabel(value: string) {
  const n = value.toLowerCase()
  return n.charAt(0).toUpperCase() + n.slice(1)
}

function formatDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Soon'
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}

function expiryClass(value: string): string {
  const ms = new Date(value).getTime() - Date.now()
  if (ms <= 0) return 'um-expiry is-expired'
  if (ms < 24 * 60 * 60 * 1000) return 'um-expiry is-soon'
  return 'um-expiry'
}
