import type { FormEvent } from 'react'
import type { InviteRole } from '../types'
import type { InstitutionOption } from '../types'
import EmailChipsInput from './EmailChipsInput'
import { InlineSpinner } from './LoadingPrimitives'

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
  onDraftChange: (value: string) => void
  onAddChip: (email: string) => void
  onRemoveChip: (index: number) => void
  onRoleChange: (value: InviteRole) => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
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
  onDraftChange,
  onAddChip,
  onRemoveChip,
  onRoleChange,
  onSubmit,
}: InvitationComposerProps) {
  const recipientCount = chips.length
  const atLimit = recipientCount >= maxRecipients
  const invalidRecipientCount = chips.filter((chip) => !isValidEmail(chip)).length

  return (
    <section
      className={`um-composer${sending ? ' is-busy' : ''}${embedded ? ' is-embedded' : ''}`}
      aria-busy={sending}
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
            {recipientCount > 0 && (
              <span className={`um-composer-count${atLimit ? ' is-limit' : ''}`}>
                {recipientCount} / {maxRecipients} recipients
              </span>
            )}
          </div>
        </div>
      )}

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
    </section>
  )
}

function isValidEmail(email: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}
