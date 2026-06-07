import { api } from "./authApi";

export type MediaAssetStatus = "processing" | "ready" | "error";

export interface AiTag {
  label: string;
  confidence: number;
}

export interface MediaUsage {
  submissionId: string;
  submissionTitle: string;
  submittedAt: string;
  submissionStatus: string;
  deepLink?: string;
}

export interface MediaAsset {
  id: string;
  code: string;
  title: string;
  fileName: string;
  fileType: string;
  fileSizeBytes: number;
  storageUrl: string;
  institutionId: string;
  institutionName?: string;
  uploaderId?: string;
  uploaderName?: string;
  uploadedAt: string;
  status: MediaAssetStatus;
  aiTags?: AiTag[];
  usedIn?: MediaUsage[];
  folderId?: string | null;
  widthPx?: number;
  heightPx?: number;
  durationSeconds?: number;
  importBatchId?: string | null;
  duplicateOfId?: string | null;
  aiDescription?: string | null;
  blurScore?: number | null;
  curatedAt?: string | null;
  aiClassifiedAt?: string | null;
  embeddingGeneratedAt?: string | null;
  /** UC-4.x consent/visibility: "internal_only" | "cleared_for_public". */
  visibility?: string | null;
  /** UC-4.12 bit-level file integrity, separate from duplicate perceptual hashes. */
  contentSha256?: string | null;
  checksumGeneratedAt?: string | null;
  integrityStatus?: MediaIntegrityStatus | null;
  integrityCheckedAt?: string | null;
  integrityFailureReason?: string | null;
  integrityReviewStatus?: MediaIntegrityReviewStatus | null;
  integrityReviewAcknowledgedAt?: string | null;
  integrityReviewAcknowledgedBy?: string | null;
}

export type MediaVisibility = "internal_only" | "cleared_for_public";
export type MediaIntegrityStatus = "pending" | "verified" | "mismatch" | "missing" | "error";
export type MediaIntegrityReviewStatus = "none" | "open" | "acknowledged" | "resolved";

export interface DeleteCheckResult {
  tier: "blocked" | "warning" | "free";
  blockingUsages: MediaUsage[];
  warningUsages: MediaUsage[];
}

export interface MediaAssetUploadUrlRequest {
  fileName: string;
  fileType: string;
  institutionId?: string | null;
}

export interface MediaAssetUploadUrlResponse {
  signedUrl: string;
  publicUrl: string;
  path: string;
}

export interface MediaAssetRegisterRequest {
  storageUrl: string;
  fileName: string;
  fileType: string;
  fileSizeBytes: number;
  institutionId?: string | null;
  importBatchId?: string | null;
}

export interface MediaImportBatchRequest {
  assetCount: number;
  institutionId?: string | null;
}

export interface MediaImportBatch {
  id: string;
  institutionId: string;
  uploadedBy: string;
  assetCount: number;
  registeredAssetCount?: number;
  readyAssetCount?: number;
  curatedAssetCount?: number;
  createdAt: string;
}

interface MediaAssetPageResponse {
  items: Array<{
    id: string;
    assetCode: string;
    storageUrl: string;
    fileName: string;
    fileType: string;
    fileSizeBytes: number;
    aiCategory?: string | null;
    createdAt: string;
    institutionId?: string | null;
    institutionName?: string | null;
    uploaderId?: string | null;
    uploaderEmail?: string | null;
    folderId?: string | null;
    title?: string | null;
    importBatchId?: string | null;
    duplicateOfId?: string | null;
    curatedAt?: string | null;
    visibility?: string | null;
  }>;
  totalCount: number;
  page: number;
  pageSize: number;
}

export interface MediaAssetSearchParams {
  networkView?: boolean;
  institutionId?: string | null;
  query?: string;
  aiCategory?: string;
  mediaType?: "image" | "video";
  page?: number;
  pageSize?: number;
}

export interface MediaAssetPage {
  items: MediaAsset[];
  totalCount: number;
  page: number;
  pageSize: number;
}

