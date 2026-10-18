package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.SubscriptionRefund;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRefundRepository extends JpaRepository<SubscriptionRefund, UUID> {
    @EntityGraph(attributePaths = {"payment", "payment.invoice", "payment.paymentLink"})
    Optional<SubscriptionRefund> findByExternalRefundId(String externalRefundId);
    List<SubscriptionRefund> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);

    @Query("""
            SELECT COALESCE(SUM(refund.amountCentavos), 0)
            FROM SubscriptionRefund refund
            WHERE refund.payment.id = :paymentId
              AND refund.status IN (
                  com.dasigconnect.backend.model.entity.SubscriptionRefundStatus.PENDING,
                  com.dasigconnect.backend.model.entity.SubscriptionRefundStatus.SUCCEEDED
              )
            """)
    long sumCommittedRefunds(@Param("paymentId") UUID paymentId);
}
