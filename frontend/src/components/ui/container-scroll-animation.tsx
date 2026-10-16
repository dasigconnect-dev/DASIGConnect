import React, { useRef, useState, useEffect } from 'react';
import { useMotionValue, useSpring, useTransform, motion, type MotionValue } from 'motion/react';
import './container-scroll-animation.css';

interface ContainerScrollProps {
  titleComponent: string | React.ReactNode;
  children: React.ReactNode;
  className?: string;
}

export function ContainerScroll({
  titleComponent,
  children,
  className = '',
}: ContainerScrollProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    const checkMobile = () => {
      setIsMobile(window.innerWidth <= 768);
    };
    checkMobile();
    window.addEventListener('resize', checkMobile);
    return () => window.removeEventListener('resize', checkMobile);
  }, []);

  // Motion value for scroll progress through viewport (0 = entering, 1 = centered/settled)
  const rawProgress = useMotionValue(0);
  const smoothProgress = useSpring(rawProgress, {
    damping: 30,
    stiffness: 140,
    mass: 0.3,
  });

  const calculateProgress = React.useCallback(() => {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    const windowHeight = window.innerHeight || 800;

    // Animates smoothly as the container approaches and settles in the viewing zone
    const startY = windowHeight * 0.95;
    const endY = windowHeight * 0.15;
    const totalDist = startY - endY;
    const currentDist = startY - rect.top;
    const p = Math.min(Math.max(currentDist / totalDist, 0), 1);

    rawProgress.set(p);
  }, [rawProgress]);

  useEffect(() => {
    let ticking = false;

    const handleScroll = () => {
      if (!ticking) {
        window.requestAnimationFrame(() => {
          calculateProgress();
          ticking = false;
        });
        ticking = true;
      }
    };

    // Calculate immediately on mount
    calculateProgress();

    // Multiple deferred ticks to ensure accuracy across browser scroll restoration and asset render
    const t1 = setTimeout(calculateProgress, 60);
    const t2 = setTimeout(calculateProgress, 250);
    const t3 = setTimeout(calculateProgress, 600);

    window.addEventListener('scroll', handleScroll, { passive: true });
    window.addEventListener('resize', handleScroll, { passive: true });

    // Use ResizeObserver to detect layout shifts and image resolution
    let resizeObserver: ResizeObserver | null = null;
    if (containerRef.current && typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(() => {
        calculateProgress();
      });
      resizeObserver.observe(containerRef.current);
    }

    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
      clearTimeout(t3);
      window.removeEventListener('scroll', handleScroll);
      window.removeEventListener('resize', handleScroll);
      resizeObserver?.disconnect();
    };
  }, [calculateProgress]);

  // Subtle 3D un-tilt (10deg to 0deg flat) with center origin so card remains normal and never cut off
  const rotate = useTransform(smoothProgress, [0, 1], [10, 0]);
  const scale = useTransform(
    smoothProgress,
    [0, 1],
    isMobile ? [0.94, 0.98] : [0.96, 1]
  );
  const headerTranslate = useTransform(smoothProgress, [0, 1], [0, -18]);
  const cardTranslate = useTransform(smoothProgress, [0, 1], [20, 0]);

  return (
    <div
      className={`container-scroll-root ${className}`}
      ref={containerRef}
    >
      <div
        className="container-scroll-perspective"
      >
        <Header translate={headerTranslate} titleComponent={titleComponent} />
        <Card rotate={rotate} translate={cardTranslate} scale={scale}>
          {children}
        </Card>
      </div>
    </div>
  );
}

interface HeaderProps {
  translate: MotionValue<number>;
  titleComponent: string | React.ReactNode;
}

export function Header({ translate, titleComponent }: HeaderProps) {
  return (
    <motion.div
      style={{
        translateY: translate,
      }}
      className="container-scroll-header"
    >
      {titleComponent}
    </motion.div>
  );
}

interface CardProps {
  rotate: MotionValue<number>;
  scale: MotionValue<number>;
  translate: MotionValue<number>;
  children: React.ReactNode;
}

export function Card({ rotate, scale, translate, children }: CardProps) {
  return (
    <motion.div
      style={{
        rotateX: rotate,
        scale,
        translateY: translate,
      }}
      className="container-scroll-card"
    >
      {/* Browser / Tablet Header Bar */}
      <div className="container-scroll-browser-bar">
        <div className="browser-dots">
          <span className="dot dot-red" />
          <span className="dot dot-yellow" />
          <span className="dot dot-green" />
        </div>
        <div className="browser-address-pill">
          <i className="ti ti-lock" style={{ fontSize: '0.75rem', color: '#10b981' }} />
          <span>https://dasigconnect.gov.ph/app/workspace</span>
        </div>
        <div className="browser-meta-chip">
          <span className="live-dot" /> Live System
        </div>
      </div>

      <div className="container-scroll-inner">
        {children}
      </div>
    </motion.div>
  );
}

export default ContainerScroll;
