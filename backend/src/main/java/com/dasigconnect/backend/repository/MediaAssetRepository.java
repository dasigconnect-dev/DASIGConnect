package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    @Query("SELECT m FROM MediaAsset m WHERE m.id = :id AND m.deletedAt IS NULL")
    Optional<MediaAsset> findActiveById(@Param("id") UUID id);

    @Query("SELECT m FROM MediaAsset m WHERE m.institution.id = :institutionId AND m.deletedAt IS NULL ORDER BY m.createdAt DESC")
    List<MediaAsset> findActiveByInstitution(@Param("institutionId") UUID institutionId);

    boolean existsByAssetCode(String assetCode);

    @Modifying
    @Query(value = "UPDATE media_assets SET embedding = :embedding::vector WHERE id = :id", nativeQuery = true)
    void updateEmbedding(@Param("id") UUID id, @Param("embedding") String embeddingJson);

    @Query(value = """
        SELECT * FROM media_assets
        WHERE institution_id = :institutionId
          AND deleted_at IS NULL
          AND embedding IS NOT NULL
        ORDER BY embedding <=> :queryVector::vector
        LIMIT 5
        """, nativeQuery = true)
    List<MediaAsset> findTopSimilar(@Param("institutionId") UUID institutionId, @Param("queryVector") String queryVectorJson);
}
