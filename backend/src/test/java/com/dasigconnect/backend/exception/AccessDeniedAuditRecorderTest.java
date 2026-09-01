package com.dasigconnect.backend.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.AuditLogService;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AccessDeniedAuditRecorderTest {

    @Mock AuditLogService auditLogService;
    @Mock HttpServletRequest request;

    private AccessDeniedAuditRecorder recorder;
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        recorder = new AccessDeniedAuditRecorder(auditLogService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        JwtUserDetails principal = new JwtUserDetails(actorId, "u@e.com", "contributor", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_CONTRIBUTOR"))));
    }

    @Test
    void authenticatedDenial_writesAccessDeniedRow_withNormalizedPath() {
        authenticate();
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/submissions/" + UUID.randomUUID() + "/reschedule");

        recorder.record(request, "Only admins may reschedule");

        verify(auditLogService).recordByActorId(
                eq(actorId), eq("ACCESS_DENIED"), any(), any(), isNull(), any());
    }

    @Test
    void unauthenticatedRequest_isIgnored() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        recorder.record(request, "method-security");

        verify(auditLogService, never()).recordByActorId(any(), any(), any(), any(), any(), any());
    }

    @Test
    void repeatDenial_sameActorMethodPath_isThrottledWithinWindow() {
        authenticate();
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/admin/resolution/42");

        recorder.record(request, "method-security");
        recorder.record(request, "method-security");
        recorder.record(request, "method-security");

        verify(auditLogService, times(1)).recordByActorId(
                eq(actorId), eq("ACCESS_DENIED"), any(), any(), isNull(), any());
    }

    @Test
    void normalize_collapsesUuidAndNumericSegments() {
        assertThat(AccessDeniedAuditRecorder.normalize(
                "/api/v1/submissions/" + UUID.randomUUID() + "/media/12"))
                .isEqualTo("/api/v1/submissions/:id/media/:id");
        assertThat(AccessDeniedAuditRecorder.normalize("/")).isEqualTo("/");
    }
}
