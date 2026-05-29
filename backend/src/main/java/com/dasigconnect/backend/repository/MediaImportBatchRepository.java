package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.MediaImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaImportBatchRepository extends JpaRepository<MediaImportBatch, UUID> {

    @Query("SELECT b FROM MediaImportBatch b WHERE b.id = :id AND b.institution.id = :institutionId")
    Optional<MediaImportBatch> findByIdAndInstitution(@Param("id") UUID id,
                                                      @Param("institutionId") UUID institutionId);

    @Query("SELECT b FROM MediaImportBatch b WHERE b.institution.id = :institutionId ORDER BY b.createdAt DESC")
    List<MediaImportBatch> findByInstitution(@Param("institutionId") UUID institutionId);
}
