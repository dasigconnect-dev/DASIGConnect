package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.MediaAssetEmbedding;
import com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MediaAssetEmbeddingRepository extends JpaRepository<MediaAssetEmbedding, UUID> {

    default void upsert(UUID assetId, MediaAssetEmbeddingType type, String embeddingJson, String model) {
        upsert(assetId, type.dbValue(), embeddingJson, model);
    }

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO media_asset_embeddings (asset_id, embedding_type, embedding, model)
        VALUES (:assetId, :embeddingType, CAST(:embedding AS vector), :model)
        ON CONFLICT (asset_id, embedding_type)
        DO UPDATE SET
          embedding = EXCLUDED.embedding,
          model = EXCLUDED.model,
          created_at = NOW()
        """, nativeQuery = true)
    void upsert(@Param("assetId") UUID assetId,
                @Param("embeddingType") String embeddingType,
                @Param("embedding") String embeddingJson,
                @Param("model") String model);

    default Optional<String> findEmbedding(UUID assetId, MediaAssetEmbeddingType type) {
        return findEmbedding(assetId, type.dbValue());
    }

    @Query(value = """
        SELECT embedding::text
        FROM media_asset_embeddings
        WHERE asset_id = :assetId
          AND embedding_type = :embeddingType
        """, nativeQuery = true)
    Optional<String> findEmbedding(@Param("assetId") UUID assetId,
                                   @Param("embeddingType") String embeddingType);

    default List<Object[]> findTopSimilarWithScore(UUID institutionId, MediaAssetEmbeddingType type,
                                                   String queryVectorJson, int limit) {
        return findTopSimilarWithScore(institutionId, type.dbValue(), queryVectorJson, limit);
    }

    @Query(value = """
        SELECT CAST(mae.asset_id AS text), 1 - (mae.embedding <=> CAST(:queryVector AS vector)) AS score
        FROM media_asset_embeddings mae
        JOIN media_assets ma ON ma.id = mae.asset_id
        WHERE ma.institution_id = :institutionId
          AND ma.deleted_at IS NULL
          AND ma.status = 'READY'
          AND mae.embedding_type = :embeddingType
          AND (
              NOT EXISTS (
                  SELECT 1 FROM submission_media_assets any_link
                  WHERE any_link.media_asset_id = ma.id
              )
              OR EXISTS (
                  SELECT 1
                  FROM submission_media_assets visible_link
                  JOIN submissions visible_submission ON visible_submission.id = visible_link.submission_id
                  WHERE visible_link.media_asset_id = ma.id
                    AND visible_submission.status <> 'draft'
              )
          )
        ORDER BY mae.embedding <=> CAST(:queryVector AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findTopSimilarWithScore(@Param("institutionId") UUID institutionId,
                                           @Param("embeddingType") String embeddingType,
                                           @Param("queryVector") String queryVectorJson,
                                           @Param("limit") int limit);

    /**
     * Finds candidates similar to the complete selected asset set for one
     * embedding type. Each selected asset performs an indexed nearest-neighbour
     * lookup; the outer query combines those bounded result sets. This avoids
     * scanning the whole institution library or limiting context to the first
     * attached image.
     */
    @Query(value = """
        WITH query_embeddings AS (
            SELECT mae.asset_id, mae.embedding
            FROM media_asset_embeddings mae
            JOIN media_assets query_asset ON query_asset.id = mae.asset_id
            WHERE mae.asset_id IN (:queryAssetIds)
              AND mae.embedding_type = :embeddingType
              AND query_asset.institution_id = :institutionId
              AND query_asset.deleted_at IS NULL
              AND query_asset.status = 'READY'
        ), nearest AS (
            SELECT q.asset_id AS query_asset_id,
                   candidate.asset_id,
                   candidate.score
            FROM query_embeddings q
            CROSS JOIN LATERAL (
                SELECT candidate_embedding.asset_id,
                       1 - (candidate_embedding.embedding <=> q.embedding) AS score
                FROM media_asset_embeddings candidate_embedding
                JOIN media_assets candidate_asset ON candidate_asset.id = candidate_embedding.asset_id
                WHERE candidate_asset.institution_id = :institutionId
                  AND candidate_asset.deleted_at IS NULL
                  AND candidate_asset.status = 'READY'
                  AND candidate_embedding.embedding_type = :embeddingType
                  AND candidate_embedding.asset_id NOT IN (SELECT asset_id FROM query_embeddings)
                  AND (
                      NOT EXISTS (
                          SELECT 1 FROM submission_media_assets any_link
                          WHERE any_link.media_asset_id = candidate_asset.id
                      )
                      OR EXISTS (
                          SELECT 1
                          FROM submission_media_assets visible_link
                          JOIN submissions visible_submission
                            ON visible_submission.id = visible_link.submission_id
                          WHERE visible_link.media_asset_id = candidate_asset.id
                            AND visible_submission.status <> 'draft'
                      )
                  )
                ORDER BY candidate_embedding.embedding <=> q.embedding
                LIMIT :perImageLimit
            ) candidate
        )
        SELECT CAST(asset_id AS text),
               (0.50 * MAX(score))
               + (0.30 * AVG(score))
               + (0.20 * COUNT(DISTINCT query_asset_id)::double precision
                  / NULLIF((SELECT COUNT(*) FROM query_embeddings), 0)) AS score
        FROM nearest
        GROUP BY asset_id
        ORDER BY score DESC
        LIMIT :resultLimit
        """, nativeQuery = true)
    List<Object[]> findTopSimilarToAssetsWithScore(@Param("institutionId") UUID institutionId,
                                                    @Param("embeddingType") String embeddingType,
                                                    @Param("queryAssetIds") List<UUID> queryAssetIds,
                                                    @Param("perImageLimit") int perImageLimit,
                                                    @Param("resultLimit") int resultLimit);

    default List<Object[]> findTopSimilarToAssetsWithScore(UUID institutionId,
                                                            MediaAssetEmbeddingType embeddingType,
                                                            List<UUID> queryAssetIds,
                                                            int perImageLimit,
                                                            int resultLimit) {
        return findTopSimilarToAssetsWithScore(
                institutionId, embeddingType.dbValue(), queryAssetIds, perImageLimit, resultLimit);
    }

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM media_asset_embeddings WHERE asset_id = :assetId", nativeQuery = true)
    void deleteByAssetId(@Param("assetId") UUID assetId);

    default List<Object[]> findMaxSimilarityByRootAlbum(UUID institutionId, MediaAssetEmbeddingType type,
                                                         String queryVectorJson) {
        return findMaxSimilarityByRootAlbum(institutionId, type.dbValue(), queryVectorJson);
    }

    /**
     * Per-root-album best match: for every root album with at least one directly
     * filed, embedded asset, the highest cosine similarity between the query
     * vector and any of that album's assets. Used by album Auto-Match (UC-1.7) —
     * "closest asset in this album" rather than a centroid, so one strong visual
     * match is enough even in an album with otherwise-unrelated photos. Scoped to
     * root albums (parent_album_id IS NULL) because that's the only kind the
     * submission composer assigns.
     */
    @Query(value = """
        SELECT CAST(ma.media_album_id AS text), MAX(1 - (mae.embedding <=> CAST(:queryVector AS vector))) AS score
        FROM media_asset_embeddings mae
        JOIN media_assets ma ON ma.id = mae.asset_id
        JOIN media_albums al ON al.id = ma.media_album_id
        WHERE ma.institution_id = :institutionId
          AND al.parent_album_id IS NULL
          AND ma.deleted_at IS NULL
          AND mae.embedding_type = :embeddingType
        GROUP BY ma.media_album_id
        """, nativeQuery = true)
    List<Object[]> findMaxSimilarityByRootAlbum(@Param("institutionId") UUID institutionId,
                                                @Param("embeddingType") String embeddingType,
                                                @Param("queryVector") String queryVectorJson);
}
