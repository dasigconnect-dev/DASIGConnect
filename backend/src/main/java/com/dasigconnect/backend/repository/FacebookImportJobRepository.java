package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.FacebookImportJob;
import com.dasigconnect.backend.model.entity.FacebookImportStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacebookImportJobRepository extends JpaRepository<FacebookImportJob, UUID> {

    Optional<FacebookImportJob> findByIdAndPageId(UUID id, String pageId);

    List<FacebookImportJob> findByPageIdOrderByCreatedAtDesc(String pageId, Pageable pageable);

    boolean existsByPageIdAndStatusIn(String pageId, Collection<FacebookImportStatus> statuses);
}
