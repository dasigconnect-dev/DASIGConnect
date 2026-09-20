import { useCallback, useEffect, useRef, useState } from "react";

export interface UseIncrementalPaginationOptions {
  /** Page size when loading more items. Defaults to 10. */
  pageSize?: number;
  /** Initial number of items visible. Defaults to pageSize. */
  initialSize?: number;
  /** Array of dependencies that reset pagination back to the initial page. */
  resetDeps?: unknown[];
  /** IntersectionObserver root element (null = viewport). */
  root?: Element | null;
  /** Margin around the root. Defaults to "180px" for seamless pre-fetching. */
  rootMargin?: string;
  /** If a specific item ID should be visible, expand visibleCount to include it. */
  selectedItemId?: string | null;
  /** Function to get ID from item, for selectedItemId lookup. */
  getItemId?: (item: unknown) => string | undefined;
}

export interface UseIncrementalPaginationResult<T> {
  visibleItems: T[];
  visibleCount: number;
  totalCount: number;
  hasMore: boolean;
  loadMore: () => void;
  sentinelRef: (node: HTMLElement | null) => void;
}

export function useIncrementalPagination<T>(
  items: T[],
  options: UseIncrementalPaginationOptions = {},
): UseIncrementalPaginationResult<T> {
  const {
    pageSize = 10,
    initialSize = pageSize,
    resetDeps = [],
    root = null,
    rootMargin = "180px",
    selectedItemId,
    getItemId,
  } = options;

  const [visibleCount, setVisibleCount] = useState(initialSize);
  const observerRef = useRef<IntersectionObserver | null>(null);
  const sentinelNodeRef = useRef<HTMLElement | null>(null);

  // Reset pagination whenever dependencies change (filter tab, search, sort, etc.)
  const resetDepsKey = resetDeps.map((d) => String(d)).join("::");
  useEffect(() => {
    setVisibleCount(initialSize);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resetDepsKey, initialSize]);

  // If a specific item was targeted by URL/selection, ensure it's within visible range
  useEffect(() => {
    if (!selectedItemId || !getItemId || items.length === 0) return;
    const targetIdx = items.findIndex((item) => getItemId(item) === selectedItemId);
    if (targetIdx >= 0 && targetIdx >= visibleCount) {
      // Expand count so target index is visible + 1 full page buffer
      setVisibleCount(Math.min(items.length, targetIdx + pageSize));
    }
  }, [selectedItemId, getItemId, items, pageSize, visibleCount]);

  const hasMore = visibleCount < items.length;

  const loadMore = useCallback(() => {
    setVisibleCount((prev) => Math.min(items.length, prev + pageSize));
  }, [items.length, pageSize]);

  // Set up intersection observer on the bottom sentinel
  const sentinelRef = useCallback(
    (node: HTMLElement | null) => {
      sentinelNodeRef.current = node;

      if (observerRef.current) {
        observerRef.current.disconnect();
        observerRef.current = null;
      }

      if (!node || !hasMore) return;

      const observer = new IntersectionObserver(
        (entries) => {
          const first = entries[0];
          if (first?.isIntersecting) {
            loadMore();
          }
        },
        {
          root,
          rootMargin,
          threshold: 0.05,
        },
      );

      observer.observe(node);
      observerRef.current = observer;
    },
    [hasMore, loadMore, root, rootMargin],
  );

  useEffect(() => {
    return () => {
      if (observerRef.current) {
        observerRef.current.disconnect();
      }
    };
  }, []);

  const visibleItems = items.slice(0, visibleCount);

  return {
    visibleItems,
    visibleCount: Math.min(visibleCount, items.length),
    totalCount: items.length,
    hasMore,
    loadMore,
    sentinelRef,
  };
}
