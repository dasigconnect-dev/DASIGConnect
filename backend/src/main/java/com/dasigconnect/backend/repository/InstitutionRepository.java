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

    boolean existsByEmailDomain(String emailDomain);

    /** A5: duplicate name guard on create */
    boolean existsByNameIgnoreCase(String name);

    /** A5: duplicate name guard on edit (exclude self) */
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    /** A1: duplicate email domain guard on edit (exclude self) */
    boolean existsByEmailDomainAndIdNot(String emailDomain, UUID id);

    List<Institution> findAllByStatus(InstitutionStatus status);
}
