package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.OrganizationSubscription;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface OrganizationSubscriptionRepository extends JpaRepository<OrganizationSubscription, UUID> {

    @EntityGraph(attributePaths = {"organization", "plan"})
    Optional<OrganizationSubscription> findFirstByOrderByCreatedAtAsc();

    @EntityGraph(attributePaths = {"organization", "plan"})
    Optional<OrganizationSubscription> findByOrganizationId(UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"organization", "plan"})
    @Query("SELECT subscription FROM OrganizationSubscription subscription "
            + "WHERE subscription.organization.id = :organizationId")
    Optional<OrganizationSubscription> findByOrganizationIdForUpdate(
            @Param("organizationId") UUID organizationId);
}
