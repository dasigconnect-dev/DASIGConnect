import React, { useEffect, useRef, useState, createElement, useMemo, useCallback } from 'react';
import './TextType.css';

export interface HighlightWord {
  word: string;
  color?: string;
  italic?: boolean;
}

export interface TextTypeProps extends React.HTMLAttributes<HTMLElement> {
  text?: string | string[];
  texts?: string[];
  as?: React.ElementType;
  typingSpeed?: number;
  initialDelay?: number;
  pauseDuration?: number;
  deletingSpeed?: number;
  loop?: boolean;
  className?: string;
  showCursor?: boolean;
  hideCursorWhileTyping?: boolean;
  cursorCharacter?: string;
  cursorClassName?: string;
  cursorBlinkDuration?: number;
  textColors?: string[];
  variableSpeed?: { min: number; max: number };
  variableSpeedEnabled?: boolean;
  variableSpeedMin?: number;
  variableSpeedMax?: number;
  onSentenceComplete?: (sentence: string, index: number) => void;
  startOnVisible?: boolean;
  reverseMode?: boolean;
  highlightWords?: HighlightWord[];
  reserveSpace?: boolean;
}

export const TextType: React.FC<TextTypeProps> = ({
  text,
  texts,
  as: Component = 'div',
  typingSpeed = 50,
  initialDelay = 0,
  pauseDuration = 2000,
  deletingSpeed = 30,
  loop = true,
  className = '',
  showCursor = true,
  hideCursorWhileTyping = false,
  cursorCharacter = '|',
  cursorClassName = '',
  cursorBlinkDuration = 0.5,
  textColors = [],
  variableSpeed,
  variableSpeedEnabled = false,
  variableSpeedMin = 60,
  variableSpeedMax = 120,
  onSentenceComplete,
  startOnVisible = false,
  reverseMode = false,
  highlightWords = [],
  reserveSpace = false,
  ...props
}) => {
  const [displayedText, setDisplayedText] = useState('');
  const [currentCharIndex, setCurrentCharIndex] = useState(0);
  const [isDeleting, setIsDeleting] = useState(false);
  const [currentTextIndex, setCurrentTextIndex] = useState(0);
  const [isVisible, setIsVisible] = useState(!startOnVisible);
  const cursorRef = useRef<HTMLSpanElement>(null);
  const containerRef = useRef<HTMLElement>(null);

  const effectiveVariableSpeed = useMemo(() => {
    if (variableSpeed) return variableSpeed;
    if (variableSpeedEnabled) return { min: variableSpeedMin, max: variableSpeedMax };
    return undefined;
  }, [variableSpeed, variableSpeedEnabled, variableSpeedMin, variableSpeedMax]);

  const rawInput = text ?? texts ?? '';
  const textArray = useMemo(() => (Array.isArray(rawInput) ? rawInput : [rawInput]), [rawInput]);

  const getRandomSpeed = useCallback(() => {
    if (!effectiveVariableSpeed) return typingSpeed;
    const { min, max } = effectiveVariableSpeed;
    return Math.random() * (max - min) + min;
  }, [effectiveVariableSpeed, typingSpeed]);

  const getCurrentTextColor = () => {
    if (textColors.length === 0) return 'inherit';
    return textColors[currentTextIndex % textColors.length];
  };

  useEffect(() => {
    if (!startOnVisible || !containerRef.current) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            setIsVisible(true);
          }
        });
      },
      { threshold: 0.1 }
    );

    observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, [startOnVisible]);

  // GSAP cursor animation removed in favor of hardware-accelerated CSS animation to prevent JS re-render flickering


  useEffect(() => {
    if (!isVisible || textArray.length === 0) return;

    let timeout: ReturnType<typeof setTimeout> | undefined;
    const currentText = textArray[currentTextIndex] || '';
    const processedText = reverseMode ? currentText.split('').reverse().join('') : currentText;

    const executeTypingAnimation = () => {
      if (isDeleting) {
        if (displayedText === '') {
          setIsDeleting(false);
          if (currentTextIndex === textArray.length - 1 && !loop) {
            return;
          }

          if (onSentenceComplete) {
            onSentenceComplete(textArray[currentTextIndex], currentTextIndex);
          }

          setCurrentTextIndex((prev) => (prev + 1) % textArray.length);
          setCurrentCharIndex(0);
          timeout = setTimeout(() => {}, pauseDuration);
        } else {
          timeout = setTimeout(() => {
            setDisplayedText((prev) => prev.slice(0, -1));
          }, deletingSpeed);
        }
      } else {
        if (currentCharIndex < processedText.length) {
          timeout = setTimeout(
            () => {
              setDisplayedText((prev) => prev + processedText[currentCharIndex]);
              setCurrentCharIndex((prev) => prev + 1);
            },
            effectiveVariableSpeed ? getRandomSpeed() : typingSpeed
          );
        } else if (textArray.length >= 1) {
          if (!loop && currentTextIndex === textArray.length - 1) return;
          timeout = setTimeout(() => {
            setIsDeleting(true);
          }, pauseDuration);
        }
      }
    };

    if (currentCharIndex === 0 && !isDeleting && displayedText === '') {
      timeout = setTimeout(executeTypingAnimation, initialDelay);
    } else {
      executeTypingAnimation();
    }

    return () => {
      if (timeout) clearTimeout(timeout);
    };
  }, [
    currentCharIndex,
    displayedText,
    isDeleting,
    typingSpeed,
    deletingSpeed,
    pauseDuration,
    textArray,
    currentTextIndex,
    loop,
    initialDelay,
    isVisible,
    reverseMode,
    effectiveVariableSpeed,
    getRandomSpeed,
    onSentenceComplete,
  ]);

  const shouldHideCursor =
    hideCursorWhileTyping && (currentCharIndex < (textArray[currentTextIndex]?.length || 0) || isDeleting);

  // Render formatted content with highlight words support
  const renderedContent = useMemo(() => {
    const fullTargetText = textArray[currentTextIndex] || '';

    if (!highlightWords || highlightWords.length === 0) {
      // Split on newlines to render <br />
      return displayedText.split('\n').map((line, i, arr) => (
        <React.Fragment key={i}>
          {line}
          {i < arr.length - 1 && <br />}
        </React.Fragment>
      ));
    }

    // Identify highlight slices
    const segments: Array<{ start: number; end: number; config: HighlightWord }> = [];
    for (const hw of highlightWords) {
      const idx = fullTargetText.indexOf(hw.word);
      if (idx !== -1) {
        segments.push({
          start: idx,
          end: idx + hw.word.length,
          config: hw,
        });
      }
    }

    segments.sort((a, b) => a.start - b.start);

    const parts: React.ReactNode[] = [];
    let cur = 0;

    for (const seg of segments) {
      if (cur < seg.start) {
        const textSlice = displayedText.slice(cur, Math.min(displayedText.length, seg.start));
        if (textSlice) {
          parts.push(
            textSlice.split('\n').map((l, i, arr) => (
              <React.Fragment key={`plain-${cur}-${i}`}>
                {l}
                {i < arr.length - 1 && <br />}
              </React.Fragment>
            ))
          );
        }
      }

      if (displayedText.length > seg.start) {
        const highlightSlice = displayedText.slice(seg.start, Math.min(displayedText.length, seg.end));
        if (highlightSlice) {
          parts.push(
            <em
              key={`hl-${seg.start}`}
              style={{
                color: seg.config.color || 'var(--lp-blue, #1877f2)',
                fontStyle: seg.config.italic !== false ? 'italic' : 'normal',
                fontWeight: 'inherit',
              }}
            >
              {highlightSlice.split('\n').map((l, i, arr) => (
                <React.Fragment key={`em-${i}`}>
                  {l}
                  {i < arr.length - 1 && <br />}
                </React.Fragment>
              ))}
            </em>
          );
        }
      }

      cur = Math.max(cur, seg.end);
    }

    if (cur < displayedText.length) {
      const trailing = displayedText.slice(cur);
      parts.push(
        trailing.split('\n').map((l, i, arr) => (
          <React.Fragment key={`trail-${i}`}>
            {l}
            {i < arr.length - 1 && <br />}
          </React.Fragment>
        ))
      );
    }

    return parts;
  }, [displayedText, highlightWords, textArray, currentTextIndex]);

  // Compute ghost full text for space reservation
  const ghostContent = useMemo(() => {
    if (!reserveSpace) return null;
    const fullTargetText = textArray[currentTextIndex] || '';
    return fullTargetText.split('\n').map((line, i, arr) => (
      <React.Fragment key={`ghost-${i}`}>
        {line}
        {i < arr.length - 1 && <br />}
      </React.Fragment>
    ));
  }, [reserveSpace, textArray, currentTextIndex]);

  if (reserveSpace) {
    return createElement(
      Component,
      {
        ref: containerRef,
        className: `text-type text-type--reserved ${className}`,
        ...props,
      },
      <span className="text-type__ghost" aria-hidden="true">
        {ghostContent}
      </span>,
      <span className="text-type__overlay" style={{ color: getCurrentTextColor() || 'inherit' }}>
        <span className="text-type__content">{renderedContent}</span>
        {showCursor && (
          <span
            ref={cursorRef}
            className={`text-type__cursor ${cursorClassName} ${shouldHideCursor ? 'text-type__cursor--hidden' : ''}`}
          >
            {cursorCharacter}
          </span>
        )}
      </span>
    );
  }

  return createElement(
    Component,
    {
      ref: containerRef,
      className: `text-type ${className}`,
      ...props,
    },
    <span className="text-type__content" style={{ color: getCurrentTextColor() || 'inherit' }}>
      {renderedContent}
    </span>,
    showCursor && (
      <span
        ref={cursorRef}
        className={`text-type__cursor ${cursorClassName} ${shouldHideCursor ? 'text-type__cursor--hidden' : ''}`}
      >
        {cursorCharacter}
      </span>
    )
  );
};

export default TextType;
