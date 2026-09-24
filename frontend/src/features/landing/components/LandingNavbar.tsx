import { Link } from 'react-router-dom';
import type { User } from '../../../types/auth.types';
import dasigLogo from '../../../assets/dasigconnect-logo.png';

interface LandingNavbarProps {
  user: User | null;
  onOpenPrivacy: () => void;
}

export default function LandingNavbar({ user, onOpenPrivacy }: LandingNavbarProps) {
  const scrollToSection = (id: string) => {
    const el = document.getElementById(id);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  };

  return (
    <header className="landing-header">
      <div className="landing-container">
        <div className="landing-nav-inner">
          <Link to="/" className="landing-brand">
            <div className="landing-brand-logo">
              <img src={dasigLogo} alt="DASIGConnect Logo" />
            </div>
            <div className="landing-brand-title">
              DASIG<em>Connect</em>
            </div>
          </Link>

          <nav>
            <ul className="landing-nav-links">
              <li>
                <button
                  type="button"
                  className="landing-nav-link"
                  onClick={() => scrollToSection('why-and-what')}
                >
                  Why & What
                </button>
              </li>
              <li>
                <button
                  type="button"
                  className="landing-nav-link"
                  onClick={() => scrollToSection('how-it-works')}
                >
                  How It Works
                </button>
              </li>
              <li>
                <button
                  type="button"
                  className="landing-nav-link"
                  onClick={() => scrollToSection('dasig-network')}
                >
                  DASIG Network
                </button>
              </li>
              <li>
                <button
                  type="button"
                  className="landing-nav-link"
                  onClick={() => scrollToSection('developers')}
                >
                  Developers
                </button>
              </li>
              <li>
                <button
                  type="button"
                  className="landing-nav-link"
                  onClick={onOpenPrivacy}
                >
                  Privacy
                </button>
              </li>
            </ul>
          </nav>

          <div className="landing-nav-actions">
            {user ? (
              <Link to="/dashboard" className="btn-editorial-primary">
                <i className="ti ti-layout-dashboard"></i>
                Go to Dashboard
              </Link>
            ) : (
              <Link to="/login" className="btn-editorial-primary">
                <i className="ti ti-login"></i>
                Sign In
              </Link>
            )}
          </div>
        </div>
      </div>
    </header>
  );
}
