package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.PaymongoWebhookReceipt;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PaymongoWebhookReceiptRepository extends JpaRepository<PaymongoWebhookReceipt, UUID> {
    Optional<PaymongoWebhookReceipt> findByExternalEventId(String externalEventId);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO paymongo_webhook_receipts (
                id, external_event_id, event_type, live_mode, status, payload_sha256, received_at
            ) VALUES (
                gen_random_uuid(), :eventId, :eventType, :liveMode, 'RECEIVED', :payloadHash, :receivedAt
            ) ON CONFLICT (external_event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("eventId") String eventId,
            @Param("eventType") String eventType,
            @Param("liveMode") boolean liveMode,
            @Param("payloadHash") String payloadHash,
            @Param("receivedAt") Instant receivedAt);
}
