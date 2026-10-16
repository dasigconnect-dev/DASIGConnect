import CircularGallery, { type CircularGalleryItem } from '../../../components/ui/CircularGallery';
import TextType from '../../../components/ui/TextType';
import ShapeGrid from '../../../components/ui/ShapeGrid';

const DASIG_GALLERY_ITEMS: CircularGalleryItem[] = [
  {
    image: '/landing-gallery/dasig-consortium-board-meeting.png',
    text: 'Consortium Board',
    textColor: '#1877f2',
  },
  {
    image: '/landing-gallery/dasig-thrive-research-ventures.jpg',
    text: 'THRIVE Ventures',
    textColor: '#07112a',
  },
  {
    image: '/landing-gallery/dasig-wildcat-innovation-labs.jpg',
    text: 'Wildcat Innovation Labs',
    textColor: '#1877f2',
  },
  {
    image: '/landing-gallery/dasig-tbi-fund-central-visayas.jpg',
    text: 'TBI Fund Central Visayas',
    textColor: '#07112a',
  },
  {
    image: '/landing-gallery/dasig-thrive-consortium-summit.jpg',
    text: 'Regional Summit',
    textColor: '#1877f2',
  },
];

export default function LandingDasigWho() {
  return (
    <section className="who-editorial-section" id="dasig-network" style={{ position: 'relative', overflow: 'hidden' }}>
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
        <div className="who-editorial-grid">
          <div>
            <div className="meta-tagline">
              <span>The Consortium</span>
              <span>&bull;</span>
              <span>Central Visayas</span>
            </div>

            <h2 className="huge-title">
              <TextType
                text={"What is\nDASIG?"}
                typingSpeed={50}
                pauseDuration={3000}
                deletingSpeed={25}
                loop={true}
                startOnVisible={true}
                showCursor={true}
                cursorCharacter="|"
                highlightWords={[{ word: 'DASIG?', color: 'var(--lp-blue, #1877f2)', italic: true }]}
                reserveSpace={true}
              />
            </h2>

            <p className="sparse-lead">
              The <strong>DOST Acad&ecirc;me&ndash;Science and Innovation Group</strong> unites
              universities across Region 7 to share academic breakthroughs, funded research, and
              student inventions with the public through a verified Facebook community.
            </p>
          </div>

          <div className="hei-tags-container">
            <div className="hei-tag-row">
              <i className="ti ti-school"></i>
              <span>Cebu Institute of Technology &ndash; University (CIT-U)</span>
            </div>

            <div className="hei-tag-row">
              <i className="ti ti-school"></i>
              <span>Silliman University</span>
            </div>

            <div className="hei-tag-row">
              <i className="ti ti-school"></i>
              <span>Region 7 State Universities &amp; Colleges (SUCs)</span>
            </div>

            <div className="hei-tag-row">
              <i className="ti ti-building"></i>
              <span>DOST Region 7 Regional Science Center</span>
            </div>
          </div>
        </div>
      </div>

      {/* Full-Bleed Edge-to-Edge Circular WebGL Gallery (No background box, close together, alternating blue & black) */}
      <div className="who-gallery-fullbleed">
        <CircularGallery
          items={DASIG_GALLERY_ITEMS}
          bend={1}
          alternateColors={true}
          borderRadius={0.05}
          scrollEase={0.05}
          padding={0.35}
          fontUrl="https://fonts.googleapis.com/css2?family=Fraunces:ital,wght@0,600;0,700;1,600&display=swap"
          font="700 24px 'Fraunces', Georgia, serif"
          scrollSpeed={2}
        />
      </div>
    </section>
  );
}
