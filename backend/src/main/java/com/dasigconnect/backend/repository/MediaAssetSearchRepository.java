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
