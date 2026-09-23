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
import GridScan from '../../components/ui/GridScan';
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

      {/* Dark Continuous Section: Regional CTA & Footer with GridScan Background */}
      <div className="landing-dark-zone">
        <div className="landing-dark-zone-gridscan" aria-hidden="true">
          <GridScan
            enableMouseTracking={false}
            sensitivity={0}
            lineThickness={1}
            linesColor="#17284f"
            gridScale={0.08}
            scanColor="#38bdf8"
            scanOpacity={0.4}
            enablePost
            bloomIntensity={0.55}
            chromaticAberration={0.002}
            noiseIntensity={0.01}
            lineJitter={0.08}
            scanGlow={0.6}
            scanSoftness={2}
            scanDirection="pingpong"
            scanDuration={2.2}
            scanDelay={1.5}
            enableWebcam={false}
            showPreview={false}
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
