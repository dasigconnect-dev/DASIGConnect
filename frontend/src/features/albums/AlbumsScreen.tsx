import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import type { User } from "../../types/auth.types";
import {
  listAlbums,
  getAlbum,
  createAlbum,
  deleteAlbum,
  addAlbumAssets,
  removeAlbumAsset,
  setAlbumCover,
  suggestCollectionFromPrompt,
  type Album,
  type AlbumDetail,
  type PromptCollectionSuggestionResponse,
} from "../../api/albumApi";
import { listInstitutions, type InstitutionResponse } from "../../api/authApi";
import { searchMediaAssets, type MediaAsset } from "../../api/mediaApi";
import { useToast } from "../../context/ToastContext";
import AlbumCard from "./components/AlbumCard";
import AlbumAssetTile from "./components/AlbumAssetTile";
import CreateAlbumModal from "./components/CreateAlbumModal";
import AssetPickerModal from "./components/AssetPickerModal";
import PromptCollectionBuilderModal from "./components/PromptCollectionBuilderModal";
import LibraryScopeSelector from "../media-repository/components/LibraryScopeSelector";
import "../../styles/media-repository.css";
import "../../styles/albums.css";

interface AlbumsScreenProps {
  user: User;
}

export default function AlbumsScreen({ user }: AlbumsScreenProps) {
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const routeState = location.state as { openAiBuilder?: boolean; institutionId?: string | null } | null;
  const isAdmin = user.role === "admin";

  const [albums, setAlbums] = useState<Album[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [institutions, setInstitutions] = useState<InstitutionResponse[]>([]);
  const [institutionsLoading, setInstitutionsLoading] = useState(isAdmin);
  const [selectedInstitutionId, setSelectedInstitutionId] = useState<string | null>(
    isAdmin ? routeState?.institutionId ?? null : null,
  );

  const [openAlbum, setOpenAlbum] = useState<AlbumDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);

  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerAssets, setPickerAssets] = useState<MediaAsset[]>([]);
  const [pickerLoading, setPickerLoading] = useState(false);
  const [pickerSelected, setPickerSelected] = useState<Set<string>>(new Set());
  const [adding, setAdding] = useState(false);

  const [builderOpen, setBuilderOpen] = useState(false);
  const [builderPrompt, setBuilderPrompt] = useState("");
  const [builderName, setBuilderName] = useState("");
  const [builderDescription, setBuilderDescription] = useState("");
  const [builderResult, setBuilderResult] = useState<PromptCollectionSuggestionResponse | null>(null);
  const [builderSelected, setBuilderSelected] = useState<Set<string>>(new Set());
  const [builderLoading, setBuilderLoading] = useState(false);
  const [builderCreating, setBuilderCreating] = useState(false);

  function fetchAlbums(signal?: AbortSignal) {
    setLoading(true);
    setError(null);
    if (isAdmin && !selectedInstitutionId) {
      setAlbums([]);
      setLoading(false);
      return;
    }
    listAlbums(isAdmin ? selectedInstitutionId : undefined, signal)
      .then(setAlbums)
      .catch(() => setError("Could not load collections."))
      .finally(() => setLoading(false));
  }

  // Event-handler reload (sets loading/error then fetches) — kept out of the effect body.
  function reload() {
    fetchAlbums();
  }

  useEffect(() => {
    if (!isAdmin) return;
    listInstitutions()
      .then((res) => {
        setInstitutions(res.data);
        setSelectedInstitutionId((current) => current ?? res.data[0]?.id ?? null);
      })
      .catch(() => toast.error("Could not load institution filters."))
      .finally(() => setInstitutionsLoading(false));
  }, [isAdmin, toast]);

  useEffect(() => {
    if (!routeState?.openAiBuilder) return;
    if (isAdmin && !selectedInstitutionId) return;
    setBuilderOpen(true);
    navigate(location.pathname, { replace: true, state: null });
  }, [isAdmin, location.pathname, navigate, routeState, selectedInstitutionId]);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;

    queueMicrotask(() => {
      if (active) fetchAlbums(controller.signal);
    });

    return () => {
      active = false;
      controller.abort();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAdmin, selectedInstitutionId]);

  async function openDetail(id: string) {
    setDetailLoading(true);
    try {
      setOpenAlbum(await getAlbum(id));
    } catch {
      toast.error("Could not open that collection.");
    } finally {
      setDetailLoading(false);
    }
  }

  async function refreshDetail() {
    if (!openAlbum) return;
    try {
      setOpenAlbum(await getAlbum(openAlbum.id));
    } catch {
      /* keep current view on refresh error */
    }
  }

  async function handleCreate(name: string, description: string) {
    if (isAdmin && !selectedInstitutionId) {
      toast.error("Select an institution before creating a collection.");
      return;
    }
    if (!name.trim()) return;
    setCreating(true);
    try {
      const album = await createAlbum({
        name: name.trim(),
        description: description.trim() || null,
        institutionId: isAdmin ? selectedInstitutionId : undefined,
      });
      setCreateOpen(false);
      setAlbums((prev) => [album, ...prev]);
      toast.success(`Collection "${album.name}" created.`);
    } catch {
      toast.error("Could not create the collection.");
    } finally {
      setCreating(false);
    }
  }

  function openBuilder() {
    if (isAdmin && !selectedInstitutionId) {
      toast.error("Select an institution before using AI Builder.");
      return;
    }
    setBuilderOpen(true);
  }

  async function handleFindPromptMatches() {
    const prompt = builderPrompt.trim();
    if (!prompt) return;
    setBuilderLoading(true);
    try {
      const result = await suggestCollectionFromPrompt({
        prompt,
        institutionId: isAdmin ? selectedInstitutionId : undefined,
        limit: 40,
      });
      setBuilderResult(result);
      setBuilderName(result.suggestedName);
      setBuilderDescription(`Created from prompt: ${result.prompt}`);
      setBuilderSelected(new Set(
        result.candidates
          .filter((candidate) => candidate.confidence !== "low")
          .map((candidate) => candidate.asset.id),
      ));
      if (result.candidates.length === 0) {
        toast.info("No matching media found for that prompt.");
      }
    } catch (error) {
      const fallback = await buildLocalPromptSuggestions(prompt);
      setBuilderResult(fallback);
      setBuilderName(fallback.suggestedName);
      setBuilderDescription(`Created from prompt: ${fallback.prompt}`);
      setBuilderSelected(new Set(
        fallback.candidates
          .filter((candidate) => candidate.confidence !== "low")
          .map((candidate) => candidate.asset.id),
      ));
      if (fallback.candidates.length === 0) {
        toast.error(errorMessage(error) || "No matching media found.");
      } else {
        toast.warning("AI suggestions are unavailable, so matches were built from the media library.");
      }
    } finally {
      setBuilderLoading(false);
    }
  }

  async function buildLocalPromptSuggestions(prompt: string): Promise<PromptCollectionSuggestionResponse> {
    const page = await searchMediaAssets({
      pageSize: 100,
      institutionId: isAdmin ? selectedInstitutionId : undefined,
    });
    const tokens = promptTokens(prompt);
    const candidates = page.items
      .map((asset) => ({
        asset,
        score: scoreLocalAsset(asset, tokens),
      }))
      .filter((candidate) => candidate.score > 0)
      .sort((a, b) => b.score - a.score)
      .slice(0, 40)
      .map((candidate) => ({
        asset: {
          id: candidate.asset.id,
          assetCode: candidate.asset.code,
          storageUrl: candidate.asset.storageUrl,
          fileName: candidate.asset.fileName,
          title: candidate.asset.title,
          fileType: candidate.asset.fileType,
          fileSizeBytes: candidate.asset.fileSizeBytes,
          aiCategory: candidate.asset.aiTags?.[0]?.label ?? null,
          createdAt: candidate.asset.uploadedAt,
          institutionId: candidate.asset.institutionId,
          institutionName: candidate.asset.institutionName ?? null,
          uploaderId: candidate.asset.uploaderId ?? null,
          uploaderEmail: candidate.asset.uploaderName ?? null,
        },
        score: candidate.score,
        confidence: candidate.score >= 6 ? "high" as const : candidate.score >= 3 ? "medium" as const : "low" as const,
        matchReasons: ["Media library match"],
      }));

    return {
      prompt,
      suggestedName: suggestedNameFromPrompt(prompt),
      totalCandidates: candidates.length,
      candidates,
    };
  }

  function toggleBuilderAsset(assetId: string) {
    setBuilderSelected((prev) => {
      const next = new Set(prev);
      if (next.has(assetId)) next.delete(assetId);
      else next.add(assetId);
      return next;
    });
  }

  async function handleCreateFromPrompt() {
    if (!builderResult || builderSelected.size === 0 || !builderName.trim()) return;
    if (isAdmin && !selectedInstitutionId) {
      toast.error("Select an institution before creating a collection.");
      return;
    }

    const assetIds = builderResult.candidates
      .filter((candidate) => builderSelected.has(candidate.asset.id))
      .map((candidate) => candidate.asset.id);

    setBuilderCreating(true);
    try {
      const album = await createAlbum({
        name: builderName.trim(),
        description: builderDescription.trim() || null,
        institutionId: isAdmin ? selectedInstitutionId : undefined,
      });
      const detail = await addAlbumAssets(album.id, assetIds);
      setAlbums((prev) => [{ ...album, assetCount: detail.assetCount }, ...prev]);
      setOpenAlbum(detail);
      setBuilderOpen(false);
      toast.success(`Collection "${album.name}" created with ${detail.assetCount} items.`);
    } catch {
      toast.error("Could not create the AI-assisted collection.");
    } finally {
      setBuilderCreating(false);
    }
  }

  async function handleDeleteAlbum(album: Album) {
    if (!window.confirm(`Delete collection "${album.name}"? The media files themselves are not deleted.`)) return;
    try {
      await deleteAlbum(album.id);
      setAlbums((prev) => prev.filter((a) => a.id !== album.id));
      if (openAlbum?.id === album.id) setOpenAlbum(null);
      toast.success("Collection deleted.");
    } catch {
      toast.error("Could not delete the collection.");
    }
  }

  function openPicker() {
    if (isAdmin && !selectedInstitutionId) {
      toast.error("Select an institution before adding media.");
      return;
    }
    setPickerOpen(true);
    setPickerSelected(new Set());
    setPickerLoading(true);
    searchMediaAssets({
      pageSize: 100,
      institutionId: isAdmin ? selectedInstitutionId : undefined,
    })
      .then((page) => setPickerAssets(page.items))
      .catch(() => toast.error("Could not load media."))
      .finally(() => setPickerLoading(false));
  }

  function togglePick(id: string) {
    setPickerSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  async function handleAddSelected() {
    if (!openAlbum || pickerSelected.size === 0) return;
    setAdding(true);
    try {
      const detail = await addAlbumAssets(openAlbum.id, [...pickerSelected]);
      setOpenAlbum(detail);
      setAlbums((prev) => prev.map((a) => (a.id === detail.id ? { ...a, assetCount: detail.assetCount } : a)));
      setPickerOpen(false);
      toast.success(`Added ${pickerSelected.size} ${pickerSelected.size === 1 ? "item" : "items"}.`);
    } catch {
      toast.error("Could not add media to the collection.");
    } finally {
      setAdding(false);
    }
  }

  async function handleRemoveAsset(assetId: string) {
    if (!openAlbum) return;
    const albumId = openAlbum.id;
    try {
      await removeAlbumAsset(albumId, assetId);
      await refreshDetail();
      setAlbums((prev) =>
        prev.map((a) => (a.id === albumId ? { ...a, assetCount: Math.max(0, a.assetCount - 1) } : a)),
      );
    } catch {
      toast.error("Could not remove the asset.");
    }
  }

  async function handleSetCover(assetId: string) {
    if (!openAlbum) return;
    try {
      await setAlbumCover(openAlbum.id, assetId);
      await refreshDetail();
      toast.success("Cover updated.");
    } catch {
      toast.error("Could not set the cover.");
    }
  }

  const focusedTopNav = (
    <nav className="med-topnav">
      <div className="med-nav-left">
        <button className="med-back-btn" type="button" onClick={() => navigate("/media-repository")}>
          <i className="ti ti-arrow-left"></i>
          <span>Back</span>
        </button>
        <div className="med-nav-brand">
          <div className="med-nav-brand-icon">
            <BrandMark />
          </div>
          <div className="med-nav-brand-name">
            DASIG<em>Connect</em>
          </div>
        </div>
        <div className="med-nav-breadcrumb" aria-label="Breadcrumb">
          <span className="med-nav-chevron">&gt;</span>
          <button className="alb-crumb-link" type="button" onClick={() => navigate("/media-repository")}>
            Media Library
          </button>
          <span className="med-nav-chevron">&gt;</span>
          <span>Collections</span>
        </div>
      </div>
      <div className="med-nav-right">
        <div className="med-nav-chip">{formatRole(user.role)}</div>
        <div className="med-nav-avatar" aria-label={user.email}>
          {user.initials}
        </div>
      </div>
    </nav>
  );

  // ── Detail view ───────────────────────────────────────────────────────────
  if (openAlbum) {
    return (
      <div className="med-page alb-page">
        {focusedTopNav}
        <div className="alb-page-canvas">
          <div className="med-header">
            <div>
              <button className="alb-back" type="button" onClick={() => setOpenAlbum(null)}>
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <line x1="19" y1="12" x2="5" y2="12" />
                  <polyline points="12,19 5,12 12,5" />
                </svg>
                All collections
              </button>
              <h1 className="med-title">{openAlbum.name}</h1>
              <p className="med-subtitle">
                {openAlbum.assetCount} {openAlbum.assetCount === 1 ? "item" : "items"}
                {openAlbum.source === "ai_suggested" ? " - AI-suggested" : ""}
                {openAlbum.description ? ` - ${openAlbum.description}` : ""}
              </p>
            </div>
            <div className="med-header-actions">
              <button className="med-btn med-btn-primary med-btn-sm" type="button" onClick={openPicker}>
                Add media
              </button>
            </div>
          </div>

          {openAlbum.assets.length === 0 ? (
            <div className="med-empty">
              <div className="med-empty-title">This collection is empty</div>
              <p className="med-empty-sub">Add media when this collection is ready to reuse.</p>
            </div>
          ) : (
            <div className="alb-grid">
              {openAlbum.assets.map((asset) => (
                <AlbumAssetTile
                  key={asset.id}
                  asset={asset}
                  isCover={openAlbum.coverAssetId === asset.id}
                  onSetCover={(id) => void handleSetCover(id)}
                  onRemove={(id) => void handleRemoveAsset(id)}
                />
              ))}
            </div>
          )}
        </div>

        {pickerOpen && (
          <AssetPickerModal
            assets={pickerAssets}
            loading={pickerLoading}
            selected={pickerSelected}
            adding={adding}
            onToggle={togglePick}
            onClose={() => { if (!adding) setPickerOpen(false); }}
            onConfirm={() => void handleAddSelected()}
          />
        )}
      </div>
    );
  }

  // ── List view ─────────────────────────────────────────────────────────────
  return (
    <div className="med-page alb-page">
      {focusedTopNav}
      <div className="alb-page-canvas">
        <div className="med-header">
          <div>
            <h1 className="med-title">Collections</h1>
            <p className="med-subtitle">Reusable media sets for campaigns and events</p>
          </div>
          <div className="med-header-actions">
            <button className="med-btn med-btn-ghost med-btn-sm" type="button" onClick={openBuilder}>
              AI Builder
            </button>
            <button className="med-btn med-btn-primary med-btn-sm" type="button" onClick={() => setCreateOpen(true)}>
              New Collection
            </button>
          </div>
        </div>

      {isAdmin && (
        <LibraryScopeSelector
          institutions={institutions}
          selectedInstitutionId={selectedInstitutionId}
          loading={institutionsLoading}
          onInstitutionChange={(institutionId) => {
            setOpenAlbum(null);
            setSelectedInstitutionId(institutionId);
          }}
        />
      )}

      {loading ? (
        <div className="med-empty"><div className="med-empty-title">Loading collections...</div></div>
      ) : isAdmin && !selectedInstitutionId ? (
        <div className="med-empty">
          <div className="med-empty-title">Select an institution</div>
          <p className="med-empty-sub">Choose an institution to view its collections.</p>
        </div>
      ) : error ? (
        <div className="med-empty">
          <div className="med-empty-title">Failed to load collections</div>
          <p className="med-empty-sub">{error}</p>
          <button className="med-btn med-btn-ghost" type="button" style={{ marginTop: 20 }} onClick={reload}>Try again</button>
        </div>
      ) : albums.length === 0 ? (
        <div className="med-empty">
          <div className="med-empty-title">No collections yet</div>
          <p className="med-empty-sub">Create a reusable set for related media.</p>
          <button className="med-btn med-btn-primary" type="button" style={{ marginTop: 20 }} onClick={() => setCreateOpen(true)}>New Collection</button>
        </div>
      ) : (
        <div className="alb-list">
          {albums.map((album) => (
            <AlbumCard
              key={album.id}
              album={album}
              onOpen={(id) => void openDetail(id)}
              onDelete={(a) => void handleDeleteAlbum(a)}
            />
          ))}
        </div>
      )}
      </div>

      {createOpen && (
        <CreateAlbumModal
          creating={creating}
          onCancel={() => setCreateOpen(false)}
          onCreate={(name, description) => void handleCreate(name, description)}
        />
      )}

      {builderOpen && (
        <PromptCollectionBuilderModal
          prompt={builderPrompt}
          name={builderName}
          description={builderDescription}
          loading={builderLoading}
          creating={builderCreating}
          result={builderResult}
          selected={builderSelected}
          onPromptChange={setBuilderPrompt}
          onNameChange={setBuilderName}
          onDescriptionChange={setBuilderDescription}
          onFind={() => void handleFindPromptMatches()}
          onToggle={toggleBuilderAsset}
          onClose={() => { if (!builderCreating) setBuilderOpen(false); }}
          onCreate={() => void handleCreateFromPrompt()}
        />
      )}

      {detailLoading && <div className="alb-loading-veil">Opening collection...</div>}
    </div>
  );
}

