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

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO media_asset_embeddings
            (asset_id, embedding_type, embedding, model, source_input_hash, source_revision, processing_version)
        VALUES (:assetId, :embeddingType, CAST(:embedding AS vector), :model, :sourceInputHash, :sourceRevision, :processingVersion)
        ON CONFLICT (asset_id, embedding_type)
        DO UPDATE SET embedding = EXCLUDED.embedding, model = EXCLUDED.model,
          source_input_hash = EXCLUDED.source_input_hash,
          source_revision = EXCLUDED.source_revision,
          processing_version = EXCLUDED.processing_version, created_at = NOW()
        """, nativeQuery = true)
    void upsertVersioned(@Param("assetId") UUID assetId,
                         @Param("embeddingType") String embeddingType,
                         @Param("embedding") String embeddingJson,
                         @Param("model") String model,
                         @Param("sourceInputHash") String sourceInputHash,
                         @Param("sourceRevision") long sourceRevision,
                         @Param("processingVersion") String processingVersion);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO media_asset_embeddings
            (asset_id, embedding_type, embedding, model, source_input_hash, source_revision, processing_version)
        SELECT :assetId, 'semantic', CAST(:embedding AS vector), :model, :sourceInputHash,
               :semanticRevision, :processingVersion
        WHERE EXISTS (
            SELECT 1 FROM media_assets
            WHERE id = :assetId AND deleted_at IS NULL AND semantic_revision = :semanticRevision
        )
        ON CONFLICT (asset_id, embedding_type)
        DO UPDATE SET embedding = EXCLUDED.embedding, model = EXCLUDED.model,
          source_input_hash = EXCLUDED.source_input_hash,
          source_revision = EXCLUDED.source_revision,
          processing_version = EXCLUDED.processing_version, created_at = NOW()
        WHERE EXISTS (
            SELECT 1 FROM media_assets
            WHERE id = :assetId AND deleted_at IS NULL AND semantic_revision = :semanticRevision
        )
        """, nativeQuery = true)
    int upsertSemanticIfCurrent(@Param("assetId") UUID assetId,
                                @Param("embedding") String embeddingJson,
                                @Param("model") String model,
                                @Param("sourceInputHash") String sourceInputHash,
                                @Param("processingVersion") String processingVersion,
                                @Param("semanticRevision") long semanticRevision);

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

    default boolean existsCurrentEmbedding(UUID assetId, MediaAssetEmbeddingType type, String model) {
        return existsCurrentEmbedding(assetId, type.dbValue(), model);
    }

    @Query(value = """
        SELECT EXISTS (
            SELECT 1
            FROM media_asset_embeddings
            WHERE asset_id = :assetId
              AND embedding_type = :embeddingType
              AND model = :model
        )
        """, nativeQuery = true)
    boolean existsCurrentEmbedding(@Param("assetId") UUID assetId,
                                   @Param("embeddingType") String embeddingType,
                                   @Param("model") String model);

    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM media_asset_embeddings
            WHERE asset_id = :assetId AND embedding_type = :embeddingType
              AND model = :model AND source_input_hash = :sourceInputHash
              AND source_revision = :sourceRevision
              AND processing_version = :processingVersion
        )
        """, nativeQuery = true)
    boolean existsCurrentVersionedEmbedding(@Param("assetId") UUID assetId,
                                             @Param("embeddingType") String embeddingType,
                                             @Param("model") String model,
                                             @Param("sourceInputHash") String sourceInputHash,
                                             @Param("sourceRevision") long sourceRevision,
                                             @Param("processingVersion") String processingVersion);

    default long countEmbeddingsForAssets(List<UUID> assetIds, MediaAssetEmbeddingType type) {
        return countCurrentEmbeddingsForAssets(
                assetIds,
                type.dbValue(),
                type == MediaAssetEmbeddingType.IMAGE
                        ? "voyage-multimodal-3.5" : "voyage-4-lite",
                type == MediaAssetEmbeddingType.IMAGE
                        ? "image-embedding-v1" : "semantic-embedding-v1");
    }

    @Query(value = """
        SELECT COUNT(DISTINCT asset_id)
        FROM media_asset_embeddings
        WHERE asset_id IN (:assetIds)
          AND embedding_type = :embeddingType
        """, nativeQuery = true)
    long countEmbeddingsForAssets(@Param("assetIds") List<UUID> assetIds,
                                  @Param("embeddingType") String embeddingType);

    @Query(value = """
        SELECT COUNT(DISTINCT mae.asset_id)
        FROM media_asset_embeddings mae
        JOIN media_assets ma ON ma.id = mae.asset_id
        WHERE mae.asset_id IN (:assetIds)
          AND mae.embedding_type = :embeddingType
          AND mae.model = :model
          AND mae.processing_version = :processingVersion
          AND (
              (:embeddingType = 'image'
                  AND mae.source_revision = 0
                  AND mae.source_input_hash = encode(
                      digest(COALESCE(BTRIM(ma.storage_url), ''), 'sha256'), 'hex'))
              OR
              (:embeddingType = 'semantic'
                  AND mae.source_revision = ma.semantic_revision)
          )
        """, nativeQuery = true)
    long countCurrentEmbeddingsForAssets(@Param("assetIds") List<UUID> assetIds,
                                          @Param("embeddingType") String embeddingType,
                                          @Param("model") String model,
                                          @Param("processingVersion") String processingVersion);

    default List<Object[]> findTopSimilarWithScore(UUID institutionId, MediaAssetEmbeddingType type,
                                                   String queryVectorJson, String model,
                                                   String processingVersion, int limit) {
        return findTopSimilarWithScore(institutionId, type.dbValue(), queryVectorJson,
                model, processingVersion, limit);
    }

    default List<Object[]> findTopSimilarWithScore(UUID institutionId, MediaAssetEmbeddingType type,
                                                   String queryVectorJson, int limit) {
        return findTopSimilarWithScore(
                institutionId,
                type,
                queryVectorJson,
                type == MediaAssetEmbeddingType.IMAGE
                        ? "voyage-multimodal-3.5" : "voyage-4-lite",
                type == MediaAssetEmbeddingType.IMAGE
                        ? "image-embedding-v1" : "semantic-embedding-v1",
                limit);
    }

    @Query(value = """
        SELECT CAST(mae.asset_id AS text), 1 - (mae.embedding <=> CAST(:queryVector AS vector)) AS score
        FROM media_asset_embeddings mae
        JOIN media_assets ma ON ma.id = mae.asset_id
        WHERE ma.institution_id = :institutionId
          AND ma.deleted_at IS NULL
          AND ma.status = 'READY'
          AND COALESCE(LOWER(ma.temporal_classification), '') <> 'expired'
          AND mae.embedding_type = :embeddingType
          AND mae.model = :model
          AND mae.processing_version = :processingVersion
          AND (
              (:embeddingType = 'image'
                  AND mae.source_revision = 0
                  AND mae.source_input_hash = encode(
                      digest(COALESCE(BTRIM(ma.storage_url), ''), 'sha256'), 'hex'))
              OR
              (:embeddingType = 'semantic'
                  AND mae.source_revision = ma.semantic_revision)
          )
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
                                           @Param("model") String model,
                                           @Param("processingVersion") String processingVersion,
                                           @Param("limit") int limit);

    /**
     * Finds candidates similar to the complete selected asset set for one
     * embedding type. Query assets must belong to the authorized submission;
     * this permits embedded STAGED draft uploads while keeping them out of the
     * candidate pool. Each selected asset performs an indexed nearest-neighbour
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
              AND mae.model = :model
              AND mae.processing_version = :processingVersion
              AND query_asset.deleted_at IS NULL
              AND query_asset.file_type IN ('jpeg', 'png', 'webp', 'gif')
              AND (
                  (:embeddingType = 'image'
                      AND mae.source_revision = 0
                      AND mae.source_input_hash = encode(
                          digest(COALESCE(BTRIM(query_asset.storage_url), ''), 'sha256'), 'hex'))
                  OR
                  (:embeddingType = 'semantic'
                      AND mae.source_revision = query_asset.semantic_revision)
              )
              AND EXISTS (
                  SELECT 1
                  FROM submission_media_assets selected_link
                  WHERE selected_link.media_asset_id = query_asset.id
                    AND selected_link.submission_id = :submissionId
              )
              AND (
                  query_asset.institution_id = :institutionId
                  OR (
                      query_asset.institution_id IS NULL
                      AND query_asset.status = 'STAGED'
                  )
              )
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
                  AND COALESCE(LOWER(candidate_asset.temporal_classification), '') <> 'expired'
                  AND candidate_embedding.embedding_type = :embeddingType
                  AND candidate_embedding.model = :model
                  AND candidate_embedding.processing_version = :processingVersion
                  AND (
                      (:embeddingType = 'image'
                          AND candidate_embedding.source_revision = 0
                          AND candidate_embedding.source_input_hash = encode(
                              digest(COALESCE(BTRIM(candidate_asset.storage_url), ''), 'sha256'), 'hex'))
                      OR
                      (:embeddingType = 'semantic'
                          AND candidate_embedding.source_revision = candidate_asset.semantic_revision)
                  )
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
               (0.625 * MAX(score)) + (0.375 * AVG(score)) AS similarity_score,
               COUNT(DISTINCT query_asset_id)::double precision
                   / NULLIF((SELECT COUNT(*) FROM query_embeddings), 0) AS coverage_score
        FROM nearest
        GROUP BY asset_id
        ORDER BY similarity_score DESC
        LIMIT :resultLimit
        """, nativeQuery = true)
    List<Object[]> findTopSimilarToAssetsWithScore(@Param("institutionId") UUID institutionId,
                                                    @Param("submissionId") UUID submissionId,
                                                    @Param("embeddingType") String embeddingType,
                                                    @Param("queryAssetIds") List<UUID> queryAssetIds,
                                                    @Param("model") String model,
                                                    @Param("processingVersion") String processingVersion,
                                                    @Param("perImageLimit") int perImageLimit,
                                                    @Param("resultLimit") int resultLimit);

    default List<Object[]> findTopSimilarToAssetsWithScore(UUID institutionId,
                                                            UUID submissionId,
                                                            MediaAssetEmbeddingType embeddingType,
                                                            List<UUID> queryAssetIds,
                                                            String model,
                                                            String processingVersion,
                                                            int perImageLimit,
                                                            int resultLimit) {
        return findTopSimilarToAssetsWithScore(
                institutionId, submissionId, embeddingType.dbValue(), queryAssetIds,
                model, processingVersion, perImageLimit, resultLimit);
    }

    default List<Object[]> findTopSimilarToAssetsWithScore(UUID institutionId,
                                                            UUID submissionId,
                                                            MediaAssetEmbeddingType embeddingType,
                                                            List<UUID> queryAssetIds,
                                                            int perImageLimit,
                                                            int resultLimit) {
        return findTopSimilarToAssetsWithScore(
                institutionId,
                submissionId,
                embeddingType,
                queryAssetIds,
                embeddingType == MediaAssetEmbeddingType.IMAGE
                        ? "voyage-multimodal-3.5" : "voyage-4-lite",
                embeddingType == MediaAssetEmbeddingType.IMAGE
                        ? "image-embedding-v1" : "semantic-embedding-v1",
                perImageLimit,
                resultLimit);
    }

    @Query(value = """
        SELECT COUNT(DISTINCT mae.asset_id)
        FROM media_asset_embeddings mae
        JOIN media_assets ma ON ma.id = mae.asset_id
        WHERE ma.institution_id = :institutionId
          AND ma.deleted_at IS NULL
          AND ma.status = 'READY'
          AND COALESCE(LOWER(ma.temporal_classification), '') <> 'expired'
          AND mae.embedding_type = :embeddingType
          AND mae.model = :model
          AND mae.processing_version = :processingVersion
          AND (
              (:embeddingType = 'image'
                  AND mae.source_revision = 0
                  AND mae.source_input_hash = encode(
                      digest(COALESCE(BTRIM(ma.storage_url), ''), 'sha256'), 'hex'))
              OR
              (:embeddingType = 'semantic'
                  AND mae.source_revision = ma.semantic_revision)
          )
          AND (
              NOT EXISTS (
                  SELECT 1 FROM submission_media_assets any_link
                  WHERE any_link.media_asset_id = ma.id
              )
              OR EXISTS (
                  SELECT 1
                  FROM submission_media_assets visible_link
                  JOIN submissions visible_submission
                    ON visible_submission.id = visible_link.submission_id
                  WHERE visible_link.media_asset_id = ma.id
                    AND visible_submission.status <> 'draft'
              )
          )
        """, nativeQuery = true)
    long countCurrentReadyCandidates(@Param("institutionId") UUID institutionId,
                                     @Param("embeddingType") String embeddingType,
                                     @Param("model") String model,
                                     @Param("processingVersion") String processingVersion);

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
          AND COALESCE(LOWER(ma.temporal_classification), '') <> 'expired'
          AND mae.embedding_type = :embeddingType
        GROUP BY ma.media_album_id
        """, nativeQuery = true)
    List<Object[]> findMaxSimilarityByRootAlbum(@Param("institutionId") UUID institutionId,
                                                @Param("embeddingType") String embeddingType,
                                                @Param("queryVector") String queryVectorJson);
}
