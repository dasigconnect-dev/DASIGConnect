import TextType from '../../../components/ui/TextType';
import ShapeGrid from '../../../components/ui/ShapeGrid';
import AccordionGallery, { type AccordionGalleryMember } from '../../../components/ui/AccordionGallery';

const DEVELOPERS: AccordionGalleryMember[] = [
  {
    name: 'Mark Anton L. Camoro',
    role: 'Frontend & UI/UX Engineer',
    initials: 'MC',
    image: '/landing-gallery/team-mark.png',
    tagline: 'Crafting responsive user interfaces, fluid micro-interactions, and accessibility standards.',
    institution: 'CIT-U Computer Studies',
    stats: [
      { label: 'Focus', value: 'Frontend' },
      { label: 'Rating', value: '5.0', isStar: true },
      { label: 'Cohort', value: '2526-S2' },
    ],
    contactLabel: 'Get in Touch',
  },
  {
    name: 'Chris Daniel P. Cabatana',
    role: 'Backend & Systems Architect',
    initials: 'CC',
    image: '/landing-gallery/team-chris.png',
    tagline: 'Architecting secure multi-tenant infrastructure, Facebook Graph API v21.0, and queue systems.',
    institution: 'CIT-U Computer Studies',
    stats: [
      { label: 'Focus', value: 'Backend' },
      { label: 'Rating', value: '5.0', isStar: true },
      { label: 'Cohort', value: '2526-S2' },
    ],
    contactLabel: 'Get in Touch',
  },
  {
    name: 'Jay Lord C. Bayonas',
    role: 'Full-Stack Developer',
    initials: 'JB',
    image: '/landing-gallery/team-jay.png',
    tagline: 'Specializing in end-to-end full-stack features, API integration, and database workflows.',
    institution: 'CIT-U Computer Studies',
    stats: [
      { label: 'Stack', value: 'Full-Stack' },
      { label: 'Rating', value: '4.9', isStar: true },
      { label: 'Cohort', value: '2526-S2' },
    ],
    contactLabel: 'Get in Touch',
  },
  {
    name: 'Lerah A. Caones',
    role: 'QA & Documentation',
    initials: 'LC',
    image: '/landing-gallery/team-lerah.png',
    tagline: 'Ensuring end-to-end software quality, comprehensive test validation, and technical documentation.',
    institution: 'CIT-U Computer Studies',
    stats: [
      { label: 'Focus', value: 'QA / Docs' },
      { label: 'Rating', value: '4.9', isStar: true },
      { label: 'Cohort', value: '2526-S2' },
    ],
    contactLabel: 'Get in Touch',
  },
  {
    name: 'Richemmae V. Bigno',
    role: 'Project Lead',
    initials: 'RB',
    image: '/landing-gallery/team-chim.png',
    tagline: 'Leading team coordination, consortium stakeholder alignment, and product vision.',
    institution: 'CIT-U Computer Studies',
    stats: [
      { label: 'Role', value: 'Lead' },
      { label: 'Rating', value: '5.0', isStar: true },
      { label: 'Cohort', value: '2526-S2' },
    ],
    contactLabel: 'Get in Touch',
  },
];

export default function LandingDevelopers() {
  return (
    <section className="devs-editorial-section" id="developers" style={{ position: 'relative', overflow: 'hidden' }}>
      <div className="shapegrid-bg-wrap">
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

      <div className="landing-container" style={{ position: 'relative', zIndex: 1 }}>
        <div style={{ textAlign: 'center', maxWidth: 760, margin: '0 auto 10px' }}>
          <div className="meta-tagline" style={{ justifyContent: 'center', width: '100%', marginBottom: 12 }}>
            <span>About Us</span>
            <span>&bull;</span>
            <span>CIT-U Capstone</span>
          </div>

          <h2 className="huge-title" style={{ textAlign: 'center' }}>
            <TextType
              text="The Developers."
              typingSpeed={50}
              pauseDuration={3000}
              deletingSpeed={25}
              loop={true}
              startOnVisible={true}
              showCursor={true}
              cursorCharacter="|"
              highlightWords={[{ word: 'Developers.', color: 'var(--lp-blue, #1877f2)', italic: true }]}
              reserveSpace={true}
            />
          </h2>

          <p className="sparse-lead" style={{ margin: '0 auto 28px', textAlign: 'center' }}>
            Built with pride for the DOST Acad&ecirc;me&ndash;Science and Innovation Group
            by students of the College of Computer Studies (IT332 &bull; Team 2526-sem2-it332-38).
          </p>
        </div>

        {/* Interactive Accordion Profile Cards (Tuned for portrait mobile photography) */}
        <AccordionGallery
          items={DEVELOPERS}
          defaultIndex={3}
          height={550}
          gap={16}
          radius={26}
          expandRatio={0.38}
          trigger="hover"
        />
      </div>
    </section>
  );
}
