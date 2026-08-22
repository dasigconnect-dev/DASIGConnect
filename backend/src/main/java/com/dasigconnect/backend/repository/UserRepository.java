package com.dasigconnect.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.model.entity.UserStatus;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    List<User> findByInstitutionIdOrderByCreatedAtDesc(UUID institutionId);

    List<User> findByInstitutionIdAndRoleOrderByCreatedAtDesc(UUID institutionId, UserRole role);

    boolean existsByInstitutionId(UUID institutionId);

    long countByInstitutionId(UUID institutionId);

    long countByInstitutionIdAndRole(UUID institutionId, UserRole role);

    long countByInstitutionIdAndRoleAndAccountState(UUID institutionId, UserRole role, UserStatus accountState);

    /**
     * A3: check if institution has any active validators before reactivating
     */
    boolean existsByInstitutionIdAndAccountState(UUID institutionId, UserStatus accountState);

    List<User> findByRole(UserRole role);
}
