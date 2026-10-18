package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.SubscriptionInvoice;
import com.dasigconnect.backend.model.entity.SubscriptionInvoiceStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionInvoiceRepository extends JpaRepository<SubscriptionInvoice, UUID> {
    @EntityGraph(attributePaths = {"subscription", "subscription.organization", "subscription.plan"})
    List<SubscriptionInvoice> findAllByOrderByCreatedAtDesc(Pageable pageable);
    @EntityGraph(attributePaths = {"subscription", "subscription.organization", "subscription.plan"})
    Optional<SubscriptionInvoice> findFirstByStatusOrderByCreatedAtDesc(SubscriptionInvoiceStatus status);
}
