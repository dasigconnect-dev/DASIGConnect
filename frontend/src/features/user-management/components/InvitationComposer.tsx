import { useState, type FormEvent, type ChangeEvent } from 'react'
import type { InviteRole } from '../types'
import type { InstitutionOption } from '../types'
import EmailChipsInput from './EmailChipsInput'
import { InlineSpinner } from './LoadingPrimitives'
import {
  previewBatchInvitations,
  sendBatchInvitations,
  type BatchInvitationRow,
  type BatchInvitationPreviewResponse,
  type BatchInvitationSendResponse,
} from '../../../api/authApi'

interface InvitationComposerProps {
  chips: string[]
  emailDraft: string
  role: InviteRole
  selectedInstitution: InstitutionOption | null
  canChooseRole: boolean
  sending: boolean
  /** Network-wide invites (e.g. moderators) that are not bound to an institution workspace. */
  networkWide?: boolean
  /**
   * Rendered inside a modal that already has its own title/subtitle. Drops the
   * component's internal header, the destination-workspace recap card, and the
   * long helper text so the form isn't a card-within-a-card.
   */
  embedded?: boolean
  /** Max recipients per batch. Defaults to 15; callers with a tighter cap (e.g. the 3-admin limit) pass their remaining slots. */
  maxRecipients?: number
  allowBulk?: boolean
  onDraftChange: (value: string) => void
  onAddChip: (email: string) => void
  onRemoveChip: (index: number) => void
  onRoleChange: (value: InviteRole) => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  onBatchSuccess?: () => void
}

function composerSubtitle(
  canChooseRole: boolean,
  _institution: InstitutionOption | null,
  role: InviteRole,
  networkWide: boolean,
) {
  if (networkWide) return 'Invite moderators with network-wide access. Activation links expire after 72 hours.'
  if (canChooseRole) return 'Invite contributors or moderators securely into this institution workspace.'
  if (role === 'contributor') return 'Invite contributors securely into this institution workspace.'
  return 'Invite contributors securely into this institution workspace.'
}

function statusBadgeClass(status: string): string {
  switch (status) {
    case 'valid':
      return 'status-valid'
    case 'pending_will_be_superseded':
      return 'status-pending_will_be_superseded'
    case 'deactivated_use_reactivation':
      return 'status-deactivated_use_reactivation'
    default:
      return 'status-' + status
  }
}

function statusDisplayLabel(status: string): string {
  switch (status) {
    case 'valid':
      return 'Valid'
    case 'pending_will_be_superseded':
      return 'Will Supersede'
    case 'duplicate_in_batch':
      return 'Duplicate in Batch'
    case 'invalid_email':
      return 'Invalid Email'
    case 'invalid_role':
      return 'Invalid Role'
    case 'inactive_institution':
      return 'Inactive Institution'
    case 'active_account_exists':
      return 'Active Exists'
    case 'deactivated_use_reactivation':
      return 'Deactivated'
    case 'admin_cap_exceeded':
      return 'Admin Cap Exceeded'
    case 'role_not_permitted':
      return 'Role Not Permitted'
    default:
      return status
  }
}

