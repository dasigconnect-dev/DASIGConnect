package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.AlbumAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface AlbumAssetRepository extends JpaRepository<AlbumAsset, UUID> {

    @Query("""
        SELECT la FROM AlbumAsset la
        WHERE la.album.id = :albumId AND la.asset.deletedAt IS NULL
        ORDER BY la.displayOrder ASC, la.addedAt ASC
        """)
    List<AlbumAsset> findByAlbumOrdered(@Param("albumId") UUID albumId);

    boolean existsByAlbumIdAndAssetId(UUID albumId, UUID assetId);

    void deleteByAlbumIdAndAssetId(UUID albumId, UUID assetId);

    long countByAlbumId(UUID albumId);
}