function rawToAsset(raw: MediaAssetPageResponse["items"][0]): MediaAsset {
  return {
    id: raw.id,
    code: raw.assetCode,
    title: raw.title || raw.fileName,
    fileName: raw.fileName,
    fileType: raw.fileType,
    fileSizeBytes: raw.fileSizeBytes,
    storageUrl: raw.storageUrl,
    institutionId: raw.institutionId ?? "",
    institutionName: raw.institutionName ?? undefined,
    uploaderId: raw.uploaderId ?? undefined,
    uploaderName: raw.uploaderEmail ?? undefined,
    uploadedAt: raw.createdAt,
    status: "ready" as const,
    aiTags: raw.aiCategory ? [{ label: raw.aiCategory, confidence: 100 }] : [],
    folderId: raw.folderId ?? null,
    importBatchId: raw.importBatchId ?? null,
    duplicateOfId: raw.duplicateOfId ?? null,
    curatedAt: raw.curatedAt ?? null,
    visibility: raw.visibility ?? null,
  };
}

export function listMediaAssets(
  params?: { networkView?: boolean; institutionId?: string | null; health?: string | null },
  signal?: AbortSignal,
) {
  const scope = params?.networkView ? "network" : undefined;
  const institutionId = params?.institutionId ?? undefined;
  const health = params?.health ?? undefined;
  return api
    .get<MediaAssetPageResponse>("/media-assets", {
      params: {
        ...(scope ? { scope } : {}),
        ...(institutionId ? { institutionId } : {}),
        ...(health ? { health } : {}),
      },
      signal,
    })
    .then((response) => ({
      ...response,
      data: (response.data.items ?? []).map(rawToAsset),
    }));
}

/**
 * Loads the FULL active library for the browse grid by paging through the list endpoint
 * (which caps each page at 100). The grid filters folders/tags client-side, so it needs the
 * complete set rather than just the first page (otherwise only the first 25 ever show).
 */
export async function listAllMediaAssets(
  params?: { networkView?: boolean; institutionId?: string | null; health?: string | null },
  signal?: AbortSignal,
): Promise<MediaAsset[]> {
  const scope = params?.networkView ? "network" : undefined;
  const institutionId = params?.institutionId ?? undefined;
  const health = params?.health ?? undefined;
  const pageSize = 100;
  const all: MediaAsset[] = [];
  let page = 1;
  // Safety cap: 100 pages = 10k assets — far beyond realistic per-institution libraries.
  while (page <= 100) {
    const res = await api.get<MediaAssetPageResponse>("/media-assets", {
      params: {
        ...(scope ? { scope } : {}),
        ...(institutionId ? { institutionId } : {}),
        ...(health ? { health } : {}),
        page,
        pageSize,
      },
      signal,
    });
    const items = res.data.items ?? [];
    all.push(...items.map(rawToAsset));
    const total = res.data.totalCount ?? all.length;
    if (items.length < pageSize || all.length >= total) {
      break;
    }
    page += 1;
  }
  return all;
}

export async function searchMediaAssets(
  params: MediaAssetSearchParams = {},
  signal?: AbortSignal
): Promise<MediaAssetPage> {
  const queryParams: Record<string, string | number | undefined> = {
    scope: params.networkView ? "network" : undefined,
    institutionId: params.institutionId ?? undefined,
    query: params.query || undefined,
    aiCategory: params.aiCategory || undefined,
    mediaType: params.mediaType || undefined,
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 24,
  };
  Object.keys(queryParams).forEach(
    (k) => queryParams[k] === undefined && delete queryParams[k]
  );
  const res = await api.get<MediaAssetPageResponse>("/media-assets", {
    params: queryParams,
    signal,
  });
  return {
    items: (res.data.items ?? []).map(rawToAsset),
    totalCount: res.data.totalCount ?? 0,
    page: res.data.page ?? 1,
    pageSize: res.data.pageSize ?? 24,
  };
}

/* ===== UC-4.5 Hybrid (natural-language) search ===== */

