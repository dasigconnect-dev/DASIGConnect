package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.PostTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostTemplateRepository extends JpaRepository<PostTemplate, UUID> {
    List<PostTemplate> findByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);
    Optional<PostTemplate> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    boolean existsByOwnerUserIdAndNameIgnoreCase(UUID ownerUserId, String name);
}
