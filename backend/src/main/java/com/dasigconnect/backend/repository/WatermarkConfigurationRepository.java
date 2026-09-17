package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.WatermarkConfiguration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatermarkConfigurationRepository extends JpaRepository<WatermarkConfiguration, UUID> {
    Optional<WatermarkConfiguration> findByInstitutionIsNull();

    /**
     * FK cleanup only, called from InstitutionService.deletePermanently() —
     * watermark_configurations.institution_id is nullable and nothing sets it
     * anymore (the per-institution override feature was removed 2026-09-17),
     * but the column and its FK to institutions still exist, so a hard delete
     * must clear any row before the institution itself can go.
     */
    void deleteByInstitutionId(UUID institutionId);
}
