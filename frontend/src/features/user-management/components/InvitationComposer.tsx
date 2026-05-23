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
              Secure onboarding links sent directly to recipients
            </div>
          </div>
        </div>
        {recipientCount > 0 && (
          <span className={`um-composer-count${atLimit ? ' is-limit' : ''}`}>
            {recipientCount} / 15
          </span>
        )}
      </div>

      <form className="um-composer-form" onSubmit={onSubmit}>
        <div className="um-composer-field">
          <label className="um-composer-label">
            Recipients
            <span className="um-composer-label-hint">
              Enter emails — press Enter or comma to add
            </span>
          </label>
          <EmailChipsInput
            chips={chips}
            draft={emailDraft}
            onDraftChange={onDraftChange}
            onAdd={onAddChip}
            onRemove={onRemoveChip}
            disabled={sending}
          />
        </div>

        {canChooseRole && (
          <div className="um-composer-field">
            <label className="um-composer-label">
              Role
              {role === null && (
                <span className="um-composer-required">Required — choose one</span>
              )}
            </label>
            <div className="um-role-pills" role="group" aria-label="Assign role">
              <button
                type="button"
                className={`um-role-pill${role === 'contributor' ? ' is-active' : ''}`}
                onClick={() => onRoleChange('contributor')}
                disabled={sending}
              >
                <i className="ti ti-pencil" aria-hidden="true"></i>
                Contributor
              </button>
              <button
                type="button"
                className={`um-role-pill${role === 'validator' ? ' is-active' : ''}`}
                onClick={() => onRoleChange('validator')}
                disabled={sending}
              >
                <i className="ti ti-shield-check" aria-hidden="true"></i>
                Validator
              </button>
            </div>
          </div>
        )}

        {selectedInstitution && (
          <div className="um-composer-context">
            <i className="ti ti-building" aria-hidden="true"></i>
            <span>
              Sending to <strong>{selectedInstitution.name}</strong>
            </span>
          </div>
        )}

        <div className="um-composer-footer">
          <div className="um-csv-hint">
            <i className="ti ti-file-type-csv" aria-hidden="true"></i>
            <span>CSV bulk import — planned</span>
          </div>
          <button
            type="submit"
            className="um-send-btn"
            disabled={sending || recipientCount === 0 || !selectedInstitution || (canChooseRole && role === null)}
          >
            {sending ? (
              <>
                <InlineSpinner />
                Sending…
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
