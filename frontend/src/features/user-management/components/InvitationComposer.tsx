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
  onDraftChange: (value: string) => void
  onAddChip: (email: string) => void
  onRemoveChip: (index: number) => void
  onRoleChange: (value: InviteRole) => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
}

export default function InvitationComposer({
  chips,
  emailDraft,
  role,
  selectedInstitution,
  canChooseRole,
  sending,
  onDraftChange,
  onAddChip,
  onRemoveChip,
  onRoleChange,
  onSubmit,
}: InvitationComposerProps) {
  const recipientCount = chips.length
  const atLimit = recipientCount >= 15
  const invalidRecipientCount = chips.filter((chip) => !isValidEmail(chip)).length

  return (
    <section className={`um-composer${sending ? ' is-busy' : ''}`} aria-busy={sending}>
      <div className="um-composer-header">
        <div className="um-composer-header-left">
          <div className="um-composer-icon">
            <i className="ti ti-send" aria-hidden="true"></i>
          </div>
          <div>
            <div className="um-composer-title">Send Invitations</div>
            <div className="um-composer-subtitle">
              {canChooseRole
                ? 'Invite contributors or validators securely into this institution workspace.'
                : 'Invite contributors securely into this institution workspace.'}
            </div>
          </div>
        </div>
        <div className="um-composer-header-meta">
          <span className="um-composer-priority">Primary workflow</span>
          {recipientCount > 0 && (
            <span className={`um-composer-count${atLimit ? ' is-limit' : ''}`}>
              {recipientCount} / 15 recipients
            </span>
          )}
        </div>
      </div>

      <form className="um-composer-form" onSubmit={onSubmit}>
        <div className="um-composer-section um-recipient-section">
          <div className="um-section-head">
            <div>
              <label className="um-composer-label" htmlFor="invitation-recipient-input">
                Recipients
              </label>
              <div className="um-composer-label-hint">
                Add one or more institutional email addresses. Press Enter or comma after each address.
              </div>
            </div>
            <span className={`um-recipient-meter${atLimit ? ' is-limit' : ''}`}>
              {recipientCount}/15
            </span>
          </div>
          <EmailChipsInput
            chips={chips}
            draft={emailDraft}
            onDraftChange={onDraftChange}
            onAdd={onAddChip}
            onRemove={onRemoveChip}
            disabled={sending}
            placeholder="name@institution.edu.ph, office@institution.edu.ph"
            inputId="invitation-recipient-input"
          />
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
                <div className="um-composer-label-hint">
                  Choose the access level these recipients will receive.
                </div>
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
                className={`um-role-pill${role === 'validator' ? ' is-active' : ''}`}
                onClick={() => onRoleChange('validator')}
                disabled={sending}
                role="radio"
                aria-checked={role === 'validator'}
              >
                <span className="um-role-pill-icon">
                  <i className="ti ti-shield-check" aria-hidden="true"></i>
                </span>
                <span>
                  <strong>Validator</strong>
                  <small>Reviews and approves submissions</small>
                </span>
              </button>
            </div>
          </div>
        )}

        {selectedInstitution && (
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
          <div className="um-csv-hint">
            <span className="um-csv-icon">
              <i className="ti ti-file-type-csv" aria-hidden="true"></i>
            </span>
            <span>
              CSV bulk import
              <strong>Planned</strong>
            </span>
          </div>
          <button
            type="submit"
            className="um-send-btn"
            disabled={sending || recipientCount === 0 || !selectedInstitution || (canChooseRole && role === null)}
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