export interface MediaSearchHit {
  asset: MediaAsset;
  /** Fused relevance score (RRF + deterministic re-rank). */
  score: number;
  /** Human-readable reasons the asset matched, for UI transparency. */
  matchReasons: string[];
  lexicalRank: number | null;
  semanticRank: number | null;
  imageRank: number | null;
}

export interface MediaSearchResult {
  query: string;
  hits: MediaSearchHit[];
  totalCount: number;
  page: number;
  pageSize: number;
  /** True when results are ordered chronologically (date-only browse), not by relevance. */
  chronological: boolean;
}

export interface HybridSearchParams {
  query: string;
  networkView?: boolean;
  institutionId?: string | null;
  mediaType?: "image" | "video";
  page?: number;
  pageSize?: number;
}

interface MediaAssetSearchRawResponse {
  query: string;
  items: Array<{
    asset: MediaAssetPageResponse["items"][0];
    score: number;
    lexicalRank?: number | null;
    semanticRank?: number | null;
    imageRank?: number | null;
    matchReasons?: string[];
  }>;
  totalCount: number;
  page: number;
  pageSize: number;
  chronological?: boolean;
}

/**
 * UC-4.5 hybrid search: lexical (tsvector) + semantic + cross-modal vectors fused
 * with RRF and deterministically re-ranked on the backend. Returns ranked hits
 * each carrying explainable match reasons. POST so the natural-language query is
 * never logged in a URL or proxy.
 */
export async function hybridSearchMediaAssets(
  params: HybridSearchParams,
  signal?: AbortSignal,
): Promise<MediaSearchResult> {
  const body: Record<string, unknown> = { query: params.query };
  if (params.networkView) body.scope = "network";
  else if (params.institutionId) body.institutionId = params.institutionId;
  if (params.mediaType) body.mediaType = params.mediaType;
  if (params.page) body.page = params.page;
  if (params.pageSize) body.pageSize = params.pageSize;

  const res = await api.post<MediaAssetSearchRawResponse>("/media-assets/search", body, { signal });
  return {
    query: res.data.query,
    hits: (res.data.items ?? []).map((it) => ({
      asset: rawToAsset(it.asset),
      score: it.score,
      matchReasons: it.matchReasons ?? [],
      lexicalRank: it.lexicalRank ?? null,
      semanticRank: it.semanticRank ?? null,
      imageRank: it.imageRank ?? null,
    })),
    totalCount: res.data.totalCount ?? 0,
    page: res.data.page ?? 1,
    pageSize: res.data.pageSize ?? 24,
    chronological: res.data.chronological ?? false,
  };
}

/* ===== UC-4.5 related-search autocomplete ===== */

export interface RemoteSuggestion {
  text: string;
  /** tag | file | uploader | collection | folder */
  type: string;
}

interface MediaSuggestionsRawResponse {
  query: string;
  suggestions?: RemoteSuggestion[];
}

/**
 * Fetches Google-style related-search suggestions spanning the whole tenant-scoped
 * media library (tags, filenames, uploaders, collections, folders).
 */
export async function fetchMediaSearchSuggestions(
  params: { query: string; networkView?: boolean; institutionId?: string | null; limit?: number },
  signal?: AbortSignal,
): Promise<RemoteSuggestion[]> {
  const search = new URLSearchParams();
  search.set("q", params.query);
  if (params.networkView) search.set("scope", "network");
  else if (params.institutionId) search.set("institutionId", params.institutionId);
  if (params.limit) search.set("limit", String(params.limit));

  const res = await api.get<MediaSuggestionsRawResponse>(
    `/media-assets/search/suggestions?${search.toString()}`,
    { signal },
  );
  return res.data.suggestions ?? [];
}

/* ===== UC-4.6 search feedback ===== */

export type SearchFeedbackAction =
  | "thumbs_up"
  | "thumbs_down"
  | "selected"
  | "applied"
  | "dismissed";

/**
 * Records feedback on a media-search result (UC-4.6). Best-effort by design — a logging
 * failure must never disrupt the user's search experience, so errors are swallowed.
 */
