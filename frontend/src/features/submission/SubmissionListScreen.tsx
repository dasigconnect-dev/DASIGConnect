import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import type { User } from "../../types/auth.types";
import { useSubmissions } from "../../hooks/useSubmissions";
import { useDebouncedValue } from "../../hooks/useDebouncedValue";
import { useToast } from "../../context/ToastContext";
import SpotlightTour from "../onboarding/components/SpotlightTour";
import { useScreenTour } from "../onboarding/hooks/useScreenTour";
import { submissionListTourSteps } from "../onboarding/tours/submissionTour";
import type { QueueFilter } from "./types";
import { statusLabels } from "./constants";
import { formatDate, getSubmissionStatusIcon } from "./utils";
import { QueueLoadingState, QueueState } from "./components/SharedPrimitives";
import { SubmissionCardMedia } from "./components/SubmissionCardMedia";
import "../../styles/submission.css";

const VALID_TABS: QueueFilter[] = ["drafts", "action-needed", "rejected", "submitted", "published", "failed", "all"];

const STATUS_TABS: Array<{ key: QueueFilter; label: string; alert?: boolean }> = [
  { key: "all", label: "All" },
  { key: "drafts", label: "Drafts" },
  { key: "action-needed", label: "Action Needed", alert: true },
  { key: "submitted", label: "Submitted" },
  { key: "published", label: "Published" },
  { key: "rejected", label: "Rejected" },
  { key: "failed", label: "Publish Failed" },
];

