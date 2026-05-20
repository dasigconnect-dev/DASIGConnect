package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    List<User> findByInstitutionIdOrderByCreatedAtDesc(UUID institutionId);
    long countByInstitutionIdAndRole(UUID institutionId, UserRole role);
}