export function recordSearchFeedback(input: {
  assetId: string;
  action: SearchFeedbackAction;
  query?: string;
  rank?: number;
}): Promise<void> {
  return api
    .post<void>("/ai/feedback/search", input)
    .then(() => undefined)
    .catch(() => undefined);
}

export interface MediaAssetDetailResponse {
  id: string;
  assetCode: string;
  storageUrl: string;
  fileName: string;
  fileType: string;
  fileSizeBytes: number;
  status?: string | null;
  aiCategory?: string | null;
  aiConfidence?: number | null;
  aiDescription?: string | null;
  blurScore?: number | null;
  curatedAt?: string | null;
  visibility?: string | null;
  aiClassifiedAt?: string | null;
  embeddingGeneratedAt?: string | null;
  contentSha256?: string | null;
  checksumGeneratedAt?: string | null;
  integrityStatus?: string | null;
  integrityCheckedAt?: string | null;
  integrityFailureReason?: string | null;
  integrityReviewStatus?: string | null;
  integrityReviewAcknowledgedAt?: string | null;
  integrityReviewAcknowledgedBy?: string | null;
  createdAt: string;
  institutionId?: string | null;
  institutionName?: string | null;
  uploaderId?: string | null;
  uploaderEmail?: string | null;
  title?: string | null;
  importBatchId?: string | null;
  duplicateOfId?: string | null;
  usedIn?: Array<{
    submissionId: string;
    eventTitle: string;
    submittedAt: string;
    status: string;
    deepLink?: string;
  }>;
  tags?: Array<{ id: string; label: string }>;
}

export interface MediaAssetCurationEdit {
  assetId: string;
  title: string;
  tags: string[];
}

function mapDetailToAsset(raw: MediaAssetDetailResponse): MediaAsset {
  const aiTags: AiTag[] = [];
  if (raw.aiCategory) {
    aiTags.push({
      label: raw.aiCategory,
      confidence: raw.aiConfidence != null ? Math.round(Number(raw.aiConfidence) * 100) : 100,
    });
  }
  for (const tag of raw.tags ?? []) {
    if (!aiTags.some((t) => t.label === tag.label)) {
      aiTags.push({ label: tag.label, confidence: 100 });
    }
  }
  return {
    id: raw.id,
    code: raw.assetCode,
    title: raw.title || raw.fileName,
    fileName: raw.fileName,
    fileType: raw.fileType,
    fileSizeBytes: raw.fileSizeBytes,
    storageUrl: raw.storageUrl,
    institutionId: raw.institutionId ?? "",
    institutionName: raw.institutionName ?? undefined,
    uploaderId: raw.uploaderId ?? undefined,
    uploaderName: raw.uploaderEmail ?? undefined,
    uploadedAt: raw.createdAt,
    status: raw.status?.toLowerCase() === "failed"
      ? "error"
      : raw.status?.toLowerCase() === "processing"
        ? "processing"
        : "ready",
    aiTags: aiTags.length > 0 ? aiTags : undefined,
    usedIn: (raw.usedIn ?? []).map((u) => ({
      submissionId: u.submissionId,
      submissionTitle: u.eventTitle,
      submittedAt: u.submittedAt,
      submissionStatus: u.status,
      deepLink: u.deepLink,
    })),
    importBatchId: raw.importBatchId ?? null,
    duplicateOfId: raw.duplicateOfId ?? null,
    aiDescription: raw.aiDescription ?? null,
    blurScore: raw.blurScore ?? null,
    curatedAt: raw.curatedAt ?? null,
    visibility: raw.visibility ?? null,
    aiClassifiedAt: raw.aiClassifiedAt ?? null,
    embeddingGeneratedAt: raw.embeddingGeneratedAt ?? null,
    contentSha256: raw.contentSha256 ?? null,
    checksumGeneratedAt: raw.checksumGeneratedAt ?? null,
    integrityStatus: normalizeIntegrityStatus(raw.integrityStatus),
    integrityCheckedAt: raw.integrityCheckedAt ?? null,
    integrityFailureReason: raw.integrityFailureReason ?? null,
    integrityReviewStatus: normalizeIntegrityReviewStatus(raw.integrityReviewStatus),
    integrityReviewAcknowledgedAt: raw.integrityReviewAcknowledgedAt ?? null,
    integrityReviewAcknowledgedBy: raw.integrityReviewAcknowledgedBy ?? null,
  };
}

