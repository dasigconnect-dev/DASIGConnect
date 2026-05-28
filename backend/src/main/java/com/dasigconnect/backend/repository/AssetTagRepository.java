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
}
