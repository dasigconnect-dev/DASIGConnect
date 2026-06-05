package com.dasigconnect.backend.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MediaAssetSearchRepository {

    private static final UUID NETWORK_SCOPE_SENTINEL = new UUID(0L, 0L);

    @PersistenceContext
    private EntityManager entityManager;

    public List<MediaAssetSearchHit> searchLexical(String queryText,
                                                   UUID institutionId,
                                                   boolean networkScope,
                                                   String mediaType,
                                                   int limit,
                                                   int offset) {
        Query query = entityManager.createNativeQuery("""
            WITH q AS (
                SELECT websearch_to_tsquery('simple', :queryText) AS tsq
            ),
            matched AS (
                SELECT
                    d.asset_id,
                    (
                        ts_rank_cd(d.fts, q.tsq, 32)
                        + CASE WHEN lower(d.search_text) LIKE :likePattern THEN 0.15 ELSE 0 END
                    ) AS lexical_score,
                    ma.created_at
                FROM media_asset_search_documents d
                JOIN media_assets ma ON ma.id = d.asset_id
                CROSS JOIN q
                WHERE ma.deleted_at IS NULL
                  AND (:networkScope = TRUE OR d.institution_id = :institutionId)
                  AND (
                      CAST(:mediaType AS text) IS NULL
                      OR (CAST(:mediaType AS text) = 'image' AND ma.file_type IN ('jpeg', 'png', 'webp', 'gif'))
                      OR (CAST(:mediaType AS text) = 'video' AND ma.file_type IN ('mp4', 'mov', 'webm'))
                  )
                  AND (d.fts @@ q.tsq OR lower(d.search_text) LIKE :likePattern)
            ),
            ranked AS (
                SELECT
                    asset_id,
                    lexical_score,
                    row_number() OVER (ORDER BY lexical_score DESC, created_at DESC) AS lexical_rank
                FROM matched
            )
            SELECT CAST(asset_id AS text), lexical_score, lexical_rank
            FROM ranked
            ORDER BY lexical_rank ASC
            LIMIT :limit OFFSET :offset
            """);
        bind(query, queryText, institutionId, networkScope, mediaType);
        query.setParameter("limit", limit);
        query.setParameter("offset", offset);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new MediaAssetSearchHit(
                        UUID.fromString((String) row[0]),
                        ((Number) row[1]).doubleValue(),
                        ((Number) row[2]).intValue()))
                .toList();
    }

    public int countLexical(String queryText,
                            UUID institutionId,
                            boolean networkScope,
                            String mediaType) {
        Query query = entityManager.createNativeQuery("""
            WITH q AS (
                SELECT websearch_to_tsquery('simple', :queryText) AS tsq
            )
            SELECT COUNT(*)
            FROM media_asset_search_documents d
            JOIN media_assets ma ON ma.id = d.asset_id
            CROSS JOIN q
            WHERE ma.deleted_at IS NULL
              AND (:networkScope = TRUE OR d.institution_id = :institutionId)
              AND (
                  CAST(:mediaType AS text) IS NULL
                  OR (CAST(:mediaType AS text) = 'image' AND ma.file_type IN ('jpeg', 'png', 'webp', 'gif'))
                  OR (CAST(:mediaType AS text) = 'video' AND ma.file_type IN ('mp4', 'mov', 'webm'))
              )
              AND (d.fts @@ q.tsq OR lower(d.search_text) LIKE :likePattern)
            """);
        bind(query, queryText, institutionId, networkScope, mediaType);
        return ((Number) query.getSingleResult()).intValue();
    }

    /**
     * UC-4.5 temporal path: assets in a half-open created_at range [from, to),
     * newest first, scoped by tenant. Used when a query is purely a date
     * ("photos uploaded on June 1") so results are not relevance-ranked.
     */
    @SuppressWarnings("unchecked")
    public List<UUID> findIdsByDateRange(UUID institutionId,
                                         boolean networkScope,
                                         String mediaType,
                                         OffsetDateTime from,
                                         OffsetDateTime to,
                                         int limit,
                                         int offset) {
        Query query = entityManager.createNativeQuery("""
            SELECT CAST(ma.id AS text)
            FROM media_assets ma
            WHERE ma.deleted_at IS NULL
              AND ma.status = 'READY'
              AND (:networkScope = TRUE OR ma.institution_id = :institutionId)
              AND ma.created_at >= :fromTs
              AND ma.created_at < :toTs
              AND (
                  CAST(:mediaType AS text) IS NULL
                  OR (CAST(:mediaType AS text) = 'image' AND ma.file_type IN ('jpeg', 'png', 'webp', 'gif'))
                  OR (CAST(:mediaType AS text) = 'video' AND ma.file_type IN ('mp4', 'mov', 'webm'))
              )
            ORDER BY ma.created_at DESC
            LIMIT :limit OFFSET :offset
            """);
        bindDateRange(query, institutionId, networkScope, mediaType, from, to);
        query.setParameter("limit", limit);
        query.setParameter("offset", offset);
        List<String> ids = query.getResultList();
        return ids.stream().map(UUID::fromString).toList();
    }

    public int countByDateRange(UUID institutionId,
                                boolean networkScope,
                                String mediaType,
                                OffsetDateTime from,
                                OffsetDateTime to) {
        Query query = entityManager.createNativeQuery("""
            SELECT COUNT(*)
            FROM media_assets ma
            WHERE ma.deleted_at IS NULL
              AND ma.status = 'READY'
              AND (:networkScope = TRUE OR ma.institution_id = :institutionId)
              AND ma.created_at >= :fromTs
              AND ma.created_at < :toTs
              AND (
                  CAST(:mediaType AS text) IS NULL
                  OR (CAST(:mediaType AS text) = 'image' AND ma.file_type IN ('jpeg', 'png', 'webp', 'gif'))
                  OR (CAST(:mediaType AS text) = 'video' AND ma.file_type IN ('mp4', 'mov', 'webm'))
              )
            """);
        bindDateRange(query, institutionId, networkScope, mediaType, from, to);
        return ((Number) query.getSingleResult()).intValue();
    }

    /**
     * UC-4.5 autocomplete: distinct related-search candidates across real, tenant-scoped data
     * (AI/manual tags, AI category, filenames, uploader emails, collection and folder names).
     * Returns rows of {text, type, rank} where rank is 0 = exact, 1 = starts-with, 2 = contains.
     * Final de-duplication across types is done in the service.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> suggest(String normalizedQuery, UUID institutionId, boolean networkScope, int limit) {
        Query query = entityManager.createNativeQuery("""
            SELECT text, type, MIN(rank) AS rank
            FROM (
                SELECT t.label AS text, 'tag' AS type,
                       CASE WHEN lower(t.label) = :q THEN 0 WHEN lower(t.label) LIKE :prefix THEN 1 ELSE 2 END AS rank
                FROM asset_tags t
                JOIN media_assets ma ON ma.id = t.media_asset_id
                WHERE ma.deleted_at IS NULL
                  AND (:networkScope = TRUE OR ma.institution_id = :institutionId)
                  AND lower(t.label) LIKE :like

                UNION ALL
                SELECT ma.ai_category AS text, 'tag' AS type,
                       CASE WHEN lower(ma.ai_category) = :q THEN 0 WHEN lower(ma.ai_category) LIKE :prefix THEN 1 ELSE 2 END
                FROM media_assets ma
                WHERE ma.deleted_at IS NULL AND ma.ai_category IS NOT NULL
                  AND (:networkScope = TRUE OR ma.institution_id = :institutionId)
                  AND lower(ma.ai_category) LIKE :like

                UNION ALL
                SELECT ma.file_name AS text, 'file' AS type,
                       CASE WHEN lower(ma.file_name) = :q THEN 0 WHEN lower(ma.file_name) LIKE :prefix THEN 1 ELSE 2 END
                FROM media_assets ma
                WHERE ma.deleted_at IS NULL AND ma.file_name IS NOT NULL
                  AND (:networkScope = TRUE OR ma.institution_id = :institutionId)
                  AND lower(ma.file_name) LIKE :like

                UNION ALL
                SELECT u.email AS text, 'uploader' AS type,
                       CASE WHEN lower(u.email) = :q THEN 0 WHEN lower(u.email) LIKE :prefix THEN 1 ELSE 2 END
                FROM media_assets ma
                JOIN users u ON u.id = ma.uploader_id
                WHERE ma.deleted_at IS NULL
                  AND (:networkScope = TRUE OR ma.institution_id = :institutionId)
                  AND lower(u.email) LIKE :like

                UNION ALL
                SELECT al.name AS text, 'collection' AS type,
                       CASE WHEN lower(al.name) = :q THEN 0 WHEN lower(al.name) LIKE :prefix THEN 1 ELSE 2 END
                FROM media_albums al
                WHERE (:networkScope = TRUE OR al.institution_id = :institutionId)
                  AND lower(al.name) LIKE :like

                UNION ALL
                SELECT f.name AS text, 'folder' AS type,
                       CASE WHEN lower(f.name) = :q THEN 0 WHEN lower(f.name) LIKE :prefix THEN 1 ELSE 2 END
                FROM media_folders f
                WHERE (:networkScope = TRUE OR f.institution_id = :institutionId)
                  AND lower(f.name) LIKE :like
            ) candidates
            GROUP BY text, type
            ORDER BY rank ASC,
                     CASE type
                         WHEN 'tag' THEN 0
                         WHEN 'collection' THEN 1
                         WHEN 'folder' THEN 2
                         WHEN 'file' THEN 3
                         WHEN 'uploader' THEN 4
                         ELSE 5
                     END,
                     text ASC
            LIMIT :limit
            """);
        query.setParameter("q", normalizedQuery);
        query.setParameter("prefix", normalizedQuery + "%");
        query.setParameter("like", "%" + normalizedQuery + "%");
        query.setParameter("institutionId", institutionId == null ? NETWORK_SCOPE_SENTINEL : institutionId);
        query.setParameter("networkScope", networkScope);
        query.setParameter("limit", limit);
        return (List<Object[]>) query.getResultList();
    }

    private void bindDateRange(Query query, UUID institutionId, boolean networkScope, String mediaType,
                               OffsetDateTime from, OffsetDateTime to) {
        query.setParameter("institutionId", institutionId == null ? NETWORK_SCOPE_SENTINEL : institutionId);
        query.setParameter("networkScope", networkScope);
        query.setParameter("mediaType", mediaType);
        query.setParameter("fromTs", from);
        query.setParameter("toTs", to);
    }

    private void bind(Query query, String queryText, UUID institutionId, boolean networkScope, String mediaType) {
        query.setParameter("queryText", queryText);
        query.setParameter("institutionId", institutionId == null ? NETWORK_SCOPE_SENTINEL : institutionId);
        query.setParameter("networkScope", networkScope);
        query.setParameter("mediaType", mediaType);
        query.setParameter("likePattern", "%" + queryText.toLowerCase() + "%");
    }
}
