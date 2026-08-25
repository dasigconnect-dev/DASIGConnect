package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.WatermarkConfiguration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatermarkConfigurationRepository extends JpaRepository<WatermarkConfiguration, UUID> {
    Optional<WatermarkConfiguration> findByInstitutionId(UUID institutionId);
    Optional<WatermarkConfiguration> findByInstitutionIsNull();
}
