package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.user.UserDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.model.entity.UserStatus;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private UUID userId;
    private UUID institutionId;
    private Institution institution;
    private User contributor;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        institutionId = UUID.randomUUID();

        institution = new Institution();
        institution.setId(institutionId);
        institution.setName("CIT-U");
        institution.setCode("CIT-U");
        institution.setEmailDomain("cit.edu.ph");

        contributor = user(userId, "contributor@cit.edu.ph", UserRole.contributor, institution);
    }

    @Test
    void getProfile_existingUser_returnsUserDtoWithoutPasswordHash() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));

        UserDto result = userService.getProfile(principal(userId, "contributor", institutionId));

        assertThat(result.getId()).isEqualTo(userId);
        assertThat(result.getEmail()).isEqualTo("contributor@cit.edu.ph");
        assertThat(result.getRole()).isEqualTo("contributor");
        assertThat(result.getInstitutionId()).isEqualTo(institutionId);
        assertThat(result.getInstitutionName()).isEqualTo("CIT-U");
    }

    @Test
    void getProfile_missingUser_throws404() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(principal(userId, "contributor", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listByInstitution_administrator_canListAnyInstitution() {
        UUID requestedInstitutionId = UUID.randomUUID();
        User validator = user(UUID.randomUUID(), "validator@cit.edu.ph", UserRole.validator, institution);
        when(userRepository.findByInstitutionIdOrderByCreatedAtDesc(requestedInstitutionId))
                .thenReturn(List.of(validator));

        List<UserDto> result = userService.listByInstitution(
                requestedInstitutionId,
                principal(UUID.randomUUID(), "administrator", null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("validator@cit.edu.ph");
    }

    @Test
    void listByInstitution_validator_canListOwnInstitution() {
        when(userRepository.findByInstitutionIdOrderByCreatedAtDesc(institutionId))
                .thenReturn(List.of(contributor));

        List<UserDto> result = userService.listByInstitution(
                institutionId,
                principal(UUID.randomUUID(), "validator", institutionId));

        assertThat(result).extracting(UserDto::getEmail).containsExactly("contributor@cit.edu.ph");
    }

    @Test
    void listByInstitution_validatorCannotListOtherInstitution() {
        assertThatThrownBy(() -> userService.listByInstitution(
                UUID.randomUUID(),
                principal(UUID.randomUUID(), "validator", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listByInstitution_contributorIsForbidden() {
        assertThatThrownBy(() -> userService.listByInstitution(
                institutionId,
                principal(userId, "contributor", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void countByRole_returnsContributorAndValidatorCounts() {
        when(userRepository.countByInstitutionIdAndRole(institutionId, UserRole.contributor)).thenReturn(12L);
        when(userRepository.countByInstitutionIdAndRole(institutionId, UserRole.validator)).thenReturn(2L);

        Map<String, Long> result = userService.countByRole(institutionId);

        assertThat(result).containsEntry("contributors", 12L).containsEntry("validators", 2L);
        verify(userRepository).countByInstitutionIdAndRole(institutionId, UserRole.contributor);
        verify(userRepository).countByInstitutionIdAndRole(institutionId, UserRole.validator);
    }

    @Test
    void getById_validatorCanViewOwnInstitutionUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));

        UserDto result = userService.getById(userId, principal(UUID.randomUUID(), "validator", institutionId));

        assertThat(result.getEmail()).isEqualTo("contributor@cit.edu.ph");
    }

    @Test
    void getById_validatorCannotViewOtherInstitutionUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));

        assertThatThrownBy(() -> userService.getById(userId, principal(UUID.randomUUID(), "validator", UUID.randomUUID())))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateStatus_administratorCanDeactivateUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));
        when(userRepository.save(contributor)).thenReturn(contributor);

        UserDto result = userService.updateStatus(userId, UserStatus.inactive,
                principal(UUID.randomUUID(), "administrator", null));

        assertThat(result.getAccountState()).isEqualTo("inactive");
        verify(userRepository).save(contributor);
    }

    @Test
    void updateStatus_rejectsPendingStatusChange() {
        assertThatThrownBy(() -> userService.updateStatus(userId, UserStatus.pending,
                principal(UUID.randomUUID(), "administrator", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateStatus_validatorCannotManageValidator() {
        User validator = user(UUID.randomUUID(), "validator@cit.edu.ph", UserRole.validator, institution);
        when(userRepository.findById(validator.getId())).thenReturn(Optional.of(validator));

        assertThatThrownBy(() -> userService.updateStatus(validator.getId(), UserStatus.inactive,
                principal(UUID.randomUUID(), "validator", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static JwtUserDetails principal(UUID id, String role, UUID institutionId) {
        return new JwtUserDetails(id, role + "@example.com", role, institutionId);
    }

    private static User user(UUID id, String email, UserRole role, Institution institution) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        user.setAccountState(UserStatus.active);
        user.setInstitution(institution);
        user.setPasswordHash("hash-not-exposed");
        return user;
    }
}
