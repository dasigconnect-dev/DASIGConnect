package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.InvitationBatch;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvitationBatchRepository extends JpaRepository<InvitationBatch, UUID> {
    List<InvitationBatch> findByCreatedByIdOrderByCreatedAtDesc(UUID createdById);
}

