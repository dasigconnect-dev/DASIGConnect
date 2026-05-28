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
        ORDER BY mae.embedding <=> CAST(:queryVector AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findTopSimilarWithScore(@Param("institutionId") UUID institutionId,
                                           @Param("embeddingType") String embeddingType,
                                           @Param("queryVector") String queryVectorJson,
                                           @Param("limit") int limit);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM media_asset_embeddings WHERE asset_id = :assetId", nativeQuery = true)
    void deleteByAssetId(@Param("assetId") UUID assetId);
}