export default function InvitationComposer({
  chips,
  emailDraft,
  role,
  selectedInstitution,
  canChooseRole,
  sending,
  networkWide = false,
  embedded = false,
  maxRecipients = 15,
  allowBulk = true,
  onDraftChange,
  onAddChip,
  onRemoveChip,
  onRoleChange,
  onSubmit,
  onBatchSuccess,
}: InvitationComposerProps) {
  const [inviteMode, setInviteMode] = useState<'single' | 'bulk'>('single')

  // Bulk mode states
  const [csvText, setCsvText] = useState('')
  const [previewLoading, setPreviewLoading] = useState(false)
  const [previewData, setPreviewData] = useState<BatchInvitationPreviewResponse | null>(null)
  const [batchSending, setBatchSending] = useState(false)
  const [batchSentResult, setBatchSentResult] = useState<BatchInvitationSendResponse | null>(null)
  const [batchError, setBatchError] = useState<string | null>(null)

  const recipientCount = chips.length
  const atLimit = recipientCount >= maxRecipients
  const invalidRecipientCount = chips.filter((chip) => !isValidEmail(chip)).length

  function parseCsvRows(rawText: string): BatchInvitationRow[] {
    const lines = rawText.split(/\r?\n/).map((l) => l.trim()).filter(Boolean)
    if (lines.length === 0) return []

    let start = 0
    const firstLower = lines[0].toLowerCase()
    if (firstLower.includes('email') && (firstLower.includes('role') || firstLower.includes('institution'))) {
      start = 1
    }

    const rows: BatchInvitationRow[] = []
    for (let i = start; i < lines.length; i++) {
      const parts = lines[i].split(',').map((p) => p.trim())
      if (!parts[0]) continue
      const email = parts[0]
      const rowRole = parts[1] || (role ?? 'contributor')
      const rowInst = parts[2] || (selectedInstitution?.code ?? selectedInstitution?.name ?? '')
      rows.push({
        email,
        role: rowRole,
        institutionCode: rowInst || null,
        institutionId: selectedInstitution?.id ?? null,
      })
    }
    return rows
  }

  async function handlePreviewBatch() {
    setBatchError(null)
    const rows = parseCsvRows(csvText)
    if (rows.length === 0) {
      setBatchError('Please provide at least one valid row in email, role, institution format.')
      return
    }
    if (rows.length > 200) {
      setBatchError(`Batch size cannot exceed 200 rows (found ${rows.length}).`)
      return
    }

    setPreviewLoading(true)
    try {
      const res = await previewBatchInvitations(rows)
      setPreviewData(res.data)
    } catch (err: unknown) {
      const msg = err && typeof err === 'object' && 'response' in err
        ? (err as { response?: { data?: { error?: { message?: string } } } }).response?.data?.error?.message
        : 'Failed to preview batch invitations.'
      setBatchError(msg || 'Failed to preview batch invitations.')
    } finally {
      setPreviewLoading(false)
    }
  }

  async function handleSendBatch() {
    if (!previewData || previewData.validCount === 0) return
    setBatchError(null)
    setBatchSending(true)

    const validRows = previewData.rows
      .filter((r) => r.status === 'valid' || r.status === 'pending_will_be_superseded')
      .map((r) => ({
        email: r.email,
        role: r.role,
        institutionId: r.institutionId,
        institutionCode: r.institutionName ?? undefined,
      }))

    try {
      const res = await sendBatchInvitations(validRows)
      setBatchSentResult(res.data)
      setPreviewData(null)
      onBatchSuccess?.()
    } catch (err: unknown) {
      const msg = err && typeof err === 'object' && 'response' in err
        ? (err as { response?: { data?: { error?: { message?: string } } } }).response?.data?.error?.message
        : 'Failed to send batch invitations.'
      setBatchError(msg || 'Failed to send batch invitations.')
    } finally {
      setBatchSending(false)
    }
  }

  function handleFileUpload(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = (event) => {
      const content = String(event.target?.result || '')
      setCsvText(content)
      setPreviewData(null)
      setBatchSentResult(null)
      setBatchError(null)
    }
    reader.readAsText(file)
  }

  function handleLoadSample() {
    const instCode = selectedInstitution?.code || 'CITU'
    const domain = selectedInstitution?.emailDomain || 'cit.edu'
    const sample = `email,role,institution\ncontributor1@${domain},contributor,${instCode}\ncontributor2@${domain},contributor,${instCode}\nmoderator1@${domain},moderator,${instCode}`
    setCsvText(sample)
    setPreviewData(null)
    setBatchSentResult(null)
    setBatchError(null)
  }

  return (
    <section
      className={`um-composer${sending || batchSending ? ' is-busy' : ''}${embedded ? ' is-embedded' : ''}`}
      aria-busy={sending || batchSending}
    >
      {!embedded && (
        <div className="um-composer-header">
          <div className="um-composer-header-left">
            <div className="um-composer-icon">
              <i className="ti ti-send" aria-hidden="true"></i>
            </div>
            <div>
              <div className="um-composer-title">Send Invitations</div>
              <div className="um-composer-subtitle">
                {composerSubtitle(canChooseRole, selectedInstitution, role, networkWide)}
              </div>
            </div>
          </div>
          <div className="um-composer-header-meta">
            {inviteMode === 'single' && recipientCount > 0 && (
              <span className={`um-composer-count${atLimit ? ' is-limit' : ''}`}>
                {recipientCount} / {maxRecipients} recipients
              </span>
            )}
          </div>
        </div>
      )}

      {allowBulk && (
        <div className="um-composer-mode-tabs" role="tablist" aria-label="Invitation Mode">
          <button
            type="button"
            role="tab"
            aria-selected={inviteMode === 'single'}
            className={`um-composer-mode-tab${inviteMode === 'single' ? ' is-active' : ''}`}
            onClick={() => {
              setInviteMode('single')
              setBatchError(null)
            }}
          >
            <i className="ti ti-mail" aria-hidden="true"></i> Quick Invite
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={inviteMode === 'bulk'}
            className={`um-composer-mode-tab${inviteMode === 'bulk' ? ' is-active' : ''}`}
            onClick={() => {
              setInviteMode('bulk')
              setBatchError(null)
            }}
          >
            <i className="ti ti-file-spreadsheet" aria-hidden="true"></i> Bulk CSV / Structured
          </button>
        </div>
      )}

      {inviteMode === 'bulk' ? (
        <div className="um-batch-section">
          {batchSentResult ? (
            <div className="um-batch-success" role="status">
              <div className="um-batch-success-icon">
                <i className="ti ti-circle-check" aria-hidden="true"></i>
              </div>
              <div className="um-batch-success-body">
                <div className="um-batch-success-title">Batch Invitations Processed</div>
                <div className="um-batch-success-desc">
                  Sent {batchSentResult.sentCount} invitation{batchSentResult.sentCount === 1 ? '' : 's'}.
                  {batchSentResult.rejectedCount > 0 && ` ${batchSentResult.rejectedCount} row(s) were rejected/skipped.`}
                </div>
                <div className="um-batch-success-meta">Batch ID: {batchSentResult.batchId}</div>
                <button
                  type="button"
                  className="um-batch-btn-secondary"
                  onClick={() => {
                    setBatchSentResult(null)
                    setPreviewData(null)
                    setCsvText('')
                  }}
                >
                  <i className="ti ti-plus" aria-hidden="true"></i> Send Another Batch
                </button>
              </div>
            </div>
          ) : previewData ? (
            <div className="um-batch-preview-view">
              <div className="um-batch-metrics">
                <div className="um-batch-metric-item">
                  <strong>Total:</strong> {previewData.totalRows}
                </div>
                <div className="um-batch-metric-item">
                  <span className="um-batch-pill is-valid">
                    {previewData.validCount} Valid
                  </span>
                </div>
                {previewData.invalidCount > 0 && (
                  <div className="um-batch-metric-item">
                    <span className="um-batch-pill is-error">
                      {previewData.invalidCount} Issues
                    </span>
                  </div>
                )}
              </div>

              <div className="um-batch-preview-table-wrap">
                <table className="um-batch-table" aria-label="Batch Preview Table">
                  <thead>
                    <tr>
                      <th>#</th>
                      <th>Email</th>
                      <th>Role</th>
                      <th>Institution</th>
                      <th>Status</th>
                      <th>Details</th>
                    </tr>
                  </thead>
                  <tbody>
                    {previewData.rows.map((row) => (
                      <tr key={row.index} className={`um-batch-row-${row.status}`}>
                        <td>{row.index}</td>
                        <td className="um-batch-email">{row.email}</td>
                        <td>
                          <span className="um-batch-role-tag">{row.role}</span>
                        </td>
                        <td>{row.institutionName || row.institutionId || '—'}</td>
                        <td>
                          <span className={`um-batch-status-badge ${statusBadgeClass(row.status)}`}>
                            {statusDisplayLabel(row.status)}
                          </span>
                        </td>
                        <td className="um-batch-reason">
                          {row.reason || (row.status === 'valid' ? 'Ready to send' : '—')}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {batchError && (
                <div className="um-field-warning" role="alert">
                  <i className="ti ti-alert-circle" aria-hidden="true"></i>
                  {batchError}
                </div>
              )}

              <div className="um-composer-footer" style={{ marginTop: '16px', display: 'flex', gap: '10px' }}>
                <button
                  type="button"
                  className="um-batch-btn-secondary"
                  disabled={batchSending}
                  onClick={() => setPreviewData(null)}
                >
                  <i className="ti ti-arrow-left" aria-hidden="true"></i> Edit Batch
                </button>
                <button
                  type="button"
                  className="um-send-btn"
                  disabled={batchSending || previewData.validCount === 0}
                  onClick={() => void handleSendBatch()}
                >
                  {batchSending ? (
                    <>
                      <InlineSpinner />
                      Sending Batch...
                    </>
                  ) : (
                    <>
                      <i className="ti ti-send" aria-hidden="true"></i>
                      Confirm & Send {previewData.validCount} Valid Invitation{previewData.validCount === 1 ? '' : 's'}
                    </>
                  )}
                </button>
              </div>
            </div>
          ) : (
            <div className="um-batch-input-view">
              <div className="um-section-head">
                <div>
                  <label className="um-composer-label" htmlFor="bulk-invitation-input">
                    CSV / Structured Recipient List
                  </label>
                  <div className="um-composer-label-hint">
                    Enter one recipient per row formatted as: <code>email, role, institution</code>. Up to 200 rows.
                  </div>
                </div>
              </div>

              <div className="um-batch-toolbar">
                <div className="um-batch-toolbar-actions">
                  <label className="um-batch-btn-secondary" style={{ cursor: 'pointer' }}>
                    <i className="ti ti-upload" aria-hidden="true"></i> Upload CSV
                    <input
                      type="file"
                      accept=".csv,text/csv,text/plain"
                      style={{ display: 'none' }}
                      onChange={handleFileUpload}
                    />
                  </label>
                  <button
                    type="button"
                    className="um-batch-btn-secondary"
                    onClick={handleLoadSample}
                  >
                    <i className="ti ti-template" aria-hidden="true"></i> Load Sample Data
                  </button>
                  {csvText && (
                    <button
                      type="button"
                      className="um-batch-btn-secondary"
                      onClick={() => setCsvText('')}
                    >
                      <i className="ti ti-trash" aria-hidden="true"></i> Clear
                    </button>
                  )}
                </div>
              </div>

              <textarea
                id="bulk-invitation-input"
                className="um-batch-textarea"
                rows={6}
                placeholder="contributor@institution.edu.ph, contributor, WVSU&#10;moderator@institution.edu.ph, moderator, WVSU"
                value={csvText}
                onChange={(e) => setCsvText(e.target.value)}
              />

              {batchError && (
                <div className="um-field-warning" role="alert">
                  <i className="ti ti-alert-circle" aria-hidden="true"></i>
                  {batchError}
                </div>
              )}

              <div className="um-composer-footer" style={{ marginTop: '14px' }}>
                <button
                  type="button"
                  className="um-send-btn"
                  disabled={previewLoading || !csvText.trim()}
                  onClick={() => void handlePreviewBatch()}
                >
                  {previewLoading ? (
                    <>
                      <InlineSpinner />
                      Validating Rows...
                    </>
                  ) : (
                    <>
                      <i className="ti ti-eye" aria-hidden="true"></i>
                      Preview Batch & Validate
                    </>
                  )}
                </button>
              </div>
            </div>
          )}
        </div>
      ) : (
        <form className="um-composer-form" onSubmit={onSubmit}>
          <div className="um-composer-section um-recipient-section">
            <div className="um-section-head">
              <div>
                <label className="um-composer-label" htmlFor="invitation-recipient-input">
                  Recipients
                </label>
                <div className="um-composer-label-hint">
                  {embedded
                    ? 'Separate addresses with Enter or a comma.'
                    : 'Add one or more institutional email addresses. Press Enter or comma after each address.'}
                </div>
              </div>
              <span className={`um-recipient-meter${atLimit ? ' is-limit' : ''}`}>
                {recipientCount}/{maxRecipients}
              </span>
            </div>
            <EmailChipsInput
              chips={chips}
              draft={emailDraft}
              onDraftChange={onDraftChange}
              onAdd={(email) => {
                if (chips.length < maxRecipients) onAddChip(email)
              }}
              onRemove={onRemoveChip}
              disabled={sending || atLimit}
              placeholder={
                networkWide
                  ? 'moderator@example.edu.ph, office@example.edu.ph'
                  : 'name@institution.edu.ph, office@institution.edu.ph'
              }
              inputId="invitation-recipient-input"
            />
            {atLimit && (
              <div className="um-composer-label-hint">
                {maxRecipients === 1
                  ? 'Only 1 invitation can be sent in this batch.'
                  : `Up to ${maxRecipients} invitations can be sent in this batch.`}
              </div>
            )}
            {invalidRecipientCount > 0 && (
              <div className="um-field-warning" role="alert">
                <i className="ti ti-alert-circle" aria-hidden="true"></i>
                {invalidRecipientCount} recipient{invalidRecipientCount === 1 ? '' : 's'} need a valid email format.
              </div>
            )}
          </div>

          {canChooseRole && (
            <div className="um-composer-section">
              <div className="um-section-head">
                <div>
                  <div className="um-composer-label">
                    Role assignment
                    {role === null && (
                      <span className="um-composer-required">Required</span>
                    )}
                  </div>
                  {!embedded && (
                    <div className="um-composer-label-hint">
                      Choose the access level these recipients will receive.
                    </div>
                  )}
                </div>
              </div>
              <div className="um-role-pills" role="radiogroup" aria-label="Assign role">
                <button
                  type="button"
                  className={`um-role-pill${role === 'contributor' ? ' is-active' : ''}`}
                  onClick={() => onRoleChange('contributor')}
                  disabled={sending}
                  role="radio"
                  aria-checked={role === 'contributor'}
                >
                  <span className="um-role-pill-icon">
                    <i className="ti ti-pencil" aria-hidden="true"></i>
                  </span>
                  <span>
                    <strong>Contributor</strong>
                    <small>Submits content for review</small>
                  </span>
                </button>
                <button
                  type="button"
                  className={`um-role-pill${role === 'moderator' ? ' is-active' : ''}`}
                  onClick={() => onRoleChange('moderator')}
                  disabled={sending}
                  role="radio"
                  aria-checked={role === 'moderator'}
                >
                  <span className="um-role-pill-icon">
                    <i className="ti ti-shield-check" aria-hidden="true"></i>
                  </span>
                  <span>
                    <strong>Moderator</strong>
                    <small>Reviews and approves submissions</small>
                  </span>
                </button>
              </div>
            </div>
          )}

          {selectedInstitution && !embedded && (
            <div className="um-workspace-card" aria-label="Destination workspace">
              <div className="um-workspace-icon">
                <i className="ti ti-building-community" aria-hidden="true"></i>
              </div>
              <div className="um-workspace-body">
                <div className="um-workspace-kicker">Destination Workspace</div>
                <div className="um-workspace-name">{selectedInstitution.name}</div>
                <div className="um-workspace-meta">
                  <span>#{selectedInstitution.code}</span>
                  <span>{selectedInstitution.emailDomain}</span>
                </div>
              </div>
            </div>
          )}

          <div className="um-composer-footer">
            <button
              type="submit"
              className="um-send-btn"
              disabled={
                sending ||
                recipientCount === 0 ||
                (!networkWide && !selectedInstitution) ||
                (canChooseRole && role === null)
              }
            >
              {sending ? (
                <>
                  <InlineSpinner />
                  Sending...
                </>
              ) : (
                <>
                  <i className="ti ti-send" aria-hidden="true"></i>
                  Send {recipientCount > 0 ? `${recipientCount} Invitation${recipientCount === 1 ? '' : 's'}` : 'Invitations'}
                </>
              )}
            </button>
          </div>
        </form>
      )}
    </section>
  )
}

function isValidEmail(email: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}
