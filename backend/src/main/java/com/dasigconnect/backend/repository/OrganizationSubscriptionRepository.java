package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.OrganizationSubscription;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationSubscriptionRepository extends JpaRepository<OrganizationSubscription, UUID> {

    @EntityGraph(attributePaths = {"organization", "plan"})
    Optional<OrganizationSubscription> findFirstByOrderByCreatedAtAsc();

    @EntityGraph(attributePaths = {"organization", "plan"})
    Optional<OrganizationSubscription> findByOrganizationId(UUID organizationId);
}
