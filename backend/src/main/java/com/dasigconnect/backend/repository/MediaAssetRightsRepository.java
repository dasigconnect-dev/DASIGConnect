package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.MediaAssetRights;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaAssetRightsRepository extends JpaRepository<MediaAssetRights, UUID> {

    /** Tenant-scoped lookup of an asset's current rights record. */
    @Query("""
        SELECT r FROM MediaAssetRights r
        WHERE r.assetId = :assetId AND r.institutionId = :institutionId
        """)
    Optional<MediaAssetRights> findByAssetIdAndInstitutionId(
            @Param("assetId") UUID assetId,
            @Param("institutionId") UUID institutionId);

    /** Batch lookup for the UC-4.12 Phase 7G inventory export. */
    List<MediaAssetRights> findByAssetIdIn(java.util.Collection<UUID> assetIds);
}
