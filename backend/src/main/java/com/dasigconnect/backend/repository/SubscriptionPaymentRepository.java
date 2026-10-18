package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.SubscriptionPayment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, UUID> {
    boolean existsByExternalPaymentId(String externalPaymentId);
    @EntityGraph(attributePaths = {"invoice", "paymentLink"})
    Optional<SubscriptionPayment> findByExternalPaymentId(String externalPaymentId);
    List<SubscriptionPayment> findByInvoiceIdOrderByPaidAtDesc(UUID invoiceId);
}