function BrandMark() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 2L22 7V17L12 22L2 17V7L12 2Z" />
    </svg>
  );
}

function promptTokens(prompt: string) {
  const stopWords = new Set([
    "a", "an", "and", "are", "at", "by", "find", "for", "from", "image", "images",
    "in", "into", "me", "media", "of", "on", "or", "photo", "photos", "picture",
    "pictures", "show", "the", "to", "video", "videos", "with",
  ]);
  return normalizePrompt(prompt)
    .split(/\s+/)
    .filter((token) => token.length >= 2 && !stopWords.has(token));
}

function scoreLocalAsset(asset: MediaAsset, tokens: string[]) {
  const haystack = normalizePrompt([
    asset.title,
    asset.fileName,
    asset.institutionName,
    asset.uploaderName,
    ...(asset.aiTags ?? []).map((tag) => tag.label),
  ].filter(Boolean).join(" "));
  return tokens.reduce((score, token) => score + (haystack.includes(token) ? 3 : 0), 0);
}

function suggestedNameFromPrompt(prompt: string) {
  const words = promptTokens(prompt).slice(0, 5);
  if (words.length === 0) return "Prompt Collection";
  return `${words.map((word) => word.charAt(0).toUpperCase() + word.slice(1)).join(" ")} Collection`;
}

function normalizePrompt(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, " ").trim().replace(/\s+/g, " ");
}

function errorMessage(error: unknown) {
  if (typeof error !== "object" || error === null) return null;
  const response = (error as { response?: { data?: { error?: string; message?: string }; status?: number } }).response;
  return response?.data?.error ?? response?.data?.message ?? null;
}

function formatRole(role: User["role"]) {
  if (role === "admin") return "Administrator";
  return role.charAt(0).toUpperCase() + role.slice(1);
}
