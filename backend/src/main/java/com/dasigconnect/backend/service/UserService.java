package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.user.UserDto;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Returns the profile of the authenticated user.
     * Used by GET /api/v1/me so the frontend has reliable identity data.
     */
    public UserDto getProfile(JwtUserDetails principal) {
        return userRepository.findById(principal.userId())
                .map(UserDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    /**
     * Lists all users for a given institution.
     * - ADMINISTRATOR: may query any institution
     * - VALIDATOR: may only query their own institution
     * - CONTRIBUTOR: access denied
     */
    public List<UserDto> listByInstitution(UUID institutionId, JwtUserDetails requester) {
        switch (requester.role().toLowerCase()) {
            case "administrator" -> { /* access allowed */ }
            case "validator" -> {
                if (!institutionId.equals(requester.institutionId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Validators can only list users in their own institution.");
                }
            }
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only administrators and validators can list users.");
        }

        return userRepository.findByInstitutionIdOrderByCreatedAtDesc(institutionId)
                .stream()
                .map(UserDto::from)
                .toList();
    }

    /**
     * Returns counts of contributors and validators for an institution.
     * Used for dashboard summary tiles.
     */
    public java.util.Map<String, Long> countByRole(UUID institutionId) {
        return java.util.Map.of(
                "contributors", userRepository.countByInstitutionIdAndRole(institutionId, UserRole.contributor),
                "validators", userRepository.countByInstitutionIdAndRole(institutionId, UserRole.validator)
        );
    }
}