/** /submissions — the contributor's list of drafts and posts. The composer is SubmissionScreen. */
export default function SubmissionListScreen({ user }: { user: User }) {
  const navigate = useNavigate();
  const toast = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const [filter, setFilter] = useState<QueueFilter>(() => {
    const tab = new URLSearchParams(window.location.search).get("tab");
    return tab && (VALID_TABS as string[]).includes(tab) ? (tab as QueueFilter) : "all";
  });
  const [queueSearch, setQueueSearch] = useState("");
  const debouncedQueueSearch = useDebouncedValue(queueSearch.trim(), 350);
  const {
    submissions,
    counts,
    totalCount: totalQueuedCount,
    hasNextPage: hasMoreQueued,
    loadingMore,
    loadMoreError,
    loadMore,
    loading,
    refreshing,
    error,
    refresh,
  } = useSubmissions(user, filter, debouncedQueueSearch, true);
  const [refreshingQueue, setRefreshingQueue] = useState(false);

  const { startTour: startSubmissionTour, tourProps: submissionTourProps } = useScreenTour({
    screenId: "submissions-list",
    steps: submissionListTourSteps,
    autoStartDelayMs: 700,
    canStart: !loading,
  });

  // ?tab= has been read by the filter initializer above; drop it from the URL.
  const filterParamConsumedRef = useRef(false);
  useEffect(() => {
    if (filterParamConsumedRef.current) return;
    if (!searchParams.get("tab")) return;
    filterParamConsumedRef.current = true;
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.delete("tab");
        return next;
      },
      { replace: true },
    );
  }, [searchParams, setSearchParams]);

  // On phones the status tabs are a horizontal scroller; keep the active one
  // centred so a filter restored from ?tab= (or picked at the edge) isn't hidden.
  const statusTabsRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    const strip = statusTabsRef.current;
    if (!strip || strip.scrollWidth <= strip.clientWidth) return;
    const tab = strip.querySelector<HTMLElement>(".sub-status-tab.is-active");
    if (!tab) return;
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    strip.scrollTo({
      left: tab.offsetLeft - (strip.clientWidth - tab.offsetWidth) / 2,
      behavior: reduceMotion ? "auto" : "smooth",
    });
  }, [filter]);

  // Infinite scroll: load the next page when the sentinel comes into view.
  const queuedSentinelRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    const target = queuedSentinelRef.current;
    if (!target || !hasMoreQueued || loadingMore || loadMoreError) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) {
          observer.disconnect();
          void loadMore();
        }
      },
      { rootMargin: "240px 0px" },
    );
    observer.observe(target);
    return () => observer.disconnect();
  }, [hasMoreQueued, loadMore, loadMoreError, loadingMore]);

  // A failed background refresh keeps the cached list; say so once per failure.
  const refreshErrorNotifiedRef = useRef(false);
  useEffect(() => {
    if (!error || submissions.length === 0) {
      if (!error) refreshErrorNotifiedRef.current = false;
      return;
    }
    if (refreshErrorNotifiedRef.current) return;
    refreshErrorNotifiedRef.current = true;
    toast.error(error);
  }, [error, submissions.length, toast]);

  async function refreshQueue() {
    if (refreshingQueue) return;
    setRefreshingQueue(true);
    try {
      await refresh();
    } catch {
      // The query error state preserves cached results and drives the existing toast feedback.
    } finally {
      setRefreshingQueue(false);
    }
  }

  const busy = refreshingQueue || loading || refreshing;

  return (
    <div className="submission-screen sub-list-shell-page">
      <main className="sub-list-page">
        <section className="sub-list-head">
          <div>
            <h1 className="sub-list-title">My Submissions</h1>
            <p className="sub-list-subtitle">
              View drafts, submitted posts, and published content before opening the composer.
            </p>
          </div>
          <div className="sub-list-actions">
            <button
              className="sub-btn-ghost"
              type="button"
              onClick={() => startSubmissionTour(true)}
              title="Show interactive feature guide"
              aria-label="Show feature guide"
            >
              <i className="ti ti-help-circle" style={{ fontSize: 14 }} />
              <span>Guide</span>
            </button>
            <button
              className="sub-btn-ghost"
              type="button"
              onClick={() => void refreshQueue()}
              disabled={busy}
              title="Refresh submissions list"
            >
              <i className={`ti ti-refresh${busy ? " spin" : ""}`} style={{ fontSize: 14 }} />
              <span>Refresh</span>
            </button>
            <button className="sub-list-new" type="button" onClick={() => navigate("/submissions/new")}>
              <i className="ti ti-plus"></i>
              New Submission
            </button>
          </div>
        </section>

        <div className="sub-toolbar-card" style={{ marginBottom: "16px" }}>
          <div className="sub-registry-toolbar">
            <div ref={statusTabsRef} className="sub-status-tabs" role="group" aria-label="Filter submissions by status">
              {STATUS_TABS.map((tab) => (
                <button
                  key={tab.key}
                  type="button"
                  className={`sub-status-tab${filter === tab.key ? " is-active" : ""}${
                    tab.alert && !loading && counts[tab.key] > 0 ? " has-alert" : ""
                  }`}
                  onClick={() => setFilter(tab.key)}
                  aria-pressed={filter === tab.key}
                >
                  {tab.label}
                  <span className="sub-status-tab-count">{loading ? "-" : counts[tab.key]}</span>
                </button>
              ))}
            </div>

            <div className="sub-search-wrap">
              <i className="ti ti-search sub-search-icon" aria-hidden="true"></i>
              <input
                type="search"
                className="sub-search-input"
                value={queueSearch}
                onChange={(event) => setQueueSearch(event.target.value)}
                placeholder="Search submissions..."
                aria-label="Search submissions"
              />
            </div>
          </div>
        </div>

        <section className="sub-list-results" aria-label="My submissions">
          {loading ? (
            <QueueLoadingState />
          ) : error && submissions.length === 0 ? (
            <QueueState
              icon="ti-database-off"
              title="Unable to load submissions"
              description="Check your session and backend connection, then refresh the page."
            />
          ) : submissions.length === 0 ? (
            <QueueState
              icon="ti-folder-open"
              title="No submissions found"
              description="Try another filter or create a new submission."
            />
          ) : (
            <>
              {submissions.map((item) => {
                const thumbnail = item.mediaAssets?.[0] ?? item.previewMediaAsset ?? undefined;
                const captionPreview = item.caption || "";
                return (
                  <article
                    className="sub-fb-post-card"
                    key={item.id}
                    onClick={() => navigate(`/submissions/${item.id}`)}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        navigate(`/submissions/${item.id}`);
                      }
                    }}
                  >
                    {/* Header: FB Brand Avatar + Page Info + Status Badge */}
                    <div className="sub-fb-card-head">
                      <div className="sub-fb-avatar" aria-hidden="true">
                        <i className="ti ti-brand-facebook"></i>
                      </div>
                      <div className="sub-fb-author">
                        <div className="sub-fb-author-name">
                          {item.institutionName || user.inst || "DASIGCONNECT"}
                        </div>
                        <div className="sub-fb-author-meta">
                          <span>{formatDate(item.eventDate)}</span>
                          <span className="sub-fb-dot" aria-hidden="true">•</span>
                          <i className="ti ti-world" title="Public post" aria-hidden="true"></i>
                        </div>
                      </div>
                      <div className="sub-fb-status-wrap">
                        <span className={`sub-qi-badge status-${item.status}`}>
                          <i className={getSubmissionStatusIcon(item.status)} aria-hidden="true"></i>
                          {statusLabels[item.status]}
                        </span>
                      </div>
                    </div>

                    {/* Post Content: Event Title & Caption */}
                    <div className="sub-fb-card-content">
                      {item.eventTitle && <h2 className="sub-fb-event-title">{item.eventTitle}</h2>}
                      {captionPreview ? (
                        <p className="sub-fb-caption-text">{captionPreview}</p>
                      ) : (
                        <p className="sub-fb-caption-text sub-fb-empty-text">No caption provided.</p>
                      )}
                    </div>

                    {/* Media Container with Circular Loader */}
                    <SubmissionCardMedia
                      thumbnail={thumbnail}
                      mediaCount={item.mediaCount}
                      detailsLoaded={item.previewMediaAsset !== undefined || item.mediaAssets !== undefined}
                    />

                    {/* Reactions & Engagement Row */}
                    <div className="sub-fb-reactions-bar">
                      <div className="sub-fb-reactions-icons">
                        <span className="sub-fb-react-icon fb-like-icon" title="Like">
                          <i className="ti ti-thumb-up-filled"></i>
                        </span>
                        <span className="sub-fb-react-icon fb-heart-icon" title="Love">
                          <i className="ti ti-heart-filled"></i>
                        </span>
                        <span className="sub-fb-reactions-text">
                          {item.mediaCount ?? 0} media · {item.eventTitle ? "1 Post" : "Draft"}
                        </span>
                      </div>
                      <div className="sub-fb-open-action">
                        <span>Open details</span>
                        <i className="ti ti-chevron-right"></i>
                      </div>
                    </div>

                    {/* Facebook Interactive Bar */}
                    <div className="sub-fb-actions-bar" aria-hidden="true">
                      <div className="sub-fb-action-btn">
                        <i className="ti ti-thumb-up"></i>
                        <span>Like</span>
                      </div>
                      <div className="sub-fb-action-btn">
                        <i className="ti ti-message-circle"></i>
                        <span>Comment</span>
                      </div>
                      <div className="sub-fb-action-btn">
                        <i className="ti ti-share-3"></i>
                        <span>Share</span>
                      </div>
                    </div>
                  </article>
                );
              })}

              {hasMoreQueued && (
                <div ref={queuedSentinelRef} className="sub-load-more-sentinel">
                  {!loadMoreError && <div className="sub-load-more-spinner" />}
                  <span>
                    {loadMoreError
                      ? "Unable to load more submissions. Use Refresh to retry."
                      : "Loading more submissions..."}
                  </span>
                </div>
              )}

              {!hasMoreQueued && totalQueuedCount > 20 && (
                <div className="sub-list-end-indicator">
                  <span>Showing all {totalQueuedCount} submissions</span>
                </div>
              )}
            </>
          )}
        </section>
      </main>
      <SpotlightTour {...submissionTourProps} />
    </div>
  );
}
