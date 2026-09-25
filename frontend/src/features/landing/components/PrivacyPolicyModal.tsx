import { useEffect } from 'react';

interface PrivacyPolicyModalProps {
  open: boolean;
  onClose: () => void;
}

export default function PrivacyPolicyModal({ open, onClose }: PrivacyPolicyModalProps) {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    if (open) {
      document.body.style.overflow = 'hidden';
      window.addEventListener('keydown', handleKeyDown);
    } else {
      document.body.style.overflow = '';
    }
    return () => {
      document.body.style.overflow = '';
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="privacy-modal-overlay"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="privacy-modal-title"
    >
      <div className="privacy-modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="privacy-modal-header">
          <div className="privacy-modal-title" id="privacy-modal-title">
            <i className="ti ti-shield-check"></i>
            DASIGConnect Privacy Policy
          </div>
          <button
            type="button"
            className="privacy-modal-close"
            onClick={onClose}
            aria-label="Close Privacy Policy Modal"
          >
            <i className="ti ti-x"></i>
          </button>
        </div>

        <div className="privacy-modal-body">
          <div className="privacy-section-item">
            <h4>
              <i className="ti ti-user-check"></i>
              1. Institutional Identity & Account Scoping
            </h4>
            <p>
              DASIGConnect strictly scopes accounts to verified member institutions under the DOST
              Acadême–Science and Innovation Group (Region 7). We collect institutional names, official
              email addresses, and role-based permissions (Contributor, Moderator, Administrator) to
              maintain accountability in all content publishing.
            </p>
          </div>

          <div className="privacy-section-item">
            <h4>
              <i className="ti ti-photo-shield"></i>
              2. Media Assets & Content Ownership
            </h4>
            <p>
              Event photographs, video assets, and post captions submitted through DASIGConnect remain
              the property of the originating Higher Education Institution (HEI). Media is stored
              securely using encrypted cloud object storage (Cloudflare R2) and PostgreSQL Row-Level
              Security (RLS), accessible only by authorized members of your institution and network moderators.
            </p>
          </div>

          <div className="privacy-section-item">
            <h4>
              <i className="ti ti-brand-facebook"></i>
              3. Meta / Facebook Graph API Integration
            </h4>
            <p>
              DASIGConnect interacts directly with the official DOST DASIG Facebook Page via Meta Graph API.
              Posts are only scheduled and published after passing institutional validation and approval.
              Draft and rejected content is never pushed to public channels. We do not access or share personal
              social media data of individual contributors.
            </p>
          </div>

          <div className="privacy-section-item">
            <h4>
              <i className="ti ti-sparkles"></i>
              4. AI-Assisted Features
            </h4>
            <p>
              Our AI drafting tools (caption generation and semantic media recommendations) are purely advisory.
              Media classification is processed through secure, enterprise-tier AI APIs (Anthropic Claude Vision &
              Voyage AI) strictly for generating suggestions. Content is never used to train external public models.
            </p>
          </div>

          <div className="privacy-section-item">
            <h4>
              <i className="ti ti-database-lock"></i>
              5. Audit Logs & System Governance
            </h4>
            <p>
              To ensure transparency and prevent unauthorized publications, DASIGConnect maintains an
              append-only, immutable audit trail of review decisions, schedule updates, and publishing events.
              Users can request account deactivation through their designated institutional Administrator.
            </p>
          </div>
        </div>

        <div className="privacy-modal-footer">
          <button
            type="button"
            className="btn-editorial-primary"
            onClick={onClose}
          >
            <i className="ti ti-check"></i>
            I Understand
          </button>
        </div>
      </div>
    </div>
  );
}