function normalizeIntegrityStatus(value?: string | null): MediaIntegrityStatus | null {
  const normalized = value?.toLowerCase();
  if (
    normalized === "pending"
    || normalized === "verified"
    || normalized === "mismatch"
    || normalized === "missing"
    || normalized === "error"
  ) {
    return normalized;
  }
  return null;
}

function normalizeIntegrityReviewStatus(value?: string | null): MediaIntegrityReviewStatus | null {
  const normalized = value?.toLowerCase();
  if (
    normalized === "none"
    || normalized === "open"
    || normalized === "acknowledged"
    || normalized === "resolved"
  ) {
    return normalized;
  }
  return null;
}

export function getMediaAsset(id: string, signal?: AbortSignal) {
  return api
    .get<MediaAssetDetailResponse>(`/media-assets/${id}`, { signal })
    .then((res) => ({ ...res, data: mapDetailToAsset(res.data) }));
}

/* ===== UC-4.11 provenance / audit trail ===== */

export interface MediaAuditEntry {
  action: string;
  actorName: string | null;
  actorEmail: string | null;
  createdAt: string;
  metadata: string | null;
}

/** UC-4.11: an asset's provenance trail (newest first). */
export function getAssetHistory(id: string, signal?: AbortSignal) {
  return api
    .get<MediaAuditEntry[]>(`/media-assets/${id}/history`, { signal })
    .then((res) => res.data);
}

/** UC-4.x: change an asset's consent/visibility. Returns the updated detail asset. */
export function changeAssetVisibility(id: string, visibility: MediaVisibility) {
  return api
    .patch<MediaAssetDetailResponse>(`/media-assets/${id}/visibility`, { visibility })
    .then((res) => mapDetailToAsset(res.data));
}

export interface MediaIntegrityResult {
  assetId: string;
  contentSha256: string | null;
  integrityStatus: MediaIntegrityStatus | null;
  checksumGeneratedAt: string | null;
  integrityCheckedAt: string | null;
  integrityFailureReason: string | null;
  integrityReviewStatus: MediaIntegrityReviewStatus | null;
  integrityReviewAcknowledgedAt: string | null;
  integrityReviewAcknowledgedBy: string | null;
}

/** UC-4.12 Phase 7A: verify stored bytes against the immutable ingest SHA-256. */
export function verifyMediaAssetIntegrity(id: string): Promise<MediaIntegrityResult> {
  return api
    .post<Omit<MediaIntegrityResult, "integrityStatus" | "integrityReviewStatus"> & {
      integrityStatus: string | null;
      integrityReviewStatus: string | null;
    }>(
      `/media-assets/${id}/integrity-check`,
    )
    .then((res) => ({
      ...res.data,
      integrityStatus: normalizeIntegrityStatus(res.data.integrityStatus),
      integrityReviewStatus: normalizeIntegrityReviewStatus(res.data.integrityReviewStatus),
    }));
}

export interface MediaIntegrityCheck {
  id: string;
  expectedSha256: string | null;
  observedSha256: string | null;
  status: MediaIntegrityStatus | null;
  trigger: "ingest" | "manual" | "scheduled" | "repair_verification";
  failureReason: string | null;
  checkedAt: string;
}

/** UC-4.12 Phase 7B: append-only preservation check history. */
export function getMediaAssetIntegrityHistory(id: string): Promise<MediaIntegrityCheck[]> {
  return api
    .get<Array<Omit<MediaIntegrityCheck, "status" | "trigger"> & {
      status: string | null;
      trigger: string;
    }>>(`/media-assets/${id}/integrity-history`)
    .then((res) => res.data.map((check) => ({
      ...check,
      status: normalizeIntegrityStatus(check.status),
      trigger: check.trigger.toLowerCase() as MediaIntegrityCheck["trigger"],
    })));
}

