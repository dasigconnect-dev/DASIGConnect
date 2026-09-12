package com.dasigconnect.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dasigconnect.backend.model.entity.AssetTag;

public interface AssetTagRepository extends JpaRepository<AssetTag, UUID> {

    List<AssetTag> findByMediaAssetIdOrderByCreatedAtAsc(UUID mediaAssetId);

    boolean existsByMediaAssetIdAndLabel(UUID mediaAssetId, String label);

    void deleteByMediaAssetId(UUID mediaAssetId);

    @Query("""
            SELECT t.mediaAsset.id, t.label
            FROM AssetTag t
            WHERE t.mediaAsset.id IN :mediaAssetIds
            ORDER BY t.createdAt ASC
            """)
    List<Object[]> findLabelsByMediaAssetIds(@Param("mediaAssetIds") List<UUID> mediaAssetIds);

    @Query("""
            SELECT t.mediaAsset.id, t.label, t.source
            FROM AssetTag t
            WHERE t.mediaAsset.id IN :mediaAssetIds
            ORDER BY t.createdAt ASC
            """)
    List<Object[]> findLabelsAndSourcesByMediaAssetIds(@Param("mediaAssetIds") List<UUID> mediaAssetIds);

    /**
     * [rootAlbumId, label] pairs for every tag on every asset filed directly
     * into a root album of the institution. Used to score album Auto-Match
     * (UC-1.7) tag overlap against the draft's own tags.
     */
    @Query(value = """
            SELECT CAST(ma.media_album_id AS text), t.label
            FROM asset_tags t
            JOIN media_assets ma ON ma.id = t.media_asset_id
            JOIN media_albums al ON al.id = ma.media_album_id
            WHERE ma.institution_id = :institutionId
              AND al.parent_album_id IS NULL
              AND ma.deleted_at IS NULL
            """, nativeQuery = true)
    List<Object[]> findLabelsByRootAlbumForInstitution(@Param("institutionId") UUID institutionId);
}
