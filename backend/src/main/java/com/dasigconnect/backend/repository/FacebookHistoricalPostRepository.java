package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.FacebookHistoricalPost;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacebookHistoricalPostRepository extends JpaRepository<FacebookHistoricalPost, UUID> {

    Optional<FacebookHistoricalPost> findByPageIdAndGraphPostId(String pageId, String graphPostId);

    Optional<FacebookHistoricalPost> findByIdAndPageId(UUID id, String pageId);

    List<FacebookHistoricalPost> findByPageIdOrderByCreatedTimeDesc(String pageId, Pageable pageable);

    List<FacebookHistoricalPost> findByPageIdAndInstitution_IdOrderByCreatedTimeDesc(
            String pageId, UUID institutionId, Pageable pageable);
}
