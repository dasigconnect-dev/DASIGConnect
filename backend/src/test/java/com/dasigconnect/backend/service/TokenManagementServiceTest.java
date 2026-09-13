package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.FacebookPageToken;
import com.dasigconnect.backend.repository.FacebookPageTokenRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenManagementServiceTest {

    @Mock FacebookPageTokenRepository pageTokenRepository;
    @Mock TokenEncryptionService tokenEncryptionService;
    @Mock AuditLogService auditLogService;

    @InjectMocks TokenManagementService service;

    private final JwtUserDetails admin =
            new JwtUserDetails(UUID.randomUUID(), "admin@example.com", "admin", null);

    @Test
    void setManualToken_encryptsStoresAndAudits() {
        UUID tokenId = UUID.randomUUID();
        FacebookPageToken token = new FacebookPageToken();
        ReflectionTestUtils.setField(token, "id", tokenId);
        token.setPageId("123456");
        token.setActive(false);

        when(pageTokenRepository.findById(tokenId)).thenReturn(Optional.of(token));
        when(tokenEncryptionService.encryptToken("raw-token")).thenReturn("encrypted-blob");
        when(pageTokenRepository.save(any(FacebookPageToken.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.setManualToken(tokenId, "  raw-token  ", admin);

        assertThat(token.getEncryptedToken()).isEqualTo("encrypted-blob");
        assertThat(token.isActive()).isTrue();
        assertThat(token.getLastValidatedAt()).isNull();
        assertThat(result.getPageId()).isEqualTo("123456");
        verify(auditLogService).recordSystemAction("TOKEN_MANUALLY_SET", tokenId,
                java.util.Map.of("pageId", "123456", "setBy", admin.userId().toString()));
    }

    @Test
    void setManualToken_blankToken_isRejected() {
        UUID tokenId = UUID.randomUUID();
        FacebookPageToken token = new FacebookPageToken();
        token.setPageId("123456");
        when(pageTokenRepository.findById(tokenId)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.setManualToken(tokenId, "   ", admin))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void setManualToken_unknownTokenId_returns404() {
        UUID tokenId = UUID.randomUUID();
        when(pageTokenRepository.findById(tokenId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setManualToken(tokenId, "raw-token", admin))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }
}
