package com.dasigconnect.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    List<User> findByInstitutionIdOrderByCreatedAtDesc(UUID institutionId);

    List<User> findByInstitutionIdAndRoleOrderByCreatedAtDesc(UUID institutionId, UserRole role);

    long countByInstitutionIdAndRole(UUID institutionId, UserRole role);

    List<User> findByRole(UserRole role);
}
