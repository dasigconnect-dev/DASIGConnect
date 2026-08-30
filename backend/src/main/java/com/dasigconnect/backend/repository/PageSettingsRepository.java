package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.PageSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PageSettingsRepository extends JpaRepository<PageSettings, UUID> {
    Optional<PageSettings> findByInstitutionId(UUID institutionId);
    Optional<PageSettings> findByInstitutionIsNull();
    void deleteByInstitutionId(UUID institutionId);
}
