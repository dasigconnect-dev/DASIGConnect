package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.MediaAssetIntegrityCheck;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetIntegrityCheckRepository extends JpaRepository<MediaAssetIntegrityCheck, UUID> {

    List<MediaAssetIntegrityCheck> findByAssetIdAndInstitutionIdOrderByCheckedAtDesc(
            UUID assetId, UUID institutionId);
}
