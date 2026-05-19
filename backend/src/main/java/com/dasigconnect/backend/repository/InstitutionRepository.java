package com.dasigconnect.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.InstitutionStatus;

public interface InstitutionRepository extends JpaRepository<Institution, UUID> {

    Optional<Institution> findByCode(String code);

    boolean existsByCode(String code);

    List<Institution> findAllByStatus(InstitutionStatus status);
}
