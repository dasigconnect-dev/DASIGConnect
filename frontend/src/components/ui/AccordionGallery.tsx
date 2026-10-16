import React, { useRef, useEffect, useState, useCallback } from 'react';
import { gsap } from 'gsap';
import './AccordionGallery.css';

export interface AccordionGalleryMember {
  image: string;
  name: string;
  role: string;
  initials: string;
  tagline?: string;
  institution?: string;
  stats?: Array<{ label: string; value: string; isStar?: boolean }>;
  contactLabel?: string;
  githubUrl?: string;
  linkedinUrl?: string;
}

export interface AccordionGalleryProps {
  items: AccordionGalleryMember[];
  defaultIndex?: number;
  height?: number;
  gap?: number;
  radius?: number;
  expandRatio?: number;
  orientation?: 'horizontal' | 'vertical';
  duration?: number;
  ease?: string;
  parallax?: number;
  tilt?: number;
  stagger?: number;
  trigger?: 'hover' | 'click';
  className?: string;
}

export const AccordionGallery: React.FC<AccordionGalleryProps> = ({
  items,
  defaultIndex = 2,
  height = 540,
  gap = 14,
  radius = 24,
  expandRatio = 0.48,
  orientation = 'horizontal',
  duration = 0.6,
  ease = 'power3.out',
  parallax = 0.5,
  tilt = 6,
  trigger = 'hover',
  className = '',
}) => {
  const rootRef = useRef<HTMLDivElement>(null);
  const panelRefs = useRef<(HTMLDivElement | null)[]>([]);
  const mediaRefs = useRef<(HTMLSpanElement | null)[]>([]);
  const expandedRefs = useRef<(HTMLDivElement | null)[]>([]);
  const collapsedRefs = useRef<(HTMLDivElement | null)[]>([]);
  const tlRef = useRef<gsap.core.Timeline | null>(null);
  const firstRunRef = useRef(true);
  const mediaSizeRef = useRef(380);

  const vertical = orientation === 'vertical';
  const count = items.length;
  const [active, setActive] = useState(Math.min(Math.max(defaultIndex, 0), count - 1));

  const prefersReduced =
    typeof window !== 'undefined' && window.matchMedia
      ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
      : false;

  const applyLayout = useCallback(
    (animate: boolean) => {
      const panels = panelRefs.current;
      if (!panels.length) return;

      const r = Math.min(Math.max(expandRatio, 0.2), 0.9);
      const grow = count > 1 ? (r * (count - 1)) / (1 - r) : 1;
      const mediaSize = mediaSizeRef.current;

      tlRef.current?.kill();
      const dur = animate && !prefersReduced ? duration : 0;
      const tl = gsap.timeline();

      panels.forEach((panel, i) => {
        if (!panel) return;
        const isActive = i === active;
        const media = mediaRefs.current[i];
        const exp = expandedRefs.current[i];
        const col = collapsedRefs.current[i];

        const rot = isActive ? 0 : i < active ? tilt : -tilt;
        const rotProp = vertical ? { rotateX: -rot } : { rotateY: rot };

        tl.to(panel, { flexGrow: isActive ? grow : 1, ...rotProp, duration: dur, ease }, 0);

        if (media) {
          const drift = Math.max(-1.5, Math.min(1.5, active - i));
          const shift = drift * parallax * mediaSize * 0.06;
          tl.to(
            media,
            {
              xPercent: -50,
              yPercent: -50,
              x: vertical ? 0 : isActive ? 0 : shift,
              y: vertical ? (isActive ? 0 : shift) : 0,
              duration: dur,
              ease,
            },
            0
          );
        }

        if (exp && col) {
          if (isActive) {
            tl.to(exp, { opacity: 1, y: 0, display: 'flex', duration: dur, ease }, 0);
            tl.to(col, { opacity: 0, y: 8, display: 'none', duration: dur * 0.4, ease }, 0);
          } else {
            tl.to(exp, { opacity: 0, y: 12, display: 'none', duration: dur * 0.4, ease }, 0);
            tl.to(col, { opacity: 1, y: 0, display: 'flex', duration: dur, ease }, 0);
          }
        }
      });

      tlRef.current = tl;
    },
    [active, count, expandRatio, duration, ease, vertical, tilt, parallax, prefersReduced]
  );

  useEffect(() => {
    const el = rootRef.current;
    if (!el) return;

    const measure = () => {
      const rect = el.getBoundingClientRect();
      const total = vertical ? rect.height : rect.width;
      const usable = Math.max(total - gap * (count - 1), 120);
      const size = Math.max(160, usable * Math.min(Math.max(expandRatio, 0.2), 0.9) * 1.25);
      mediaSizeRef.current = size;
      el.style.setProperty('--ag-media-size', `${size}px`);
      applyLayout(!firstRunRef.current);
    };

    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, [applyLayout, gap, count, expandRatio, vertical]);

  useEffect(() => {
    applyLayout(!firstRunRef.current);
    firstRunRef.current = false;
  }, [applyLayout]);

  const [isMobile, setIsMobile] = useState<boolean>(() =>
    typeof window !== 'undefined' ? window.innerWidth <= 768 : false
  );
  const mobileTrackRef = useRef<HTMLDivElement>(null);
  const [mobileActiveIndex, setMobileActiveIndex] = useState(defaultIndex);

  useEffect(() => {
    const handleResize = () => {
      setIsMobile(window.innerWidth <= 768);
    };
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  const handleMobileScroll = () => {
    const el = mobileTrackRef.current;
    if (!el) return;
    const cardWidth = el.offsetWidth * 0.85;
    const scrollPos = el.scrollLeft;
    const newIdx = Math.round(scrollPos / cardWidth);
    if (newIdx >= 0 && newIdx < count && newIdx !== mobileActiveIndex) {
      setMobileActiveIndex(newIdx);
    }
  };

  const scrollToMobileCard = (idx: number) => {
    const el = mobileTrackRef.current;
    if (!el) return;
    const targetCard = el.children[idx] as HTMLElement;
    if (targetCard) {
      targetCard.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
      setMobileActiveIndex(idx);
    }
  };

  useEffect(() => () => {
    tlRef.current?.kill();
  }, []);

  const handleEnter = (i: number) => {
    if (trigger === 'hover') setActive(i);
  };

  const handleClick = (i: number, e: React.MouseEvent) => {
    if (i !== active) {
      e.preventDefault();
      setActive(i);
    }
  };

  const handleKeyDown = (i: number, e: React.KeyboardEvent) => {
    if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
      e.preventDefault();
      setActive((i + 1) % count);
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((i - 1 + count) % count);
    }
  };

  if (isMobile) {
    return (
      <div className={`ag-mobile-gallery-wrapper ${className}`}>
        <div
          ref={mobileTrackRef}
          className="ag-mobile-track"
          onScroll={handleMobileScroll}
          role="list"
          aria-label="Developers carousel"
        >
          {items.map((item) => (
            <div className="ag-mobile-card" key={item.name} role="listitem">
              <div className="ag-mobile-media-frame">
                <img
                  src={item.image}
                  alt={item.name}
                  className="ag-mobile-card-img"
                  draggable={false}
                />
                <div className="ag-mobile-card-overlay" aria-hidden="true" />
              </div>

              <div className="ag-mobile-card-content">
                <div className="ag-member-header">
                  <div className="ag-member-badge">{item.initials}</div>
                  <div className="ag-member-meta">
                    <div className="ag-member-name-row">
                      <span className="ag-member-name">{item.name}</span>
                      <i className="ti ti-circle-check-filled ag-verify-check" title="Verified Contributor" />
                    </div>
                    <span className="ag-member-role">{item.role}</span>
                  </div>
                </div>

                {item.tagline && <p className="ag-member-bio">{item.tagline}</p>}

                {item.stats && item.stats.length > 0 && (
                  <div className="ag-member-stats">
                    {item.stats.map((s) => (
                      <div className="ag-stat-item" key={s.label}>
                        <span className="ag-stat-val">
                          {s.isStar && <i className="ti ti-star-filled" />}
                          {s.value}
                        </span>
                        <span className="ag-stat-lbl">{s.label}</span>
                      </div>
                    ))}
                  </div>
                )}

                <div className="ag-member-actions">
                  <span className="ag-btn-contact">
                    <i className="ti ti-mail" />
                    {item.contactLabel || 'Connect'}
                  </span>
                  <span className="ag-btn-icon" title={item.institution || 'CIT-U'}>
                    <i className="ti ti-school" />
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Mobile Indicator Dots & Nav Buttons */}
        <div className="ag-mobile-controls">
          <button
            type="button"
            className="ag-mobile-nav-arrow"
            onClick={() => scrollToMobileCard(Math.max(0, mobileActiveIndex - 1))}
            disabled={mobileActiveIndex === 0}
            aria-label="Previous developer"
          >
            <i className="ti ti-chevron-left" />
          </button>

          <div className="ag-mobile-dots" role="tablist" aria-label="Developer cards pagination">
            {items.map((item, idx) => (
              <button
                key={item.name}
                type="button"
                className={`ag-mobile-dot ${idx === mobileActiveIndex ? 'is-active' : ''}`}
                onClick={() => scrollToMobileCard(idx)}
                aria-label={`Jump to ${item.name}`}
              />
            ))}
          </div>

          <button
            type="button"
            className="ag-mobile-nav-arrow"
            onClick={() => scrollToMobileCard(Math.min(count - 1, mobileActiveIndex + 1))}
            disabled={mobileActiveIndex === count - 1}
            aria-label="Next developer"
          >
            <i className="ti ti-chevron-right" />
          </button>
        </div>
      </div>
    );
  }

  return (
    <div
      ref={rootRef}
      className={`accordion-gallery${vertical ? ' accordion-gallery--vertical' : ''}${className ? ` ${className}` : ''}`}
      style={{
        '--ag-gap': `${gap}px`,
        '--ag-radius': `${radius}px`,
        height: vertical ? `${Math.round(height * 1.6)}px` : `${height}px`,
      } as React.CSSProperties}
      role="list"
      aria-label="Developers gallery"
    >
      {items.map((item, i) => {
        const isActive = i === active;
        return (
          <div
            key={item.name}
            ref={(el) => { panelRefs.current[i] = el; }}
            className={`ag-panel${isActive ? ' ag-panel--active' : ''}`}
            onClick={(e) => handleClick(i, e)}
            onMouseEnter={() => handleEnter(i)}
            onFocus={() => setActive(i)}
            onKeyDown={(e) => handleKeyDown(i, e)}
            role="listitem"
            tabIndex={0}
            aria-current={isActive ? 'true' : undefined}
            aria-label={item.name}
          >
            <span className="ag-panel__frame">
              <span className="ag-panel__media" ref={(el) => { mediaRefs.current[i] = el; }}>
                <img src={item.image} alt={item.name} draggable="false" />
              </span>
              <span className="ag-panel__overlay" aria-hidden="true" />
            </span>

            {/* Bottom Card Details Container */}
            <div className="ag-panel__details">
              {/* Collapsed View: Compact Pill */}
              <div
                ref={(el) => { collapsedRefs.current[i] = el; }}
                className="ag-collapsed-badge"
              >
                <span className="ag-collapsed-initials">{item.initials}</span>
                <span className="ag-collapsed-name">{item.name.split(' ')[0]}</span>
              </div>

              {/* Expanded View: Full Profile Card */}
              <div
                ref={(el) => { expandedRefs.current[i] = el; }}
                className="ag-expanded-card"
              >
                <div className="ag-member-header">
                  <div className="ag-member-badge">{item.initials}</div>
                  <div className="ag-member-meta">
                    <div className="ag-member-name-row">
                      <span className="ag-member-name">{item.name}</span>
                      <i className="ti ti-circle-check-filled ag-verify-check" title="Verified Contributor" />
                    </div>
                    <span className="ag-member-role">{item.role}</span>
                  </div>
                </div>

                {item.tagline && <p className="ag-member-bio">{item.tagline}</p>}

                {item.stats && item.stats.length > 0 && (
                  <div className="ag-member-stats">
                    {item.stats.map((s) => (
                      <div className="ag-stat-item" key={s.label}>
                        <span className="ag-stat-val">
                          {s.isStar && <i className="ti ti-star-filled" />}
                          {s.value}
                        </span>
                        <span className="ag-stat-lbl">{s.label}</span>
                      </div>
                    ))}
                  </div>
                )}

                <div className="ag-member-actions">
                  <span className="ag-btn-contact">
                    <i className="ti ti-mail" />
                    {item.contactLabel || 'Connect'}
                  </span>
                  <span className="ag-btn-icon" title="Institution">
                    <i className="ti ti-school" />
                  </span>
                </div>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
};

export default AccordionGallery;
