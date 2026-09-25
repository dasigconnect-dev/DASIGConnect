import { useState } from 'react';
import type { User } from '../../types/auth.types';
import LandingNavbar from './components/LandingNavbar';
import LandingHero from './components/LandingHero';
import LandingWhatHowWhy from './components/LandingWhatHowWhy';
import LandingDasigWho from './components/LandingDasigWho';
import LandingDevelopers from './components/LandingDevelopers';
import LandingCta from './components/LandingCta';
import LandingFooter from './components/LandingFooter';
import PrivacyPolicyModal from './components/PrivacyPolicyModal';
import ShapeGrid from '../../components/ui/ShapeGrid';
import '../../styles/landing.css';

interface LandingPageProps {
  user: User | null;
}

export default function LandingPage({ user }: LandingPageProps) {
  const [privacyOpen, setPrivacyOpen] = useState(false);

  return (
    <div className="landing-root">
      {/* Navigation */}
      <LandingNavbar user={user} onOpenPrivacy={() => setPrivacyOpen(true)} />

      {/* Main Sections */}
      <main>
        {/* Section 1: Hero (Crisp White Background with DriftWall Media Stream) */}
        <LandingHero user={user} />

        {/* Section 2: Why & What (System Blue) & How It Works (Inset White Curved Card) */}
        <LandingWhatHowWhy />

        {/* Section 4: Stats & DASIG Who (Light Section) */}
        <LandingDasigWho />

        {/* Section 5: Developers (CIT-U Capstone Team) */}
        <LandingDevelopers />
      </main>

      {/* Harmonious White Theme Section: Regional CTA & Footer with Animated Moving Box Grid */}
      <div className="landing-cta-zone">
        <div className="shapegrid-bg-wrap" aria-hidden="true">
          <ShapeGrid
            speed={0.35}
            squareSize={42}
            direction="diagonal"
            borderColor="rgba(24, 119, 242, 0.09)"
            hoverFillColor="rgba(24, 119, 242, 0.15)"
            shape="square"
            hoverTrailAmount={4}
          />
        </div>

        {/* Section 6: Regional CTA */}
        <LandingCta user={user} onOpenPrivacy={() => setPrivacyOpen(true)} />

        {/* Footer */}
        <LandingFooter onOpenPrivacy={() => setPrivacyOpen(true)} />
      </div>

      {/* Privacy Policy Modal */}
      <PrivacyPolicyModal open={privacyOpen} onClose={() => setPrivacyOpen(false)} />
    </div>
  );
}
