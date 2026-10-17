package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.Organization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findBySlug(String slug);
}
