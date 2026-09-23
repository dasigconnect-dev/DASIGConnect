import { useEffect, useState, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  createPostTemplate,
  suggestTemplateFromTopPosts,
  type PostTemplate,
  type TopPostTemplateSuggestion,
} from "../../../api/postTemplateApi";
import { authenticatedQueryMeta } from "../../../lib/queryClient";
import { CAPTION_CHAR_LIMIT, getErrorMessage } from "../utils";
import "./TopPostTemplateModal.css";

interface TopPostTemplateModalProps {
  institutionId: string | null;
  onClose: () => void;
  onSaved: (template: PostTemplate) => void;
}

/**
 * UC-1.5 alternate flow — AI drafts a reusable caption template from the
 * Page's best-performing posts. The draft is only a suggestion: the actor
 * reviews/edits it here and saves it as a normal personal template.
 */
export default function TopPostTemplateModal({ institutionId, onClose, onSaved }: TopPostTemplateModalProps) {
  const [saving, setSaving] = useState(false);
  // One fresh AI call per open / "Regenerate" — never served from cache.
  const suggestion = useQuery({
    queryKey: ["ai", "top-post-template"],
    queryFn: () => suggestTemplateFromTopPosts().then((response) => response.data),
    staleTime: 0,
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
    meta: authenticatedQueryMeta,
  });
  const loading = suggestion.isFetching;
  const data = loading ? undefined : suggestion.data;

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !saving) onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose, saving]);

  const regenerate = () => void suggestion.refetch();

  return (
    <div className="sub-modal-overlay" role="presentation" onMouseDown={() => !saving && onClose()}>
      <div
        className="sub-modal tpt-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="tpt-title"
        aria-busy={loading}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <button className="sub-modal-close" type="button" onClick={onClose} disabled={saving} aria-label="Close">
          <i className="ti ti-x" aria-hidden />
        </button>

        <div className="tpt-head">
          <span className="tpt-head-icon" aria-hidden>
            <i className="ti ti-sparkles" />
          </span>
          <div>
            <div className="sub-modal-title" id="tpt-title">Template from top posts</div>
            <div className="sub-modal-desc">
              AI studies the Page's best-performing posts and drafts a reusable caption. Review and edit it before saving.
            </div>
          </div>
        </div>

        {loading ? (
          <>
            <div className="tpt-status" role="status">
              <i className="ti ti-loader-2 sub-spin" aria-hidden />
              <p className="tpt-status-title">Analyzing recent posts…</p>
              <p className="tpt-status-text">
                Ranking posts by reactions, comments, and shares. This can take up to half a minute.
              </p>
            </div>
            <ModalActions onClose={onClose} />
          </>
        ) : suggestion.isError ? (
          <>
            <div className="tpt-status is-error" role="alert">
              <i className="ti ti-alert-triangle" aria-hidden />
              <p className="tpt-status-title">Couldn't draft a template</p>
              <p className="tpt-status-text">{describeGenerateError(suggestion.error)}</p>
            </div>
            <ModalActions onClose={onClose} onRegenerate={regenerate} regenerateLabel="Try again" />
          </>
        ) : data && !data.available ? (
          <>
            <div className="tpt-status is-empty" role="status">
              <i className="ti ti-chart-bar-off" aria-hidden />
              <p className="tpt-status-title">Not enough engagement to learn from yet</p>
              <p className="tpt-status-text">{data.reason}</p>
            </div>
            <ModalActions onClose={onClose} />
          </>
        ) : data ? (
          <TemplateDraft
            // Remount per result so the editable fields start from the new draft.
            key={suggestion.dataUpdatedAt}
            suggestion={data}
            institutionId={institutionId}
            saving={saving}
            onSavingChange={setSaving}
            onClose={onClose}
            onRegenerate={regenerate}
            onSaved={onSaved}
          />
        ) : null}
      </div>
    </div>
  );
}

