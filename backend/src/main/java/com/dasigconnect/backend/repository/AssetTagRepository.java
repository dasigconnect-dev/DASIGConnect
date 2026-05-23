package com.dasigconnect.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dasigconnect.backend.model.entity.AssetTag;

public interface AssetTagRepository extends JpaRepository<AssetTag, UUID> {

    List<AssetTag> findByMediaAssetIdOrderByCreatedAtAsc(UUID mediaAssetId);

    boolean existsByMediaAssetIdAndLabel(UUID mediaAssetId, String label);
}