/** UC-4.12 Phase 7B: mark an active failure as owned by a reviewer. */
export function acknowledgeMediaAssetIntegrityReview(id: string): Promise<MediaIntegrityResult> {
  return api
    .post<Omit<MediaIntegrityResult, "integrityStatus" | "integrityReviewStatus"> & {
      integrityStatus: string | null;
      integrityReviewStatus: string | null;
    }>(`/media-assets/${id}/integrity-review/acknowledge`)
    .then((res) => ({
      ...res.data,
      integrityStatus: normalizeIntegrityStatus(res.data.integrityStatus),
      integrityReviewStatus: normalizeIntegrityReviewStatus(res.data.integrityReviewStatus),
    }));
}

/* ===== UC-4.12 Phase 7D: Rights & consent records ===== */

export type RightsBasis = "owned" | "consent" | "licensed" | "public_domain" | "unknown";
export type RightsState = "incomplete" | "expired" | "expiring_soon" | "cleared";

export interface MediaAssetRights {
  assetId: string;
  present: boolean;
  rightsHolder: string | null;
  rightsBasis: RightsBasis;
  licenseUri: string | null;
  consentReference: string | null;
  clearanceDate: string | null;
  expiresAt: string | null;
  permittedChannels: string[];
  restrictions: string | null;
  supportingDocumentUrl: string | null;
  verifiedBy: string | null;
  verifiedAt: string | null;
  derivedState: RightsState;
  permitsPublicClearance: boolean;
  updatedAt: string | null;
}

export interface MediaAssetRightsUpdate {
  rightsHolder?: string | null;
  rightsBasis: RightsBasis;
  licenseUri?: string | null;
  consentReference?: string | null;
  clearanceDate?: string | null;
  expiresAt?: string | null;
  permittedChannels?: string[];
  restrictions?: string | null;
  supportingDocumentUrl?: string | null;
}

export function getAssetRights(id: string): Promise<MediaAssetRights> {
  return api.get<MediaAssetRights>(`/media-assets/${id}/rights`).then((res) => res.data);
}

export function putAssetRights(id: string, payload: MediaAssetRightsUpdate): Promise<MediaAssetRights> {
  return api.put<MediaAssetRights>(`/media-assets/${id}/rights`, payload).then((res) => res.data);
}

/* ===== UC-4.12 Phase 7F: Duplicate review ===== */

export type DuplicateDecision = "duplicate" | "keep_both" | "not_duplicate";

export interface DuplicateAssetSummary {
  id: string;
  assetCode: string;
  storageUrl: string;
  fileName: string;
  title?: string | null;
  fileType: string;
  createdAt?: string;
}

export interface DuplicateCandidatePair {
  candidateAssetId: string;
  candidate: DuplicateAssetSummary;
  compared: DuplicateAssetSummary | null;
  hammingDistance: number | null;
  decision: DuplicateDecision | null;
  canonicalAssetId: string | null;
  mergedTags: boolean;
  reviewedAt: string | null;
}

export interface DuplicateDecisionResult {
  reviewId: string;
  candidateAssetId: string;
  decision: DuplicateDecision;
  canonicalAssetId: string | null;
  mergedTags: boolean;
  reviewedAt: string | null;
}

export function getDuplicateCandidates(
  params?: { status?: "pending" | "reviewed"; institutionId?: string | null },
  signal?: AbortSignal,
): Promise<DuplicateCandidatePair[]> {
  return api
    .get<DuplicateCandidatePair[]>("/media-duplicates", {
      params: {
        status: params?.status ?? "pending",
        ...(params?.institutionId ? { institutionId: params.institutionId } : {}),
      },
      signal,
    })
    .then((res) => res.data);
}

export function decideDuplicate(
  candidateId: string,
  payload: { decision: DuplicateDecision; canonicalAssetId?: string | null; mergeTags?: boolean },
): Promise<DuplicateDecisionResult> {
  return api
    .post<DuplicateDecisionResult>(`/media-duplicates/${candidateId}/decision`, payload)
    .then((res) => res.data);
}

