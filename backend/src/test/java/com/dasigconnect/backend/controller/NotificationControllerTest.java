package com.dasigconnect.backend.controller;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.notification.NotificationDto;
import com.dasigconnect.backend.model.entity.Notification;
import com.dasigconnect.backend.model.entity.NotificationEventType;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.NotificationService;
import com.dasigconnect.backend.service.TenantScopeService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JWTService jwtService;

    @MockitoBean
    private TenantScopeService tenantScopeService;

    @Test
    void list_withoutAuth_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void list_authenticated_returnsNotifications() throws Exception {
        Notification notification = new Notification();
        UUID notificationId = UUID.randomUUID();
        notification.setId(notificationId);
        notification.setEventType(NotificationEventType.submission_pending);
        notification.setMessage("New submission pending review.");
        notification.setDeepLink("/dashboard");
        NotificationDto dto = NotificationDto.from(notification);

        when(notificationService.list(any())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$[0].eventType").value("submission_pending"))
                .andExpect(jsonPath("$[0].message").value("New submission pending review."))
                .andExpect(jsonPath("$[0].deepLink").value("/dashboard"));
    }

    @Test
    @WithMockUser
    void unreadCount_authenticated_returnsCount() throws Exception {
        when(notificationService.unreadCount(any())).thenReturn(4L);

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(4));
    }

    @Test
    @WithMockUser
    void markRead_authenticated_returns204() throws Exception {
        UUID notificationId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notificationId))
                .andExpect(status().isNoContent());

        verify(notificationService).markRead(eq(notificationId), any());
    }

    @Test
    @WithMockUser
    void markAllRead_authenticated_returns204() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/read-all"))
                .andExpect(status().isNoContent());

        verify(notificationService).markAllRead(any());
    }

    @Test
    @WithMockUser
    void stream_authenticated_returns200() throws Exception {
        when(notificationService.subscribe(any())).thenReturn(new SseEmitter(0L));

        mockMvc.perform(get("/api/v1/notifications/stream"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }
}
