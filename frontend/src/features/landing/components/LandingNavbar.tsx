import { useState, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import type { User } from '../../../types/auth.types';
import dasigLogo from '../../../assets/dasigconnect-logo.png';

interface LandingNavbarProps {
  user: User | null;
  onOpenPrivacy: () => void;
}

export default function LandingNavbar({ user, onOpenPrivacy }: LandingNavbarProps) {
  const [activeSection, setActiveSection] = useState<string>('');
  const isProgrammaticScrollRef = useRef(false);
  const scrollEndTimerRef = useRef<number | null>(null);

  const scrollToSection = (id: string) => {
    setActiveSection(id);
    const el = document.getElementById(id);
    if (!el) return;

    // Lock scroll spy to prevent flickering across intermediate buttons
    isProgrammaticScrollRef.current = true;
    if (scrollEndTimerRef.current) {
      window.clearTimeout(scrollEndTimerRef.current);
    }

    const headerOffset = 108;
    const elementPosition = el.getBoundingClientRect().top;
    const offsetPosition = elementPosition + window.pageYOffset - headerOffset;

    window.scrollTo({
      top: Math.max(0, offsetPosition),
      behavior: 'smooth',
    });

    scrollEndTimerRef.current = window.setTimeout(() => {
      isProgrammaticScrollRef.current = false;
    }, 850);
  };

  useEffect(() => {
    const sectionIds = ['why-and-what', 'how-it-works', 'dasig-network', 'developers'];
    let ticking = false;

    const handleScroll = () => {
      if (isProgrammaticScrollRef.current) return;

      if (!ticking) {
        window.requestAnimationFrame(() => {
          const scrollY = window.scrollY;
          if (scrollY < 200) {
            setActiveSection('');
            ticking = false;
            return;
          }

          let current = '';
          for (const id of sectionIds) {
            const el = document.getElementById(id);
            if (el) {
              const rect = el.getBoundingClientRect();
              if (rect.top <= 190 && rect.bottom > 140) {
                current = id;
                break;
              }
            }
          }

          if (current) {
            setActiveSection(current);
          }
          ticking = false;
        });
        ticking = true;
      }
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    handleScroll();

    return () => {
      window.removeEventListener('scroll', handleScroll);
      if (scrollEndTimerRef.current) {
        window.clearTimeout(scrollEndTimerRef.current);
      }
    };
  }, []);

  return (
    <header className="landing-header">
      <div className="landing-nav-inner">
        <Link
          to="/"
          className="landing-brand"
          onClick={(e) => {
            e.preventDefault();
            setActiveSection('');
            window.scrollTo({ top: 0, behavior: 'smooth' });
          }}
          title="DASIGConnect — Home"
        >
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
                className={`landing-nav-link ${activeSection === 'why-and-what' ? 'is-active' : ''}`}
                onClick={() => scrollToSection('why-and-what')}
              >
                Why & What
              </button>
            </li>
            <li>
              <button
                type="button"
                className={`landing-nav-link ${activeSection === 'how-it-works' ? 'is-active' : ''}`}
                onClick={() => scrollToSection('how-it-works')}
              >
                How It Works
              </button>
            </li>
            <li>
              <button
                type="button"
                className={`landing-nav-link ${activeSection === 'dasig-network' ? 'is-active' : ''}`}
                onClick={() => scrollToSection('dasig-network')}
              >
                DASIG Network
              </button>
            </li>
            <li>
              <button
                type="button"
                className={`landing-nav-link ${activeSection === 'developers' ? 'is-active' : ''}`}
                onClick={() => scrollToSection('developers')}
              >
                Developers
              </button>
            </li>
            <li>
              <button
                type="button"
                className={`landing-nav-link ${activeSection === 'privacy' ? 'is-active' : ''}`}
                onClick={() => {
                  setActiveSection('privacy');
                  onOpenPrivacy();
                }}
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
    </header>
  );
}
