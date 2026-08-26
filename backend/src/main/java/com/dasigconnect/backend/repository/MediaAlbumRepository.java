package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.MediaAlbum;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaAlbumRepository extends JpaRepository<MediaAlbum, UUID> {

    @Query("SELECT a FROM MediaAlbum a WHERE a.institution.id = :institutionId ORDER BY LOWER(a.name)")
    List<MediaAlbum> findByInstitutionIdOrderByName(@Param("institutionId") UUID institutionId);

    @Query("""
            SELECT a FROM MediaAlbum a
            WHERE a.institution.id = :institutionId
              AND LOWER(a.name) = LOWER(:name)
            """)
    Optional<MediaAlbum> findByInstitutionIdAndNameIgnoreCase(
            @Param("institutionId") UUID institutionId,
            @Param("name") String name);

    boolean existsByInstitutionIdAndNameIgnoreCase(UUID institutionId, String name);
}
