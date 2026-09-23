import { useState, useRef, useEffect } from 'react';
import FolderFloat, { type FolderFloatItem } from '../../../components/ui/FolderFloat';
import ShapeGrid from '../../../components/ui/ShapeGrid';
import TextType from '../../../components/ui/TextType';

const THREE_CORE_CHALLENGES: FolderFloatItem[] = [
  {
    label: 'Delayed and Irregular Content Publishing',
    value: 'delays',
    icon: 'ti ti-clock-pause',
  },
  {
    label: 'Fragmented and Unstructured Content Coordination',
    value: 'coordination',
    icon: 'ti ti-message-2-share',
  },
  {
    label: 'Lack of a Structured Validation and Approval Process',
    value: 'validation',
    icon: 'ti ti-shield-x',
  },
];

const SOLUTION_SLIDES = [
  {
    id: 'submit',
    stepNumber: '01',
    tabLabel: 'Draft & Assist (Submit Content)',
    title: 'Draft & Assist',
    subtitle: 'Centralized Content Submission',
    description: 'Structured multi-institution authoring workspace with embedded guidelines and media proofing.',
    image: '/landing-gallery/dasig-submit-content-screenshot.png?v=20260923',
    alt: 'Draft & Assist — Submit Content Admin View',
  },
  {
    id: 'review',
    stepNumber: '02',
    tabLabel: 'Review & Brand (Validation Queue)',
    title: 'Review & Brand',
    subtitle: 'Administrative Validation Queue',
    description: 'Regional verification pipeline to inspect student drafts, enforce brand compliance, and approve for release.',
    image: '/landing-gallery/dasig-review-queue-screenshot.png?v=20260923',
    alt: 'Review & Brand — Validation Queue Admin View',
  },
];

