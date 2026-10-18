package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.SubscriptionPaymentLink;
import com.dasigconnect.backend.model.entity.SubscriptionPaymentLinkStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionPaymentLinkRepository extends JpaRepository<SubscriptionPaymentLink, UUID> {
    @EntityGraph(attributePaths = {"invoice", "invoice.subscription", "invoice.subscription.organization", "invoice.subscription.plan"})
    Optional<SubscriptionPaymentLink> findByExternalLinkId(String externalLinkId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"invoice", "invoice.subscription", "invoice.subscription.organization", "invoice.subscription.plan"})
    @Query("SELECT link FROM SubscriptionPaymentLink link WHERE link.externalLinkId = :externalLinkId")
    Optional<SubscriptionPaymentLink> findByExternalLinkIdForUpdate(
            @Param("externalLinkId") String externalLinkId);
    @EntityGraph(attributePaths = {"invoice"})
    Optional<SubscriptionPaymentLink> findFirstByInvoiceIdAndStatusOrderByCreatedAtDesc(
            UUID invoiceId, SubscriptionPaymentLinkStatus status);
    @EntityGraph(attributePaths = {"invoice"})
    Optional<SubscriptionPaymentLink> findFirstByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);
    @EntityGraph(attributePaths = {"invoice", "invoice.subscription", "invoice.subscription.organization", "invoice.subscription.plan"})
    List<SubscriptionPaymentLink> findByStatus(SubscriptionPaymentLinkStatus status);
}
