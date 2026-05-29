package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.MediaFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaFolderRepository extends JpaRepository<MediaFolder, UUID> {

    @Query("SELECT f FROM MediaFolder f WHERE f.id = :id AND f.institution.id = :institutionId")
    Optional<MediaFolder> findByIdAndInstitution(@Param("id") UUID id,
                                                 @Param("institutionId") UUID institutionId);

    @Query("SELECT f FROM MediaFolder f WHERE f.institution.id = :institutionId ORDER BY f.name ASC")
    List<MediaFolder> findByInstitution(@Param("institutionId") UUID institutionId);

    @Query("""
        SELECT f FROM MediaFolder f
        WHERE f.institution.id = :institutionId
          AND (:parentId IS NULL AND f.parentFolder IS NULL
               OR f.parentFolder.id = :parentId)
        ORDER BY f.name ASC
        """)
    List<MediaFolder> findChildren(@Param("institutionId") UUID institutionId,
                                   @Param("parentId") UUID parentId);

    boolean existsByParentFolderId(UUID parentFolderId);
}
