package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    @Query("SELECT m FROM MediaAsset m WHERE m.id = :id AND m.deletedAt IS NULL")
    Optional<MediaAsset> findActiveById(@Param("id") UUID id);

    @Query("""
        SELECT new com.dasigconnect.backend.repository.MediaIntegrityTarget(
            m.id, m.institution.id, m.storageUrl, m.assetCode
        )
        FROM MediaAsset m
        WHERE m.id = :id
          AND m.deletedAt IS NULL
        """)
    Optional<MediaIntegrityTarget> findIntegrityTargetById(@Param("id") UUID id);

    /**
     * System-job query. It is intentionally network-wide and returns only the bounded
     * projection needed for storage verification; user-facing reads remain tenant-scoped.
     */
    @Query("""
        SELECT new com.dasigconnect.backend.repository.MediaIntegrityTarget(
            m.id, m.institution.id, m.storageUrl, m.assetCode
        )
        FROM MediaAsset m
        WHERE m.deletedAt IS NULL
          AND (
              m.contentSha256 IS NULL
              OR m.integrityStatus = com.dasigconnect.backend.model.entity.MediaIntegrityStatus.PENDING
              OR m.integrityCheckedAt IS NULL
              OR (
                  m.integrityStatus IN (
                      com.dasigconnect.backend.model.entity.MediaIntegrityStatus.MISMATCH,
                      com.dasigconnect.backend.model.entity.MediaIntegrityStatus.MISSING,
                      com.dasigconnect.backend.model.entity.MediaIntegrityStatus.ERROR
                  )
                  AND m.integrityCheckedAt <= :failureCutoff
              )
              OR (
                  m.integrityStatus = com.dasigconnect.backend.model.entity.MediaIntegrityStatus.VERIFIED
                  AND m.integrityCheckedAt <= :verifiedCutoff
              )
          )
        ORDER BY
          CASE
              WHEN m.contentSha256 IS NULL
                OR m.integrityStatus = com.dasigconnect.backend.model.entity.MediaIntegrityStatus.PENDING
                OR m.integrityCheckedAt IS NULL THEN 0
              WHEN m.integrityStatus IN (
                  com.dasigconnect.backend.model.entity.MediaIntegrityStatus.MISMATCH,
                  com.dasigconnect.backend.model.entity.MediaIntegrityStatus.MISSING,
                  com.dasigconnect.backend.model.entity.MediaIntegrityStatus.ERROR
              ) THEN 1
              ELSE 2
          END,
          m.integrityCheckedAt ASC,
          m.createdAt ASC
        """)
    List<MediaIntegrityTarget> findDueIntegrityTargets(
            @Param("verifiedCutoff") java.time.Instant verifiedCutoff,
            @Param("failureCutoff") java.time.Instant failureCutoff,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MediaAsset m WHERE m.id = :id AND m.deletedAt IS NULL")
    Optional<MediaAsset> findActiveByIdForIntegrityUpdate(@Param("id") UUID id);

    @Query("SELECT COUNT(m) > 0 FROM MediaAsset m WHERE m.institution.id = :institutionId AND m.deletedAt IS NULL")
    boolean existsActiveByInstitutionId(@Param("institutionId") UUID institutionId);

    /**
     * UC-4.12 Phase 7C: one filtered aggregate scan for the Repository Health dashboard.
     * A null {@code institutionId} aggregates network-wide (administrator scope); otherwise
     * the scan is tenant-scoped. Aliases are quoted to preserve camelCase for the
     * {@link RepositoryHealthCounts} interface projection.
     */
    @Query(value = """
        SELECT
          COUNT(*) FILTER (WHERE deleted_at IS NULL AND purged_at IS NULL)                                   AS "activeAssets",
          COALESCE(SUM(file_size_bytes) FILTER (WHERE deleted_at IS NULL AND purged_at IS NULL), 0)::bigint  AS "storageBytes",
          COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'READY')                                    AS "readyAssets",
          COUNT(*) FILTER (WHERE deleted_at IS NULL AND content_sha256 IS NOT NULL)                          AS "checksumCovered",
          COUNT(*) FILTER (WHERE deleted_at IS NULL AND integrity_status = 'VERIFIED')                       AS "integrityVerified",
          COUNT(*) FILTER (WHERE deleted_at IS NULL AND integrity_status = 'PENDING')                        AS "integrityPending",
          COUNT(*) FILTER (WHERE deleted_at IS NULL AND integrity_status = 'MISMATCH')                       AS "integrityMismatch",
          COUNT(*) FILTER (WHERE deleted_at IS NULL AND integrity_status = 'MISSING')                        AS "integrityMissing",
          COUNT(*) FILTER (WHERE deleted_at IS NULL AND integrity_status = 'ERROR')                          AS "integrityError",
          COUNT(*) FILTER (WHERE deleted_at IS NULL AND integrity_review_status = 'OPEN')                    AS "reviewOpen",
          COUNT(*) FILTER (WHERE deleted_at IS NULL AND integrity_review_status = 'ACKNOWLEDGED')            AS "reviewAcknowledged",
          COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'FAILED')                                   AS "processingFailed",
          COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'PROCESSING')                               AS "processingPending",
          COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'READY' AND curated_at IS NOT NULL)         AS "metadataCurated",
          COUNT(*) FILTER (WHERE deleted_at IS NULL AND folder_id IS NULL)                                   AS "unorganized",
          COUNT(*) FILTER (WHERE deleted_at IS NULL AND visibility = 'internal_only')                        AS "internalOnly",
          COUNT(*) FILTER (WHERE deleted_at IS NULL AND duplicate_of_id IS NOT NULL)                         AS "duplicateCandidates",
          COUNT(*) FILTER (WHERE deleted_at IS NOT NULL AND purged_at IS NULL)                               AS "deletedPendingPurge",
          COUNT(*) FILTER (WHERE deleted_at IS NOT NULL AND purged_at IS NULL AND deleted_at <= :purgeSoonCutoff) AS "deletedApproachingPurge"
        FROM media_assets
        WHERE (CAST(:institutionId AS uuid) IS NULL OR institution_id = CAST(:institutionId AS uuid))
        """, nativeQuery = true)
    RepositoryHealthCounts aggregateRepositoryHealth(
            @Param("institutionId") UUID institutionId,
            @Param("purgeSoonCutoff") java.time.Instant purgeSoonCutoff);

    @Query("SELECT m FROM MediaAsset m WHERE m.institution.id = :institutionId AND m.deletedAt IS NULL ORDER BY m.createdAt DESC")
    List<MediaAsset> findActiveByInstitution(@Param("institutionId") UUID institutionId);

    /**
     * UC-4.12 Phase 7F: deterministic duplicate candidates (perceptual-hash flagged). A null
     * institution aggregates network-wide for administrators.
     */
    @Query("""
        SELECT m FROM MediaAsset m
        WHERE m.duplicateOfId IS NOT NULL
          AND m.deletedAt IS NULL
          AND (:institutionId IS NULL OR m.institution.id = :institutionId)
        ORDER BY m.createdAt DESC
        """)
    List<MediaAsset> findDuplicateCandidates(@Param("institutionId") UUID institutionId);

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
    Optional<MediaAsset> findByAssetCode(String assetCode);
    boolean existsByUploaderId(UUID uploaderId);
    long countByFolderIdAndDeletedAtIsNull(UUID folderId);

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

    @Query("""
        SELECT m FROM MediaAsset m
        JOIN FETCH m.institution
        JOIN FETCH m.uploader
        WHERE m.id IN :ids
          AND m.deletedAt IS NULL
        """)
    List<MediaAsset> findActiveByIdsWithInstitutionAndUploader(@Param("ids") List<UUID> ids);

    @Query("""
        SELECT m FROM MediaAsset m
        WHERE m.importBatchId = :importBatchId
          AND m.institution.id = :institutionId
          AND m.deletedAt IS NULL
        ORDER BY m.createdAt ASC
        """)
    List<MediaAsset> findActiveByImportBatch(@Param("importBatchId") UUID importBatchId,
                                             @Param("institutionId") UUID institutionId);

    @Query("""
        SELECT m FROM MediaAsset m
        WHERE m.institution.id = :institutionId
          AND m.id <> :assetId
          AND m.deletedAt IS NULL
          AND m.perceptualHash IS NOT NULL
        """)
    List<MediaAsset> findDuplicateHashCandidates(@Param("institutionId") UUID institutionId,
                                                 @Param("assetId") UUID assetId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE media_assets SET status = :status WHERE id = :id", nativeQuery = true)
    void updateStatus(@Param("id") UUID id, @Param("status") String status);

    @Modifying
    @Transactional
    @Query("UPDATE MediaAsset m SET m.folderId = :folderId "
            + "WHERE m.id IN :ids AND m.institution.id = :institutionId AND m.deletedAt IS NULL")
    int assignToFolder(@Param("ids") List<UUID> ids,
                       @Param("folderId") UUID folderId,
                       @Param("institutionId") UUID institutionId);

    @Modifying
    @Transactional
    @Query("UPDATE MediaAsset m SET m.folderId = NULL "
            + "WHERE m.id IN :ids AND m.institution.id = :institutionId AND m.deletedAt IS NULL")
    int unfileAssets(@Param("ids") List<UUID> ids, @Param("institutionId") UUID institutionId);

    /** UC-4.2: un-group a batch — clear import_batch_id on its assets (keeps the assets). */
    @Modifying
    @Transactional
    @Query("UPDATE MediaAsset m SET m.importBatchId = NULL "
            + "WHERE m.importBatchId = :batchId AND m.institution.id = :institutionId")
    int clearImportBatch(@Param("batchId") UUID batchId, @Param("institutionId") UUID institutionId);

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
