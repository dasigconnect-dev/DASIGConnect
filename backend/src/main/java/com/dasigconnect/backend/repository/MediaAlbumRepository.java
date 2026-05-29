package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.MediaAlbum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaAlbumRepository extends JpaRepository<MediaAlbum, UUID> {

    @Query("SELECT a FROM MediaAlbum a WHERE a.id = :id AND a.institution.id = :institutionId")
    Optional<MediaAlbum> findByIdAndInstitution(@Param("id") UUID id,
                                                @Param("institutionId") UUID institutionId);

    @Query("SELECT a FROM MediaAlbum a WHERE a.institution.id = :institutionId ORDER BY a.updatedAt DESC")
    List<MediaAlbum> findByInstitution(@Param("institutionId") UUID institutionId);
}
