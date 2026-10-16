package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.FacebookPostMedia;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacebookPostMediaRepository extends JpaRepository<FacebookPostMedia, UUID> {

    Optional<FacebookPostMedia> findByHistoricalPost_IdAndPageIdAndGraphMediaId(
            UUID historicalPostId, String pageId, String graphMediaId);

    List<FacebookPostMedia> findByHistoricalPost_IdAndPageIdOrderByPositionAsc(
            UUID historicalPostId, String pageId);
}
