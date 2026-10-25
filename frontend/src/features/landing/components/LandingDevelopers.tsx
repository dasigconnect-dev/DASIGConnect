import TextType from '../../../components/ui/TextType';
import ShapeGrid from '../../../components/ui/ShapeGrid';
import AccordionGallery, { type AccordionGalleryMember } from '../../../components/ui/AccordionGallery';

const DEVELOPERS: AccordionGalleryMember[] = [
  {
    name: 'Mark Anton L. Camoro',
    email: 'markantoncamoro@gmail.com',
    role: 'Frontend Developer',
    initials: 'MC',
    image: '/landing-gallery/team-mark.jpg',
    tagline: 'Crafting responsive user interfaces, fluid micro-interactions, and accessible web experiences.',
    institution: 'CIT-U Computer Studies',
    contactLabel: 'Get in Touch',
  },
  {
    name: 'Chris Daniel P. Cabatana',
    email: 'chrisdanielcabatana@gmail.com',
    role: 'Full Stack Developer',
    initials: 'CC',
    image: '/landing-gallery/team-chris.jpg',
    tagline: 'Developing scalable full-stack features, multi-tenant backend architecture, and AI-driven services.',
    institution: 'CIT-U Computer Studies',
    contactLabel: 'Get in Touch',
  },
  {
    name: 'Jay Lord C. Bayonas',
    email: 'jeerold1122@gmail.com',
    role: 'Frontend Developer',
    initials: 'JB',
    image: '/landing-gallery/team-jay.png',
    tagline: 'Building intuitive user interfaces, engaging interactive workflows, and responsive component systems.',
    institution: 'CIT-U Computer Studies',
    contactLabel: 'Get in Touch',
  },
  {
    name: 'Lerah A. Caones',
    email: 'lerahcaones18@gmail.com',
    role: 'Full Stack Developer',
    initials: 'LC',
    image: '/landing-gallery/team-lerah.jpg',
    tagline: 'Implementing robust full-stack capabilities, seamless API workflows, and end-to-end feature reliability.',
    institution: 'CIT-U Computer Studies',
    contactLabel: 'Get in Touch',
  },
  {
    name: 'Richemmae V. Bigno',
    email: 'richemmaeb@gmail.com',
    role: 'Project Lead / Full Stack Developer',
    initials: 'RB',
    image: '/landing-gallery/team-chim.jpg',
    tagline: 'Directing project architecture, full-stack platform development, and consortium stakeholder alignment.',
    institution: 'CIT-U Computer Studies',
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
            by students of the CIT-U College of Computer Studies.
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
