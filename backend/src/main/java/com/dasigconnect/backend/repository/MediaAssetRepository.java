package com.dasigconnect.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    @Query("SELECT m FROM MediaAsset m WHERE m.id = :id AND m.deletedAt IS NULL")
    Optional<MediaAsset> findActiveById(@Param("id") UUID id);

    @Query("SELECT COUNT(m) > 0 FROM MediaAsset m WHERE m.institution.id = :institutionId AND m.deletedAt IS NULL")
    boolean existsActiveByInstitutionId(@Param("institutionId") UUID institutionId);

    @Query("SELECT m FROM MediaAsset m WHERE m.institution.id = :institutionId AND m.deletedAt IS NULL ORDER BY m.createdAt DESC")
    List<MediaAsset> findActiveByInstitution(@Param("institutionId") UUID institutionId);

    @Query("SELECT m FROM MediaAsset m WHERE m.institution.id IN :institutionIds AND m.deletedAt IS NULL ORDER BY m.createdAt DESC")
    List<MediaAsset> findActiveByInstitutionIds(@Param("institutionIds") java.util.Collection<UUID> institutionIds);

    @EntityGraph(attributePaths = {"institution", "uploader", "mediaAlbum"})
    @Query(value = """
        SELECT m FROM MediaAsset m
        WHERE m.deletedAt IS NULL
          AND m.status <> com.dasigconnect.backend.model.entity.MediaAssetStatus.STAGED
          AND (:networkWide = true OR m.institution.id IN :institutionIds)
          AND (
              NOT EXISTS (
                  SELECT sma.id FROM SubmissionMediaAsset sma
                  WHERE sma.mediaAsset = m
              )
              OR EXISTS (
                  SELECT publishedLink.id FROM SubmissionMediaAsset publishedLink
                  WHERE publishedLink.mediaAsset = m
                    AND publishedLink.submission.status <> com.dasigconnect.backend.model.entity.SubmissionStatus.draft
              )
          )
          AND (:albumId IS NULL OR m.mediaAlbum.id = :albumId)
          AND (
              :searchTerm = ''
              OR LOWER(m.fileName) LIKE CONCAT('%', :searchTerm, '%')
              OR LOWER(COALESCE(m.displayTitle, '')) LIKE CONCAT('%', :searchTerm, '%')
              OR LOWER(m.assetCode) LIKE CONCAT('%', :searchTerm, '%')
              OR LOWER(m.uploader.email) LIKE CONCAT('%', :searchTerm, '%')
              OR EXISTS (
                  SELECT tag.id FROM AssetTag tag
                  WHERE tag.mediaAsset = m
                    AND LOWER(tag.label) LIKE CONCAT('%', :searchTerm, '%')
              )
          )
          AND m.fileType IN :mediaTypes
          AND (:uploaderId IS NULL OR m.uploader.id = :uploaderId)
        """, countQuery = """
        SELECT COUNT(m) FROM MediaAsset m
        WHERE m.deletedAt IS NULL
          AND m.status <> com.dasigconnect.backend.model.entity.MediaAssetStatus.STAGED
          AND (:networkWide = true OR m.institution.id IN :institutionIds)
          AND (
              NOT EXISTS (
                  SELECT sma.id FROM SubmissionMediaAsset sma
                  WHERE sma.mediaAsset = m
              )
              OR EXISTS (
                  SELECT publishedLink.id FROM SubmissionMediaAsset publishedLink
                  WHERE publishedLink.mediaAsset = m
                    AND publishedLink.submission.status <> com.dasigconnect.backend.model.entity.SubmissionStatus.draft
              )
          )
          AND (:albumId IS NULL OR m.mediaAlbum.id = :albumId)
          AND (
              :searchTerm = ''
              OR LOWER(m.fileName) LIKE CONCAT('%', :searchTerm, '%')
              OR LOWER(COALESCE(m.displayTitle, '')) LIKE CONCAT('%', :searchTerm, '%')
              OR LOWER(m.assetCode) LIKE CONCAT('%', :searchTerm, '%')
              OR LOWER(m.uploader.email) LIKE CONCAT('%', :searchTerm, '%')
              OR EXISTS (
                  SELECT tag.id FROM AssetTag tag
                  WHERE tag.mediaAsset = m
                    AND LOWER(tag.label) LIKE CONCAT('%', :searchTerm, '%')
              )
          )
          AND m.fileType IN :mediaTypes
          AND (:uploaderId IS NULL OR m.uploader.id = :uploaderId)
        """)
    Page<MediaAsset> findRepositoryPage(
            @Param("networkWide") boolean networkWide,
            @Param("institutionIds") Collection<UUID> institutionIds,
            @Param("albumId") UUID albumId,
            @Param("searchTerm") String searchTerm,
            @Param("mediaTypes") Collection<MediaFileType> mediaTypes,
            @Param("uploaderId") UUID uploaderId,
            Pageable pageable);

    /**
     * Keyword-search fallback for {@code /media-assets/search}, filtered and
     * capped in SQL instead of loading every visible asset into memory first
     * (that used to be the semantic-search keyword path — a full table scan
     * into the JVM heap on every Enter-press, the single biggest Supabase
     * egress source once semantic search itself is already scoped by pgvector).
     */
    @EntityGraph(attributePaths = {"institution", "uploader", "mediaAlbum"})
    @Query("""
        SELECT m FROM MediaAsset m
        WHERE m.deletedAt IS NULL
          AND m.status <> com.dasigconnect.backend.model.entity.MediaAssetStatus.STAGED
          AND (:networkWide = true OR m.institution.id IN :institutionIds)
          AND m.id NOT IN :excludeIds
          AND (
              NOT EXISTS (
                  SELECT sma.id FROM SubmissionMediaAsset sma
                  WHERE sma.mediaAsset = m
              )
              OR EXISTS (
                  SELECT publishedLink.id FROM SubmissionMediaAsset publishedLink
                  WHERE publishedLink.mediaAsset = m
                    AND publishedLink.submission.status <> com.dasigconnect.backend.model.entity.SubmissionStatus.draft
              )
          )
          AND (
              LOWER(m.fileName) LIKE CONCAT('%', :searchTerm, '%')
              OR LOWER(COALESCE(m.displayTitle, '')) LIKE CONCAT('%', :searchTerm, '%')
              OR LOWER(m.assetCode) LIKE CONCAT('%', :searchTerm, '%')
              OR LOWER(COALESCE(m.aiCategory, '')) LIKE CONCAT('%', :searchTerm, '%')
              OR LOWER(COALESCE(m.aiDescription, '')) LIKE CONCAT('%', :searchTerm, '%')
              OR (m.uploader IS NOT NULL AND LOWER(m.uploader.email) LIKE CONCAT('%', :searchTerm, '%'))
              OR EXISTS (
                  SELECT tag.id FROM AssetTag tag
                  WHERE tag.mediaAsset = m
                    AND LOWER(tag.label) LIKE CONCAT('%', :searchTerm, '%')
              )
          )
        ORDER BY m.createdAt DESC
        """)
    List<MediaAsset> findKeywordMatches(
            @Param("networkWide") boolean networkWide,
            @Param("institutionIds") Collection<UUID> institutionIds,
            @Param("searchTerm") String searchTerm,
            @Param("excludeIds") Collection<UUID> excludeIds,
            Pageable pageable);

    // JPQL (not SELECT *) so Hibernate emits an explicit column list and never
    // pulls the unmapped embedding VECTOR(1024) column across the wire.
    @Query("""
        SELECT m FROM MediaAsset m
        WHERE m.institution.id = :institutionId
          AND m.deletedAt IS NULL
          AND m.status = com.dasigconnect.backend.model.entity.MediaAssetStatus.READY
        ORDER BY m.createdAt DESC
        """)
    List<MediaAsset> findReadyByInstitution(@Param("institutionId") UUID institutionId);

    @Query("""
        SELECT m FROM MediaAsset m
        WHERE m.institution.id = :institutionId
          AND m.deletedAt IS NULL
          AND m.status = com.dasigconnect.backend.model.entity.MediaAssetStatus.READY
          AND LOWER(COALESCE(m.temporalClassification, '')) <> 'expired'
          AND (
              NOT EXISTS (
                  SELECT link.id FROM SubmissionMediaAsset link
                  WHERE link.mediaAsset.id = m.id
              )
              OR EXISTS (
                  SELECT visibleLink.id FROM SubmissionMediaAsset visibleLink
                  WHERE visibleLink.mediaAsset.id = m.id
                    AND visibleLink.submission.status <> com.dasigconnect.backend.model.entity.SubmissionStatus.draft
              )
          )
        ORDER BY m.createdAt DESC
        """)
    List<MediaAsset> findVisibleReadyByInstitution(
            @Param("institutionId") UUID institutionId, Pageable pageable);

    // Excludes STAGED rows (draft uploads not yet bound to an institution) so they
    // never surface in the admin network-wide Media Repository view.
    @Query("SELECT m FROM MediaAsset m WHERE m.deletedAt IS NULL AND m.status <> com.dasigconnect.backend.model.entity.MediaAssetStatus.STAGED ORDER BY m.createdAt DESC")
    List<MediaAsset> findAllActive();

    long countByMediaAlbumIdAndDeletedAtIsNull(UUID mediaAlbumId);

    /**
     * Same visibility rule as findRepositoryPage/countActiveAssetsByAlbum: an
     * asset exclusively attached to a draft submission doesn't count as
     * "in" the folder for the purposes of blocking a delete — the album is
     * the source of truth for where an asset lives, not a draft that hasn't
     * even been submitted yet.
     */
    @Query("""
            SELECT COUNT(m) FROM MediaAsset m
            WHERE m.mediaAlbum.id = :albumId AND m.deletedAt IS NULL
              AND m.status <> com.dasigconnect.backend.model.entity.MediaAssetStatus.STAGED
              AND (
                  NOT EXISTS (
                      SELECT sma.id FROM SubmissionMediaAsset sma
                      WHERE sma.mediaAsset = m
                  )
                  OR EXISTS (
                      SELECT publishedLink.id FROM SubmissionMediaAsset publishedLink
                      WHERE publishedLink.mediaAsset = m
                        AND publishedLink.submission.status <> com.dasigconnect.backend.model.entity.SubmissionStatus.draft
                  )
              )
            """)
    long countVisibleAssetsByAlbum(@Param("albumId") UUID albumId);

    /**
     * Detach every remaining asset from a deleted album — soft-deleted rows
     * still awaiting purge, and active rows that countVisibleAssetsByAlbum
     * doesn't count (exclusively attached to a draft submission). The album
     * is gone either way, so their media_album_id can't survive it.
     */
    @Modifying
    @Query(value = "UPDATE media_assets SET media_album_id = NULL WHERE media_album_id = :albumId", nativeQuery = true)
    void detachAllAssetsFromAlbum(@Param("albumId") UUID albumId);

    /**
     * Every stored object URL for an institution's assets (active and
     * soft-deleted). Used to best-effort purge objects from the media store
     * before the institution and its asset rows are hard-deleted.
     */
    @Query("SELECT m.storageUrl FROM MediaAsset m WHERE m.institution.id = :institutionId AND m.storageUrl IS NOT NULL")
    List<String> findStorageUrlsByInstitutionId(@Param("institutionId") UUID institutionId);

    /**
     * Hard-deletes every media asset owned by an institution — including
     * soft-deleted rows that {@code existsActiveByInstitutionId} does not see —
     * so the institution and its albums can be removed without tripping the
     * {@code media_assets.institution_id} / {@code media_album_id} foreign
     * keys. {@code asset_tags}, {@code asset_embeddings}, and
     * {@code media_asset_embeddings} are cleared by their {@code ON DELETE
     * CASCADE} constraints.
     */
    @Modifying
    @Query(value = "DELETE FROM media_assets WHERE institution_id = :institutionId", nativeQuery = true)
    void deleteByInstitutionId(@Param("institutionId") UUID institutionId);

    /**
     * Re-home the active assets in a set of albums to another institution.
     */
    @Modifying
    @Query(value = "UPDATE media_assets SET institution_id = :institutionId WHERE media_album_id IN :albumIds AND deleted_at IS NULL", nativeQuery = true)
    void rehomeAssetsInAlbums(@Param("institutionId") UUID institutionId, @Param("albumIds") java.util.Collection<UUID> albumIds);

    /**
     * [albumId, assetCount] pairs for every album in the institution that holds
     * active assets. Mirrors findRepositoryPage's own visibility rule (STAGED
     * excluded, and an asset exclusively attached to a draft submission hidden
     * until that draft leaves draft status) — otherwise a folder card could
     * show "1 item" for an asset the browse view itself never displays,
     * leaving the folder looking empty once opened.
     */
    @Query("""
            SELECT m.mediaAlbum.id, COUNT(m)
            FROM MediaAsset m
            WHERE m.institution.id = :institutionId AND m.deletedAt IS NULL AND m.mediaAlbum IS NOT NULL
              AND m.status <> com.dasigconnect.backend.model.entity.MediaAssetStatus.STAGED
              AND (
                  NOT EXISTS (
                      SELECT sma.id FROM SubmissionMediaAsset sma
                      WHERE sma.mediaAsset = m
                  )
                  OR EXISTS (
                      SELECT publishedLink.id FROM SubmissionMediaAsset publishedLink
                      WHERE publishedLink.mediaAsset = m
                        AND publishedLink.submission.status <> com.dasigconnect.backend.model.entity.SubmissionStatus.draft
                  )
              )
            GROUP BY m.mediaAlbum.id
            """)
    List<Object[]> countActiveAssetsByAlbum(@Param("institutionId") UUID institutionId);

    /**
     * Same as {@link #countActiveAssetsByAlbum} but across every institution
     * (admin network view).
     */
    @Query("""
            SELECT m.mediaAlbum.id, COUNT(m)
            FROM MediaAsset m
            WHERE m.deletedAt IS NULL AND m.mediaAlbum IS NOT NULL
              AND m.status <> com.dasigconnect.backend.model.entity.MediaAssetStatus.STAGED
              AND (
                  NOT EXISTS (
                      SELECT sma.id FROM SubmissionMediaAsset sma
                      WHERE sma.mediaAsset = m
                  )
                  OR EXISTS (
                      SELECT publishedLink.id FROM SubmissionMediaAsset publishedLink
                      WHERE publishedLink.mediaAsset = m
                        AND publishedLink.submission.status <> com.dasigconnect.backend.model.entity.SubmissionStatus.draft
                  )
              )
            GROUP BY m.mediaAlbum.id
            """)
    List<Object[]> countActiveAssetsByAlbumAllInstitutions();

    boolean existsByAssetCode(String assetCode);

    @Query("SELECT m FROM MediaAsset m WHERE m.institution.id = :institutionId AND m.contentHash = :contentHash AND m.deletedAt IS NULL")
    Optional<MediaAsset> findActiveByInstitutionIdAndContentHash(@Param("institutionId") UUID institutionId,
            @Param("contentHash") String contentHash);

    boolean existsByUploaderId(UUID uploaderId);

    /**
     * Personal-data erasure: soft-delete every still-live asset this account
     * uploaded that isn't attached to a submission. Attached assets are
     * institutional/published content and are left in place (the uploader link
     * is anonymised separately). The retention purge job clears the storage
     * objects on its next run.
     */
    @Modifying
    @Query(value = """
        UPDATE media_assets
        SET deleted_at = NOW(), deleted_by_user_id = :actorId
        WHERE uploader_id = :uploaderId
          AND deleted_at IS NULL
          AND id NOT IN (SELECT media_asset_id FROM submission_media_assets)
        """, nativeQuery = true)
    int softDeleteUnattachedAssetsByUploader(@Param("uploaderId") UUID uploaderId, @Param("actorId") UUID actorId);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE media_assets
        SET embedding = CAST(:embedding AS vector),
            embedding_generated_at = NOW(),
            embedding_model = :embeddingModel
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
          AND COALESCE(LOWER(temporal_classification), '') <> 'expired'
        ORDER BY embedding <=> CAST(:queryVector AS vector)
        LIMIT 5
        """, nativeQuery = true)
    List<MediaAsset> findTopSimilar(@Param("institutionId") UUID institutionId, @Param("queryVector") String queryVectorJson);

    @Query(value = "SELECT embedding::text FROM media_assets WHERE id = :id AND deleted_at IS NULL AND status = 'READY' AND embedding IS NOT NULL", nativeQuery = true)
    Optional<String> findEmbeddingById(@Param("id") UUID id);

    @Query("""
        SELECT m FROM MediaAsset m
        WHERE m.deletedAt IS NULL
          AND (
              m.status IS NULL
              OR m.status <> com.dasigconnect.backend.model.entity.MediaAssetStatus.STAGED
          )
          AND (
              m.status IN (
                  com.dasigconnect.backend.model.entity.MediaAssetStatus.PROCESSING,
                  com.dasigconnect.backend.model.entity.MediaAssetStatus.FAILED
              )
              OR m.aiProcessingVersion IS NULL
              OR m.aiProcessingVersion <> :processingVersion
          )
          AND NOT EXISTS (
              SELECT job.id FROM MediaProcessingJob job
              WHERE job.assetId = m.id
                AND job.processingVersion = :processingVersion
                AND job.status IN (
                    com.dasigconnect.backend.model.entity.MediaProcessingJobStatus.PENDING,
                    com.dasigconnect.backend.model.entity.MediaProcessingJobStatus.PROCESSING,
                    com.dasigconnect.backend.model.entity.MediaProcessingJobStatus.RETRY,
                    com.dasigconnect.backend.model.entity.MediaProcessingJobStatus.DEAD
                )
          )
        ORDER BY m.createdAt ASC, m.id ASC
        """)
    List<MediaAsset> findNeedingProcessingVersion(
            @Param("processingVersion") String processingVersion, Pageable pageable);

    @Query(value = """
        SELECT ma.*
        FROM media_assets ma
        WHERE ma.deleted_at IS NULL
          AND ma.status = 'READY'
          AND ma.file_type IN ('jpeg', 'png', 'webp', 'gif')
          AND NOT EXISTS (
              SELECT 1
              FROM media_asset_embeddings embedding
              WHERE embedding.asset_id = ma.id
                AND embedding.embedding_type = 'image'
          )
          AND NOT EXISTS (
              SELECT 1
              FROM media_processing_jobs job
              WHERE job.asset_id = ma.id
                AND job.job_type = 'EMBED_IMAGE_ONLY'
                AND job.status IN ('PENDING', 'PROCESSING', 'RETRY', 'DEAD')
          )
        ORDER BY ma.created_at ASC, ma.id ASC
        """, nativeQuery = true)
    List<MediaAsset> findReadyImagesMissingImageEmbedding(Pageable pageable);

    /**
     * Returns id + cosine similarity score for top nearest neighbours.
     */
    @Query(value = """
        SELECT CAST(id AS text), 1 - (embedding <=> CAST(:queryVector AS vector)) AS score
        FROM media_assets
        WHERE institution_id = :institutionId
          AND deleted_at IS NULL
          AND status = 'READY'
          AND embedding IS NOT NULL
          AND COALESCE(LOWER(temporal_classification), '') <> 'expired'
        ORDER BY embedding <=> CAST(:queryVector AS vector)
        LIMIT 30
        """, nativeQuery = true)
    List<Object[]> findTopSimilarWithScore(@Param("institutionId") UUID institutionId,
            @Param("queryVector") String queryVectorJson);

    @Query("SELECT m FROM MediaAsset m WHERE m.id IN :ids AND m.deletedAt IS NULL")
    List<MediaAsset> findActiveByIds(@Param("ids") List<UUID> ids);

    /**
     * [idText, cosineScore] for the nearest ready assets across the given
     * institutions.
     */
    @Query(value = """
        SELECT CAST(id AS text), 1 - (embedding <=> CAST(:queryVector AS vector)) AS score
        FROM media_assets
        WHERE institution_id IN (:institutionIds)
          AND deleted_at IS NULL
          AND status = 'READY'
          AND embedding IS NOT NULL
          AND COALESCE(LOWER(temporal_classification), '') <> 'expired'
        ORDER BY embedding <=> CAST(:queryVector AS vector)
        LIMIT 60
        """, nativeQuery = true)
    List<Object[]> findTopSimilarInInstitutions(@Param("institutionIds") java.util.Collection<UUID> institutionIds,
            @Param("queryVector") String queryVectorJson);

    /**
     * [idText, cosineScore] for the nearest ready assets network-wide (admin).
     */
    @Query(value = """
        SELECT CAST(id AS text), 1 - (embedding <=> CAST(:queryVector AS vector)) AS score
        FROM media_assets
        WHERE deleted_at IS NULL
          AND status = 'READY'
          AND embedding IS NOT NULL
          AND COALESCE(LOWER(temporal_classification), '') <> 'expired'
        ORDER BY embedding <=> CAST(:queryVector AS vector)
        LIMIT 60
        """, nativeQuery = true)
    List<Object[]> findTopSimilarAllInstitutions(@Param("queryVector") String queryVectorJson);

    @Modifying
    @Transactional
    @Query(value = "UPDATE media_assets SET status = :status WHERE id = :id", nativeQuery = true)
    void updateStatus(@Param("id") UUID id, @Param("status") String status);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE media_assets
        SET status = 'READY', ai_processing_version = :processingVersion
        WHERE id = :id AND deleted_at IS NULL
        """, nativeQuery = true)
    void markProcessingReady(@Param("id") UUID id,
                             @Param("processingVersion") String processingVersion);

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

    @Query(value = """
        SELECT COUNT(*) FROM media_assets
        WHERE deleted_at IS NULL
          AND status = 'FAILED'
        """, nativeQuery = true)
    long countFailedAssets();

    @Query(value = """
        SELECT file_name FROM media_assets
        WHERE deleted_at IS NULL
          AND status = 'FAILED'
        ORDER BY created_at DESC
        LIMIT 5
        """, nativeQuery = true)
    List<String> findSampleFailedFilenames();

    @Query(value = """
        SELECT m FROM MediaAsset m
        WHERE m.deletedAt IS NOT NULL
          AND m.purgedAt IS NULL
          AND (:networkWide = true OR m.institution.id IN :institutionIds)
          AND (
              :searchTerm = ''
              OR LOWER(m.fileName) LIKE CONCAT('%', :searchTerm, '%')
              OR LOWER(COALESCE(m.displayTitle, '')) LIKE CONCAT('%', :searchTerm, '%')
              OR LOWER(m.assetCode) LIKE CONCAT('%', :searchTerm, '%')
          )
        """, countQuery = """
        SELECT COUNT(m) FROM MediaAsset m
        WHERE m.deletedAt IS NOT NULL
          AND m.purgedAt IS NULL
          AND (:networkWide = true OR m.institution.id IN :institutionIds)
          AND (
              :searchTerm = ''
              OR LOWER(m.fileName) LIKE CONCAT('%', :searchTerm, '%')
              OR LOWER(COALESCE(m.displayTitle, '')) LIKE CONCAT('%', :searchTerm, '%')
              OR LOWER(m.assetCode) LIKE CONCAT('%', :searchTerm, '%')
          )
        """)
    Page<MediaAsset> findTrashPage(
            @Param("networkWide") boolean networkWide,
            @Param("institutionIds") Collection<UUID> institutionIds,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

    @Query("SELECT m FROM MediaAsset m WHERE m.id = :id AND m.deletedAt IS NOT NULL AND m.purgedAt IS NULL")
    Optional<MediaAsset> findTrashedById(@Param("id") UUID id);

    @Query(value = """
        SELECT COUNT(m) FROM MediaAsset m
        WHERE m.deletedAt IS NOT NULL
          AND m.purgedAt IS NULL
          AND (:networkWide = true OR m.institution.id IN :institutionIds)
        """)
    long countTrash(
            @Param("networkWide") boolean networkWide,
            @Param("institutionIds") Collection<UUID> institutionIds);

    @Query(value = """
        SELECT m FROM MediaAsset m
        WHERE m.deletedAt IS NOT NULL
          AND m.purgedAt IS NULL
          AND (:networkWide = true OR m.institution.id IN :institutionIds)
        """)
    List<MediaAsset> findAllTrashed(
            @Param("networkWide") boolean networkWide,
            @Param("institutionIds") Collection<UUID> institutionIds);
}

