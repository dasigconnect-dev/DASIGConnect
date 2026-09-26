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
    id: 'scheduling',
    stepNumber: '01',
    tabLabel: 'Smart Scheduling',
    title: 'Smart Scheduling',
    subtitle: 'Automated Slot Allocation',
    caption: 'Coordinated calendar scheduling with automated guardrails preventing overlapping posts across member institutions.',
    howItWorks: 'Contributors select verified regional timeslots, attach organized media albums, and reserve publishing windows with zero coordination friction.',
    image: '/landing-gallery/dasig-solution-scheduling.png',
    alt: 'DASIGConnect Content Scheduling and Publishing Slot Allocation',
  },
  {
    id: 'preview',
    stepNumber: '02',
    tabLabel: 'Feed Preview',
    title: 'Feed Preview',
    subtitle: 'Social Pre-Flight & Readiness',
    caption: 'Real-time Facebook feed simulation that audits post formatting, hashtags, and media compliance before submission.',
    howItWorks: 'An intelligent 7-point readiness check inspects caption length, event dates, and aspect ratios to guarantee publish-ready quality.',
    image: '/landing-gallery/dasig-solution-preview.png',
    alt: 'DASIGConnect Facebook Feed Preview and Pre-Flight Readiness',
  },
  {
    id: 'review',
    stepNumber: '03',
    tabLabel: 'Review Queue',
    title: 'Review Queue',
    subtitle: 'Regional Validation & Branding',
    caption: 'Centralized editorial clearance queue where regional administrators inspect, brand, approve, or return submissions.',
    howItWorks: 'Moderators secure exclusive review locks, verify institution credentials, apply official watermarks, and authorize live Facebook publication.',
    image: '/landing-gallery/dasig-solution-review.png',
    alt: 'DASIGConnect Regional Review Queue and Approval Pipeline',
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
              A synchronized regional editorial pipeline — structured scheduling, real-time social pre-flight previews, and centralized multi-institution validation.
            </p>

            {/* Solution Switcher Tabs */}
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
                  <span className="step-tab-text">{slide.tabLabel}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Flat Clean Browser Mockup Showcase Card */}
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
                  aria-label="Previous solution screenshot"
                  title="Previous Screenshot"
                >
                  <i className="ti ti-chevron-left" />
                </button>

                {/* Floating Side Arrow: Next */}
                <button
                  type="button"
                  className="solution-carousel-arrow is-next"
                  onClick={nextSlide}
                  aria-label="Next solution screenshot"
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

              {/* Bottom Control & Detailed Caption Bar */}
              <div className="solution-carousel-bottom-bar">
                <div className="solution-caption-block">
                  <div className="solution-caption-head">
                    <span className="carousel-step-name">{SOLUTION_SLIDES[activeSlideIndex].title}</span>
                  </div>

                  <div className="solution-how-phrase">
                    <span className="how-badge">
                      <i className="ti ti-bolt" /> How it works:
                    </span>
                    <span className="how-text">{SOLUTION_SLIDES[activeSlideIndex].howItWorks}</span>
                  </div>
                </div>

                <div className="solution-carousel-dots" role="tablist" aria-label="Slide indicators">
                  {SOLUTION_SLIDES.map((slide, idx) => (
                    <button
                      key={slide.id}
                      type="button"
                      className={`solution-dot ${idx === activeSlideIndex ? 'is-active' : ''}`}
                      onClick={() => setActiveSlideIndex(idx)}
                      aria-label={`Switch to slide: ${slide.title}`}
                    />
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