/* ===== UC-4.12 Phase 7E: Versioning & lineage ===== */

export type RelationType = "new_version" | "derived_from" | "replacement_for" | "component_of";

export interface LineageAssetSummary {
  id: string;
  assetCode: string;
  fileName: string;
  storageUrl: string;
  fileType: string;
  title?: string | null;
}

export interface LineageEdge {
  relationId: string;
  relationType: RelationType;
  createdAt: string | null;
  asset: LineageAssetSummary;
}

export interface MediaAssetLineage {
  assetId: string;
  parents: LineageEdge[];
  children: LineageEdge[];
}

export function getAssetRelations(id: string): Promise<MediaAssetLineage> {
  return api.get<MediaAssetLineage>(`/media-assets/${id}/relations`).then((res) => res.data);
}

export function createAssetRelation(
  id: string,
  payload: { childAssetId: string; relationType: RelationType },
): Promise<MediaAssetLineage> {
  return api.post<MediaAssetLineage>(`/media-assets/${id}/relations`, payload).then((res) => res.data);
}

/* ===== UC-2.2 Trash (soft-deleted media) ===== */

export interface TrashItem {
  asset: {
    id: string;
    assetCode: string;
    fileName: string;
    title?: string | null;
    storageUrl: string;
    fileType: string;
  };
  deletedAt: string;
  deletedByUserId: string | null;
  purgeDueAt: string | null;
  daysUntilPurge: number;
}

export function getTrash(institutionId?: string | null, signal?: AbortSignal): Promise<TrashItem[]> {
  return api
    .get<TrashItem[]>("/media-assets/trash", {
      params: { ...(institutionId ? { institutionId } : {}) },
      signal,
    })
    .then((res) => res.data);
}

/** Restore a soft-deleted asset within the retention window (lossless). */
export function restoreMediaAsset(id: string): Promise<void> {
  return api.post(`/media-assets/${id}/restore`).then(() => undefined);
}

/** "Delete forever" — purge the asset's content/derived data immediately. */
export function purgeMediaAsset(id: string): Promise<void> {
  return api.post(`/media-assets/${id}/purge`).then(() => undefined);
}

/* ===== UC-4.12 Phase 7C: Repository Health ===== */

export interface RepositoryHealth {
  scope: "institution" | "network";
  institutionId: string | null;
  generatedAt: string;
  totals: { activeAssets: number; storageBytes: number; readyAssets: number };
  integrity: {
    checksumCovered: number;
    checksumCoveragePct: number;
    verified: number;
    pending: number;
    mismatch: number;
    missing: number;
    error: number;
    failuresTotal: number;
    reviewOpen: number;
    reviewAcknowledged: number;
  };
  processing: { failed: number; processing: number };
  curation: { curated: number; uncurated: number; completenessPct: number; unorganized: number };
  governance: { internalOnly: number; duplicateCandidates: number };
  retention: { pendingPurge: number; approachingPurge: number };
}

/**
 * Tenant-scoped Repository Health aggregates. Validators get their own institution;
 * admins get the network, or one institution when institutionId is supplied.
 */
export function getRepositoryHealth(
  params?: { institutionId?: string | null },
  signal?: AbortSignal,
): Promise<RepositoryHealth> {
  const institutionId = params?.institutionId ?? undefined;
  return api
    .get<RepositoryHealth>("/media-repository/health", {
      params: { ...(institutionId ? { institutionId } : {}) },
      signal,
    })
    .then((res) => res.data);
}

/**
 * UC-4.12 Phase 7G: download a portable Dublin Core-aligned inventory export (csv|json).
 * Returns the blob plus the server-provided filename.
 */
export function exportRepository(
  format: "csv" | "json",
  institutionId?: string | null,
): Promise<{ blob: Blob; filename: string }> {
  return api
    .get("/media-repository/export", {
      params: { format, ...(institutionId ? { institutionId } : {}) },
      responseType: "blob",
    })
    .then((res) => {
      const disposition = (res.headers as Record<string, string>)["content-disposition"] ?? "";
      const match = disposition.match(/filename="?([^"]+)"?/);
      return { blob: res.data as Blob, filename: match?.[1] ?? `media-inventory.${format}` };
    });
}

