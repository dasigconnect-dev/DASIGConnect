import { useState } from "react";
import {
  suggestCollectionFromPrompt,
  createAlbum,
  addAlbumAssets,
  type Album,
  type AlbumDetail,
  type PromptCollectionSuggestionResponse,
} from "../../../api/albumApi";
import { searchMediaAssets, type MediaAsset } from "../../../api/mediaApi";
import { useToast } from "../../../context/ToastContext";
import PromptCollectionBuilderModal from "./PromptCollectionBuilderModal";

interface Props {
  open: boolean;
  isAdmin: boolean;
  /** Institution scope (admin: selected institution or null = none). */
  institutionId: string | null;
  onClose: () => void;
  /** Fired after a collection is created from the prompt. */
  onCreated?: (album: Album, detail: AlbumDetail) => void;
}

/**
 * Self-contained AI Collection Builder. Owns prompt/result/selection state and the
 * find + create handlers so it can be dropped into both the Collections page and the
 * Media Library (UC-4.x reuse). Falls back to a local metadata match if the AI endpoint
 * is unavailable.
 */
export default function PromptCollectionBuilderContainer({ open, isAdmin, institutionId, onClose, onCreated }: Props) {
  const toast = useToast();
  const [prompt, setPrompt] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [result, setResult] = useState<PromptCollectionSuggestionResponse | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);

  const scopedInstitutionId = isAdmin ? institutionId ?? undefined : undefined;

  if (!open) return null;

  async function handleFind() {
    const trimmed = prompt.trim();
    if (!trimmed) return;
    setLoading(true);
    try {
      const found = await suggestCollectionFromPrompt({ prompt: trimmed, institutionId: scopedInstitutionId, limit: 40 });
      applyResult(found);
      if (found.candidates.length === 0) toast.info("No matching media found for that prompt.");
    } catch (error) {
      const fallback = await buildLocalPromptSuggestions(trimmed, scopedInstitutionId);
      applyResult(fallback);
      if (fallback.candidates.length === 0) {
        toast.error(errorMessage(error) || "No matching media found.");
      } else {
        toast.warning("AI suggestions are unavailable, so matches were built from the media library.");
      }
    } finally {
      setLoading(false);
    }
  }

  function applyResult(res: PromptCollectionSuggestionResponse) {
    setResult(res);
    setName(res.suggestedName);
    setDescription(`Created from prompt: ${res.prompt}`);
    setSelected(new Set(res.candidates.filter((c) => c.confidence !== "low").map((c) => c.asset.id)));
  }

  function toggle(assetId: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(assetId)) next.delete(assetId);
      else next.add(assetId);
      return next;
    });
  }

  async function handleCreate() {
    if (!result || selected.size === 0 || !name.trim()) return;
    if (isAdmin && !institutionId) {
      toast.error("Select an institution before creating a collection.");
      return;
    }
    const assetIds = result.candidates.filter((c) => selected.has(c.asset.id)).map((c) => c.asset.id);
    setCreating(true);
    try {
      const album = await createAlbum({ name: name.trim(), description: description.trim() || null, institutionId: scopedInstitutionId });
      const detail = await addAlbumAssets(album.id, assetIds);
      toast.success(`Collection "${album.name}" created with ${detail.assetCount} items.`);
      onCreated?.(album, detail);
      reset();
      onClose();
    } catch {
      toast.error("Could not create the AI-assisted collection.");
    } finally {
      setCreating(false);
    }
  }

  function reset() {
    setPrompt("");
    setName("");
    setDescription("");
    setResult(null);
    setSelected(new Set());
  }

  return (
    <PromptCollectionBuilderModal
      prompt={prompt}
      name={name}
      description={description}
      loading={loading}
      creating={creating}
      result={result}
      selected={selected}
      onPromptChange={setPrompt}
      onNameChange={setName}
      onDescriptionChange={setDescription}
      onFind={() => void handleFind()}
      onToggle={toggle}
      onClose={() => { if (!creating) { reset(); onClose(); } }}
      onCreate={() => void handleCreate()}
    />
  );
}

async function buildLocalPromptSuggestions(
  prompt: string,
  institutionId?: string,
): Promise<PromptCollectionSuggestionResponse> {
  const page = await searchMediaAssets({ pageSize: 100, institutionId });
  const tokens = promptTokens(prompt);
  const candidates = page.items
    .map((asset) => ({ asset, score: scoreLocalAsset(asset, tokens) }))
    .filter((c) => c.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, 40)
    .map((c) => ({
      asset: {
        id: c.asset.id,
        assetCode: c.asset.code,
        storageUrl: c.asset.storageUrl,
        fileName: c.asset.fileName,
        title: c.asset.title,
        fileType: c.asset.fileType,
        fileSizeBytes: c.asset.fileSizeBytes,
        aiCategory: c.asset.aiTags?.[0]?.label ?? null,
        createdAt: c.asset.uploadedAt,
        institutionId: c.asset.institutionId,
        institutionName: c.asset.institutionName ?? null,
        uploaderId: c.asset.uploaderId ?? null,
        uploaderEmail: c.asset.uploaderName ?? null,
      },
      score: c.score,
      confidence: c.score >= 6 ? ("high" as const) : c.score >= 3 ? ("medium" as const) : ("low" as const),
      matchReasons: ["Media library match"],
    }));

  return { prompt, suggestedName: suggestedNameFromPrompt(prompt), totalCandidates: candidates.length, candidates };
}

function promptTokens(prompt: string) {
  const stopWords = new Set([
    "a", "an", "and", "are", "at", "by", "find", "for", "from", "image", "images",
    "in", "into", "me", "media", "of", "on", "or", "photo", "photos", "picture",
    "pictures", "show", "the", "to", "video", "videos", "with",
  ]);
  return normalizePrompt(prompt).split(/\s+/).filter((token) => token.length >= 2 && !stopWords.has(token));
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
  const response = (error as { response?: { data?: { error?: string; message?: string } } }).response;
  return response?.data?.error ?? response?.data?.message ?? null;
}