export default function LandingWhatHowWhy() {
  const [activeSlideIndex, setActiveSlideIndex] = useState(0);
  const [isPaused, setIsPaused] = useState(false);
  const touchStartX = useRef<number | null>(null);

  // Live Auto-play carousel rotation (pauses on hover)
  useEffect(() => {
    if (isPaused) return;
    const timer = setInterval(() => {
      setActiveSlideIndex((prev) => (prev + 1) % SOLUTION_SLIDES.length);
    }, 4200);
    return () => clearInterval(timer);
  }, [isPaused]);

  const prevSlide = () => {
    setActiveSlideIndex((prev) => (prev === 0 ? SOLUTION_SLIDES.length - 1 : prev - 1));
  };

  const nextSlide = () => {
    setActiveSlideIndex((prev) => (prev === SOLUTION_SLIDES.length - 1 ? 0 : prev + 1));
  };

  const handleTouchStart = (e: React.TouchEvent) => {
    touchStartX.current = e.touches[0].clientX;
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (touchStartX.current === null) return;
    const deltaX = e.changedTouches[0].clientX - touchStartX.current;
    if (deltaX > 40) {
      prevSlide();
    } else if (deltaX < -40) {
      nextSlide();
    }
    touchStartX.current = null;
  };
  return (
    <>
      {/* Crisp White Problem Section with Interactive ShapeGrid Canvas */}
      <section className="problem-editorial-section" id="why-and-what">
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
          <div className="split-editorial-grid">
            {/* Left Column: Interactive Physics FolderFloat */}
            <div className="folder-showcase-wrap">
              <div className="folder-float-container">
                <FolderFloat
                  items={THREE_CORE_CHALLENGES}
                  label="Institutional Bottlenecks"
                  sublabel="3 Core Challenges"
                  trigger="hover"
                  defaultOpen={true}
                  closeOnSelect={false}
                  physics={true}
                  drift={0.35}
                  folderColor="#0c1d3d"
                  frontColor="#1877f2"
                  paperColor="#ffffff"
                  itemColor="#ffffff"
                  itemTextColor="#0f172a"
                  labelColor="#ffffff"
                  width={260}
                  height={170}
                  radius={16}
                  spread={280}
                  lift={72}
                  rowGap={80}
                  tilt={5}
                  flapAngle={34}
                  restAngle={16}
                  openDuration={520}
                  stagger={50}
                  bounce={0.25}
                />
              </div>

              <div className="folder-hint-bar">
                <span className="folder-hint-icon">
                  <i className="ti ti-hand-grab"></i>
                </span>
                <span>Hover or drag the floating bottleneck notes</span>
              </div>
            </div>

            {/* Right Column: Editorial Problem Content */}
            <div className="problem-content-col">
              <div className="meta-tagline">
                <span>The Challenge</span>
                <span>&bull;</span>
                <span>Central Visayas HEIs</span>
              </div>

              <h2 className="huge-title">
                <TextType
                  text={"The Problem\nWe Solve."}
                  typingSpeed={50}
                  pauseDuration={3000}
                  deletingSpeed={25}
                  loop={true}
                  startOnVisible={true}
                  showCursor={true}
                  cursorCharacter="|"
                  highlightWords={[{ word: 'We Solve.', color: 'var(--lp-blue, #1877f2)', italic: true }]}
                  reserveSpace={true}
                />
              </h2>

              <p className="sparse-lead" style={{ maxWidth: 540 }}>
                Before DASIGConnect, Higher Education Institutions operated in informational silos
                without a unified pipeline to coordinate, validate, and publish campus achievements.
              </p>

              <div className="problem-badges-row">
                <div className="problem-badge-pill">
                  <i className="ti ti-shield-check" /> 100% Verified Posts
                </div>
                <div className="problem-badge-pill">
                  <i className="ti ti-clock-bolt" /> Real-Time Publishing
                </div>
                <div className="problem-badge-pill">
                  <i className="ti ti-topology-star-3" /> Unified Consortium
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Proposed Solution: Clean Flat Showcase with Interactive ShapeGrid Canvas */}
      <section className="workflow-section-wrap" id="how-it-works" style={{ position: 'relative', overflow: 'hidden' }}>
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
          <div className="solution-scroll-title-wrap">
            <div
              className="meta-tagline"
              style={{
                justifyContent: 'center',
                width: '100%',
                marginBottom: 12,
              }}
            >
              <span>The Solution</span>
              <span>&bull;</span>
              <span>Centralized Editorial Pipeline</span>
            </div>

            <h2
              className="huge-title"
              style={{
                textAlign: 'center',
                fontSize: 'clamp(2.3rem, 4.6vw, 4rem)',
                marginBottom: 14,
              }}
            >
              <TextType
                text="Proposed Solution."
                typingSpeed={50}
                pauseDuration={3000}
                deletingSpeed={25}
                loop={true}
                startOnVisible={true}
                showCursor={true}
                cursorCharacter="|"
                highlightWords={[{ word: 'Solution.', color: 'var(--lp-blue, #1877f2)', italic: true }]}
                reserveSpace={true}
              />
            </h2>

            <p
              className="sparse-lead"
              style={{
                textAlign: 'center',
                maxWidth: 580,
                margin: '0 auto 28px',
              }}
            >
              A unified editorial workflow replacing informal handoffs with structured multi-institution drafting and verified regional review.
            </p>

            {/* Solution Switcher Tabs - Clean horizontal layout without step numbers */}
            <div className="solution-step-tabs" role="tablist" aria-label="Proposed Solutions">
              {SOLUTION_SLIDES.map((slide, idx) => (
                <button
                  key={slide.id}
                  type="button"
                  role="tab"
                  aria-selected={idx === activeSlideIndex}
                  className={`solution-step-tab-btn ${idx === activeSlideIndex ? 'is-active' : ''}`}
                  onClick={() => setActiveSlideIndex(idx)}
                >
                  <span className="step-tab-text">{slide.title}</span>
                  <span className="step-tab-sub">({slide.subtitle})</span>
                </button>
              ))}
            </div>
          </div>

          {/* Flat Clean Browser Mockup Showcase Card (Max-width locked to native 1024px to prevent blur) */}
          <div className="solution-flat-card">
            {/* Carousel Viewport Container */}
            <div
              className="solution-carousel-container"
              onMouseEnter={() => setIsPaused(true)}
              onMouseLeave={() => setIsPaused(false)}
              onTouchStart={handleTouchStart}
              onTouchEnd={handleTouchEnd}
            >
              <div className="solution-carousel-viewport">
                {/* Floating Side Arrow: Prev */}
                <button
                  type="button"
                  className="solution-carousel-arrow is-prev"
                  onClick={prevSlide}
                  aria-label="Previous screenshot"
                  title="Previous Screenshot"
                >
                  <i className="ti ti-chevron-left" />
                </button>

                {/* Floating Side Arrow: Next */}
                <button
                  type="button"
                  className="solution-carousel-arrow is-next"
                  onClick={nextSlide}
                  aria-label="Next screenshot"
                  title="Next Screenshot"
                >
                  <i className="ti ti-chevron-right" />
                </button>

                {/* Horizontal Sliding Track */}
                <div
                  className="solution-carousel-track"
                  style={{
                    transform: `translateX(-${activeSlideIndex * 100}%)`,
                  }}
                >
                  {SOLUTION_SLIDES.map((slide) => (
                    <div className="solution-carousel-slide" key={slide.id}>
                      <img
                        src={slide.image}
                        alt={slide.alt}
                        className="solution-screenshot-img"
                        draggable={false}
                      />
                    </div>
                  ))}
                </div>
              </div>

              {/* Bottom Control & Status Bar */}
              <div className="solution-carousel-bottom-bar">
                <div className="carousel-status-tag">
                  <span className="live-pulse-dot" />
                  <div className="carousel-label-meta">
                    <span className="carousel-step-name">{SOLUTION_SLIDES[activeSlideIndex].title}</span>
                    <span className="carousel-step-divider">&bull;</span>
                    <span className="carousel-step-sub">{SOLUTION_SLIDES[activeSlideIndex].subtitle}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