export function deleteMediaAsset(id: string, force = false) {
  return api.delete<void>(`/media-assets/${id}`, { params: force ? { force: true } : undefined });
}

export interface BulkDeleteSkippedAsset {
  assetId: string;
  assetCode: string | null;
  reason: string;
  message: string;
}

export interface BulkDeleteResult {
  deletedIds: string[];
  deletedCount: number;
  skipped: BulkDeleteSkippedAsset[];
  skippedCount: number;
}

/**
 * Resilient bulk delete: the server deletes what it can and reports the rest in `skipped`
 * (e.g. referenced by an active submission). A partial result is still a 200.
 */
export function bulkDeleteMediaAssets(assetIds: string[], force = false) {
  return api.post<BulkDeleteResult>("/media-assets/bulk-delete", { assetIds, force });
}

export interface BulkOperationResult {
  affected: number;
}

/** Bulk-assign assets to a folder, or unfile them by passing null (UC-4.1). */
export function bulkMoveAssets(assetIds: string[], folderId: string | null) {
  return api
    .post<BulkOperationResult>("/media-assets/bulk-move", { assetIds, folderId })
    .then((res) => res.data);
}

/** Bulk-add a single manual tag to many assets (UC-4.1). */
export function bulkTagAssets(assetIds: string[], label: string) {
  return api
    .post<BulkOperationResult>("/media-assets/bulk-tag", { assetIds, label })
    .then((res) => res.data);
}

/** Upload-time exact-duplicate (SHA-256) hit: an existing asset already holds these bytes. */
export interface MediaDuplicateMatch {
  sha256: string;
  existingAsset: {
    id: string;
    assetCode: string;
    fileName: string;
    title?: string | null;
    storageUrl: string;
    fileType: string;
  };
}

/** Ask whether any of the supplied file hashes already exist in the target institution. */
export function checkMediaDuplicates(
  sha256s: string[],
  institutionId?: string | null,
): Promise<MediaDuplicateMatch[]> {
  return api
    .post<MediaDuplicateMatch[]>("/media-assets/check-duplicates", {
      sha256s,
      ...(institutionId ? { institutionId } : {}),
    })
    .then((res) => res.data);
}

export function getMediaAssetUploadUrl(payload: MediaAssetUploadUrlRequest) {
  return api.post<MediaAssetUploadUrlResponse>("/media-assets/upload-url", payload);
}

export function registerMediaAsset(payload: MediaAssetRegisterRequest) {
  return api.post<MediaAsset>("/media-assets/upload", payload);
}

export function createMediaImportBatch(payload: MediaImportBatchRequest) {
  return api.post<MediaImportBatch>("/media-assets/import-batches", payload);
}

export function listMediaImportBatches(institutionId?: string | null) {
  return api
    .get<MediaImportBatch[]>("/media-assets/import-batches", {
      params: institutionId ? { institutionId } : undefined,
    })
    .then((res) => res.data);
}

export function listImportBatchAssets(importBatchId: string, institutionId?: string | null) {
  return api
    .get<MediaAssetDetailResponse[]>(`/media-assets/import-batches/${importBatchId}/assets`, {
      params: institutionId ? { institutionId } : undefined,
    })
    .then((res) => res.data);
}

/** UC-4.2: delete a batch grouping. Un-groups the batch's assets — the media itself is kept. */
export function deleteMediaImportBatch(importBatchId: string, institutionId?: string | null) {
  return api.delete<void>(`/media-assets/import-batches/${importBatchId}`, {
    params: institutionId ? { institutionId } : undefined,
  });
}

export function markImportBatchCurated(
  importBatchId: string,
  institutionId?: string | null,
  edits?: MediaAssetCurationEdit[],
) {
  return api
    .post<{ curatedCount: number }>(
      `/media-assets/import-batches/${importBatchId}/curate`,
      edits ? { edits } : null,
      { params: institutionId ? { institutionId } : undefined },
    )
    .then((res) => res.data);
}
