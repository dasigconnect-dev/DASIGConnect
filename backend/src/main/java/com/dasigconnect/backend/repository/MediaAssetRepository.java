package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    @Query("SELECT m FROM MediaAsset m WHERE m.id = :id AND m.deletedAt IS NULL")
    Optional<MediaAsset> findActiveById(@Param("id") UUID id);

    @Query("SELECT m FROM MediaAsset m WHERE m.institution.id = :institutionId AND m.deletedAt IS NULL ORDER BY m.createdAt DESC")
    List<MediaAsset> findActiveByInstitution(@Param("institutionId") UUID institutionId);

    @Query(value = """
        SELECT * FROM media_assets
        WHERE institution_id = :institutionId
          AND deleted_at IS NULL
          AND status = 'READY'
        ORDER BY created_at DESC
        """, nativeQuery = true)
    List<MediaAsset> findReadyByInstitution(@Param("institutionId") UUID institutionId);

    @Query("SELECT m FROM MediaAsset m WHERE m.deletedAt IS NULL ORDER BY m.createdAt DESC")
    List<MediaAsset> findAllActive();

    boolean existsByAssetCode(String assetCode);
    boolean existsByUploaderId(UUID uploaderId);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE media_assets
        SET embedding = CAST(:embedding AS vector),
            embedding_generated_at = NOW(),
            embedding_model = :embeddingModel,
            status = 'READY'
        WHERE id = :id
        """, nativeQuery = true)
    void updateEmbedding(@Param("id") UUID id,
                         @Param("embedding") String embeddingJson,
                         @Param("embeddingModel") String embeddingModel);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE media_assets
        SET ai_category = :category,
            ai_confidence = :confidence,
            ai_description = :description,
            ai_classified_at = NOW(),
            ai_classification_model = :classificationModel
        WHERE id = :id
        """, nativeQuery = true)
    void updateClassification(@Param("id") UUID id,
                              @Param("category") String category,
                              @Param("confidence") double confidence,
                              @Param("description") String description,
                              @Param("classificationModel") String classificationModel);

    @Query(value = """
        SELECT * FROM media_assets
        WHERE institution_id = :institutionId
          AND deleted_at IS NULL
          AND status = 'READY'
          AND embedding IS NOT NULL
        ORDER BY embedding <=> CAST(:queryVector AS vector)
        LIMIT 5
        """, nativeQuery = true)
    List<MediaAsset> findTopSimilar(@Param("institutionId") UUID institutionId, @Param("queryVector") String queryVectorJson);

    @Query(value = "SELECT embedding::text FROM media_assets WHERE id = :id AND deleted_at IS NULL AND status = 'READY' AND embedding IS NOT NULL", nativeQuery = true)
    Optional<String> findEmbeddingById(@Param("id") UUID id);

    @Query(value = """
        SELECT * FROM media_assets
        WHERE deleted_at IS NULL
          AND (status IS NULL OR status IN ('PROCESSING', 'FAILED') OR embedding IS NULL)
        LIMIT 10
        """, nativeQuery = true)
    List<MediaAsset> findNeedingEmbedding();

    /** Returns id + cosine similarity score for top nearest neighbours. */
    @Query(value = """
        SELECT CAST(id AS text), 1 - (embedding <=> CAST(:queryVector AS vector)) AS score
        FROM media_assets
        WHERE institution_id = :institutionId
          AND deleted_at IS NULL
          AND status = 'READY'
          AND embedding IS NOT NULL
        ORDER BY embedding <=> CAST(:queryVector AS vector)
        LIMIT 30
        """, nativeQuery = true)
    List<Object[]> findTopSimilarWithScore(@Param("institutionId") UUID institutionId,
                                            @Param("queryVector") String queryVectorJson);

    @Query("SELECT m FROM MediaAsset m WHERE m.id IN :ids AND m.deletedAt IS NULL")
    List<MediaAsset> findActiveByIds(@Param("ids") List<UUID> ids);

    @Modifying
    @Transactional
    @Query(value = "UPDATE media_assets SET status = :status WHERE id = :id", nativeQuery = true)
    void updateStatus(@Param("id") UUID id, @Param("status") String status);

    @Query(value = """
        SELECT * FROM media_assets
        WHERE deleted_at IS NOT NULL
          AND purged_at IS NULL
          AND deleted_at < :cutoff
        ORDER BY deleted_at ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<MediaAsset> findDeletedReadyForPurge(@Param("cutoff") java.time.Instant cutoff,
                                               @Param("limit") int limit);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE media_assets
        SET embedding = NULL,
            embedding_generated_at = NULL,
            embedding_model = NULL,
            ai_category = NULL,
            ai_confidence = NULL,
            ai_description = NULL,
            asset_type = NULL,
            visible_objects = NULL,
            specific_subjects = NULL,
            visual_style = NULL,
            dominant_colors = NULL,
            possible_use_cases = NULL,
            ai_tags = NULL,
            excluded_categories = NULL,
            ai_classified_at = NULL,
            ai_classification_model = NULL,
            reclassified_at = NULL,
            purged_at = NOW()
        WHERE id = :id
        """, nativeQuery = true)
    void purgeAiProfile(@Param("id") UUID id);
}
