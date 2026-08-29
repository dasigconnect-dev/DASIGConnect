package com.dasigconnect.backend.repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.model.entity.UserStatus;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    List<User> findByInstitutionIdOrderByCreatedAtDesc(UUID institutionId);

    List<User> findByInstitutionIdAndRoleOrderByCreatedAtDesc(UUID institutionId, UserRole role);

    boolean existsByInstitutionId(UUID institutionId);

    long countByInstitutionId(UUID institutionId);

    long countByInstitutionIdAndRole(UUID institutionId, UserRole role);

    long countByInstitutionIdAndRoleAndAccountState(UUID institutionId, UserRole role, UserStatus accountState);

    /** Network-wide count for a role in a given account state (e.g. active admins). */
    long countByRoleAndAccountState(UserRole role, UserStatus accountState);

    /**
     * A3: check if institution has any active moderators before reactivating
     */
    boolean existsByInstitutionIdAndAccountState(UUID institutionId, UserStatus accountState);

    List<User> findByRole(UserRole role);

    @Query("""
            select user
            from User user
            where user.role in :roles
            order by user.createdAt desc
            """)
    List<User> findByRolesOrderByCreatedAtDesc(@Param("roles") Collection<UserRole> roles);

    /** Network-wide roster across all institutions, with institution eagerly fetched to avoid N+1. */
    @Query("""
            select user
            from User user
            left join fetch user.institution
            where user.role in :roles
            order by user.createdAt desc
            """)
    List<User> findByRolesWithInstitutionOrderByCreatedAtDesc(@Param("roles") Collection<UserRole> roles);

    /** Batch load with institution eagerly fetched — avoids an N+1 when rendering lists. */
    @Query("select user from User user left join fetch user.institution where user.id in :ids")
    List<User> findAllByIdWithInstitution(@Param("ids") Collection<UUID> ids);
}
