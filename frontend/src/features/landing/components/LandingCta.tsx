import { Link } from 'react-router-dom';
import type { User } from '../../../types/auth.types';

interface LandingCtaProps {
  user: User | null;
  onOpenPrivacy: () => void;
}

export default function LandingCta({ user, onOpenPrivacy }: LandingCtaProps) {
  return (
    <section className="cta-editorial-section">
      <div className="landing-container">
        <div className="cta-editorial-banner">
          <div className="meta-tagline" style={{ justifyContent: 'center', marginBottom: 14 }}>
            <span>Workspace</span>
            <span>&bull;</span>
            <span>Access Portal</span>
          </div>

          <h2>
            Ready to synchronize your<br />
            institution&rsquo;s <em>voice?</em>
          </h2>

          <p>
            Whether drafting campus highlights or validating schedules,
            DASIGConnect brings your institution into sync.
          </p>

          <div className="cta-buttons-row">
            {user ? (
              <Link
                to="/dashboard"
                className="btn-editorial-primary"
                style={{ padding: '12px 28px', fontSize: '0.98rem' }}
              >
                <i className="ti ti-layout-dashboard"></i>
                Open Dashboard Workspace
              </Link>
            ) : (
              <Link
                to="/login"
                className="btn-editorial-primary"
                style={{ padding: '12px 28px', fontSize: '0.98rem' }}
              >
                <i className="ti ti-login"></i>
                Access Portal
              </Link>
            )}

            <button
              type="button"
              className="btn-editorial-secondary"
              onClick={onOpenPrivacy}
              style={{ padding: '12px 22px', fontSize: '0.95rem' }}
            >
              <i className="ti ti-shield-lock"></i>
              Privacy Policy
            </button>
          </div>
        </div>
      </div>
    </section>
  );
}
