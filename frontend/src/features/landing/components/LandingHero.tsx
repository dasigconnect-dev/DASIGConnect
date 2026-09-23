import { Link } from 'react-router-dom';
import type { User } from '../../../types/auth.types';
import DriftWall, { type DriftWallItem } from '../../../components/ui/DriftWall';
import TextType from '../../../components/ui/TextType';

interface LandingHeroProps {
  user: User | null;
}

const HERO_MEDIA_ITEMS: DriftWallItem[] = [
  {
    image: '/landing-gallery/dasig-nia-innovation.jpg',
    title: 'National Innovation Agency (NIA) Thailand',
  },
  {
    image: '/landing-gallery/dasig-ted-fund.jpg',
    title: 'TED Fund Technology & Enterprise Summit',
  },
  {
    image: '/landing-gallery/dasig-bootstart-cebu.jpg',
    title: 'Bootstart Cebu Visayas Leg Incubation',
  },
  {
    image: '/landing-gallery/dasig-number-one-delegates.jpg',
    title: 'Central Visayas Innovation Hub Delegation',
  },
  {
    image: '/landing-gallery/dasig-digitech-thailand.jpg',
    title: 'DigiTech ASEAN & AI Connect 2025',
  },
  {
    image: '/landing-gallery/dasig-bootcamp-visayas-presentation.png',
    title: 'Startup Grant Fund Program Bootcamp Visayas',
  },
  {
    image: '/landing-gallery/dasig-rstw-awarding.png',
    title: 'RSTW Regional Science & Technology Awarding',
  },
  {
    image: '/landing-gallery/dasig-hybrid-consortium-meeting.jpg',
    title: 'DASIG Central Visayas Hybrid Consortium Session',
  },
  {
    image: '/landing-gallery/dasig-delegation-kx.jpg',
    title: 'KX Startup & Innovation Ecosystem',
  },
  {
    image: '/landing-gallery/dasig-dost-philippine-flag-group.jpg',
    title: 'DOST Region 7 Academic Consortium Delegation',
  },
  {
    image: '/landing-gallery/dasig-pcieerd-innovation-assembly.jpg',
    title: 'DOST-PCIEERD 15 Years of Innovation Assembly',
  },
  {
    image: '/landing-gallery/dasig-ai-connect-convention.jpg',
    title: 'The Future of AI International Forum',
  },
  {
    image: '/landing-gallery/dasig-keynote-presentation.jpg',
    title: 'DASIG Regional Program Keynote',
  },
  {
    image: '/landing-gallery/dasig-consortium-meeting.jpg',
    title: 'DASIG Central Visayas Strategic Planning',
  },
  {
    image: '/landing-gallery/dasig-delegates-hall.png',
    title: 'Central Visayas Consortium Delegation',
  },
  {
    image: '/landing-gallery/dasig-ted-fund.jpg',
    title: 'Regional Enterprise Incubation Exchange',
  },
  {
    image: '/landing-gallery/dasig-bootcamp-visayas-presentation.png',
    title: 'DOST Startup Capacity Building Workshop',
  },
  {
    image: '/landing-gallery/dasig-dost-philippine-flag-group.jpg',
    title: 'Consortium Leaders Strategic Assembly',
  },
  {
    image: '/landing-gallery/dasig-number-one-delegates.jpg',
    title: 'Academic Technology Commercialization',
  },
  {
    image: '/landing-gallery/dasig-hybrid-consortium-meeting.jpg',
    title: 'Inter-HEI Digital Alignment Session',
  },
];

export default function LandingHero({ user }: LandingHeroProps) {
  const scrollToSection = (id: string) => {
    const el = document.getElementById(id);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  };

  return (
    <section className="hero-editorial-section">
      <div className="hero-split-screen">
        {/* Left Side: Context */}
        <div className="hero-content-col">
          <div className="meta-tagline">
            <span>DASIGConnect</span>
            <span>&bull;</span>
            <span>DOST Region 7</span>
            <span>&bull;</span>
            <span>2026</span>
          </div>

          <h1 className="huge-title">
            <TextType
              text={"One platform.\nEvery institution.\nOne Facebook page."}
              typingSpeed={50}
              pauseDuration={3000}
              deletingSpeed={25}
              loop={true}
              showCursor={true}
              cursorCharacter="|"
              highlightWords={[{ word: 'institution.', color: 'var(--lp-blue, #1877f2)', italic: true }]}
              reserveSpace={true}
            />
          </h1>

          <p className="sparse-lead">
            A centralized digital workflow connecting Higher Education Institutions across
            Central Visayas to coordinate, validate, and automatically publish verified event
            content to the official DASIG Facebook Page.
          </p>

          <div className="hero-actions-row">
            {user ? (
              <Link to="/dashboard" className="btn-editorial-primary" style={{ padding: '12px 26px', fontSize: '0.98rem' }}>
                <i className="ti ti-layout-dashboard"></i>
                Open Dashboard
              </Link>
            ) : (
              <Link to="/login" className="btn-editorial-primary" style={{ padding: '12px 26px', fontSize: '0.98rem' }}>
                <i className="ti ti-login"></i>
                Access Portal
              </Link>
            )}

            <button
              type="button"
              className="btn-editorial-secondary"
              onClick={() => scrollToSection('why-and-what')}
              style={{ padding: '12px 22px', fontSize: '0.95rem' }}
            >
              <i className="ti ti-arrow-down"></i>
              Learn More
            </button>
          </div>

          {/* Compact Pillars Row */}
          <div className="hero-mini-pillars">
            <div className="hero-mini-pill">
              <span className="mini-pill-num">01</span>
              <div className="mini-pill-info">
                <strong className="mini-pill-title">Draft &amp; Assist</strong>
                <span className="mini-pill-desc">Multi-HEI submissions</span>
              </div>
            </div>
            <div className="hero-mini-pill">
              <span className="mini-pill-num">02</span>
              <div className="mini-pill-info">
                <strong className="mini-pill-title">Review &amp; Brand</strong>
                <span className="mini-pill-desc">Moderator validation</span>
              </div>
            </div>
            <div className="hero-mini-pill">
              <span className="mini-pill-num">03</span>
              <div className="mini-pill-info">
                <strong className="mini-pill-title">Auto-Publish</strong>
                <span className="mini-pill-desc">Meta Graph API v21.0</span>
              </div>
            </div>
          </div>
        </div>

        {/* Right Side: DriftWall 3D React Bit Component (Transparent Floating Stream) */}
        <div className="hero-driftwall-container">
          <div className="hero-driftwall-frame">
            <DriftWall
              items={HERO_MEDIA_ITEMS}
              columns={4}
              tileWidth={205}
              tileHeight={140}
              gap={18}
              tilt={14}
              turn={-12}
              perspective={1200}
              depth={95}
              speed={28}
              direction="up"
              variance={0.42}
              parallax={0.55}
              lift={50}
              fade={0.65}
              dim={0.92}
              overlayColor="transparent"
              radius={14}
              pauseOnHover={false}
              grayscale={false}
            />
          </div>
        </div>
      </div>
    </section>
  );
}
