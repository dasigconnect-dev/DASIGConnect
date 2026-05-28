package com.dasigconnect.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dasigconnect.backend.model.entity.EmailDeliveryLog;

public interface EmailDeliveryLogRepository extends JpaRepository<EmailDeliveryLog, UUID> {

    @Modifying
    @Query("DELETE FROM EmailDeliveryLog e WHERE e.recipient.id = :recipientId")
    void deleteByRecipientId(@Param("recipientId") UUID recipientId);
}