function TemplateDraft({
  suggestion,
  institutionId,
  saving,
  onSavingChange,
  onClose,
  onRegenerate,
  onSaved,
}: {
  suggestion: TopPostTemplateSuggestion;
  institutionId: string | null;
  saving: boolean;
  onSavingChange: (saving: boolean) => void;
  onClose: () => void;
  onRegenerate: () => void;
  onSaved: (template: PostTemplate) => void;
}) {
  const [name, setName] = useState(suggestion.name ?? "");
  const [caption, setCaption] = useState(suggestion.caption ?? "");
  const [saveError, setSaveError] = useState("");

  const captionLength = Array.from(caption).length;
  const captionTooLong = captionLength > CAPTION_CHAR_LIMIT;
  const canSave = name.trim() !== "" && caption.trim() !== "" && !captionTooLong && !saving;

  async function save() {
    if (!canSave) return;
    onSavingChange(true);
    setSaveError("");
    try {
      const response = await createPostTemplate({
        name: name.trim(),
        caption,
        tags: suggestion.tags.length > 0 ? suggestion.tags : ["AI"],
        target: `Based on ${suggestion.topPosts.length} top-performing posts`,
        category: "AI Suggested",
        institutionId,
      });
      onSaved(response.data);
    } catch (err) {
      setSaveError(getErrorMessage(err, "Could not save template."));
    } finally {
      onSavingChange(false);
    }
  }

  return (
    <>
      <div className="tpt-grid">
        <div className="tpt-editor">
          <label className="tpt-field">
            <span>Template name</span>
            <input className="sub-finput" value={name} maxLength={80} onChange={(event) => setName(event.target.value)} />
          </label>
          <label className="tpt-field">
            <span>Template caption</span>
            <textarea
              className="sub-finput tpt-caption"
              value={caption}
              rows={11}
              onChange={(event) => setCaption(event.target.value)}
              aria-describedby="tpt-caption-hint"
            />
          </label>
          <div className="tpt-caption-meta" id="tpt-caption-hint">
            <span>Replace the [BRACKETED] parts when you use it.</span>
            <span className={captionTooLong ? "is-over" : undefined}>
              {captionLength} / {CAPTION_CHAR_LIMIT}
            </span>
          </div>
          {suggestion.tags.length > 0 && (
            <ul className="tpt-tags" aria-label="Template tags">
              {suggestion.tags.map((tag) => (
                <li key={tag}>{tag}</li>
              ))}
            </ul>
          )}
        </div>

        <aside className="tpt-evidence" aria-label="Why this template">
          {suggestion.insights.length > 0 && (
            <section>
              <h3 className="tpt-evidence-title">
                <i className="ti ti-bulb" aria-hidden /> Why this works
              </h3>
              <ul className="tpt-insights">
                {suggestion.insights.map((insight) => (
                  <li key={insight}>{insight}</li>
                ))}
              </ul>
            </section>
          )}
          <section>
            <h3 className="tpt-evidence-title">
              <i className="ti ti-trending-up" aria-hidden /> Based on
            </h3>
            <p className="tpt-source">
              Top {suggestion.topPosts.length} of {suggestion.postsConsidered} recent posts{" "}
              {suggestion.source === "facebook_page"
                ? "on the Facebook Page"
                : "published through DASIGConnect (the Facebook Page couldn't be read)"}
            </p>
            <ol className="tpt-posts">
              {suggestion.topPosts.map((post, index) => (
                <li key={`${index}-${post.excerpt}`}>
                  <p className="tpt-post-text">{post.excerpt}</p>
                  <p className="tpt-post-stats">
                    <span title="Reactions">
                      <i className="ti ti-thumb-up" aria-hidden /> {post.reactions}
                    </span>
                    <span title="Comments">
                      <i className="ti ti-message-circle" aria-hidden /> {post.comments}
                    </span>
                    <span title="Shares">
                      <i className="ti ti-share-3" aria-hidden /> {post.shares}
                    </span>
                    {post.publishedAt && <span className="tpt-post-date">{formatDate(post.publishedAt)}</span>}
                  </p>
                </li>
              ))}
            </ol>
          </section>
        </aside>
      </div>

      {saveError && (
        <p className="tpt-save-error" role="alert">
          {saveError}
        </p>
      )}

      <ModalActions
        onClose={onClose}
        closeLabel="Cancel"
        disabled={saving}
        onRegenerate={onRegenerate}
        regenerateLabel="Regenerate"
      >
        <button className="sub-modal-btn info" type="button" onClick={() => void save()} disabled={!canSave}>
          {saving ? "Saving..." : "Save Template"}
        </button>
      </ModalActions>
    </>
  );
}

function ModalActions({
  onClose,
  closeLabel = "Close",
  disabled = false,
  onRegenerate,
  regenerateLabel,
  children,
}: {
  onClose: () => void;
  closeLabel?: string;
  disabled?: boolean;
  onRegenerate?: () => void;
  regenerateLabel?: string;
  children?: ReactNode;
}) {
  return (
    <div className="sub-modal-actions tpt-actions">
      <button className="sub-modal-btn cancel" type="button" onClick={onClose} disabled={disabled}>
        {closeLabel}
      </button>
      {onRegenerate && (
        <button className="sub-modal-btn cancel" type="button" onClick={onRegenerate} disabled={disabled}>
          <i className="ti ti-refresh" aria-hidden /> {regenerateLabel}
        </button>
      )}
      {children}
    </div>
  );
}

function describeGenerateError(err: unknown): string {
  const status = (err as { response?: { status?: number } })?.response?.status;
  if (status === 429) return "You've reached the limit of 10 template drafts per hour. Try again later.";
  if (status === 504) return "The AI took too long to respond. Try again.";
  if (status === 403) return "You don't have permission to use this feature.";
  return getErrorMessage(err, "The AI service is unavailable right now. Try again in a moment.");
}

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", year: "numeric" }).format(date);
}
