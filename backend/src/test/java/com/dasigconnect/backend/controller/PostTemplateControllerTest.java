package com.dasigconnect.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.template.PostTemplateDto;
import com.dasigconnect.backend.model.dto.template.PostTemplateRequestDto;
import com.dasigconnect.backend.model.entity.PostTemplate;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.AiAdoptionTrackingService;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.PostTemplateService;
import com.dasigconnect.backend.service.TenantScopeService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostTemplateController.class)
@Import(SecurityConfig.class)
class PostTemplateControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PostTemplateService postTemplateService;
    @MockitoBean private AiAdoptionTrackingService adoptionTrackingService;
    @MockitoBean private JWTService jwtService;
    @MockitoBean private TenantScopeService tenantScopeService;

    @Test
    void list_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/post-templates"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withAuthentication_returnsTemplates() throws Exception {
        UsernamePasswordAuthenticationToken auth = auth("contributor", UUID.randomUUID());
        PostTemplateDto template = templateDto("Event Announcement");
        when(postTemplateService.list((JwtUserDetails) auth.getPrincipal()))
                .thenReturn(List.of(template));

        mockMvc.perform(get("/api/v1/post-templates").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(template.getId().toString()))
                .andExpect(jsonPath("$.data[0].name").value("Event Announcement"))
                .andExpect(jsonPath("$.data[0].tags[0]").value("event"));
    }

    @Test
    void create_withInvalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/post-templates")
                        .with(authentication(auth("contributor", UUID.randomUUID())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"caption\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_fromAiDraft_returns201AndTracksSave() throws Exception {
        UUID institutionId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken auth = auth("contributor", institutionId);
        PostTemplateDto created = templateDto("AI Event Template");
        when(postTemplateService.create(
                any(PostTemplateRequestDto.class), eq((JwtUserDetails) auth.getPrincipal())))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/post-templates")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"AI Event Template","caption":"Join our event.",
                                 "tags":["event"],"source":"ai_top_posts"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("AI Event Template"));

        verify(adoptionTrackingService).recordTemplateDraft(institutionId, "saved");
    }

    @Test
    void create_trackingFailure_doesNotFailSave() throws Exception {
        UUID institutionId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken auth = auth("admin", institutionId);
        when(postTemplateService.create(any(), any())).thenReturn(templateDto("AI Template"));
        doThrow(new RuntimeException("analytics unavailable"))
                .when(adoptionTrackingService).recordTemplateDraft(institutionId, "saved");

        mockMvc.perform(post("/api/v1/post-templates")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"AI Template","caption":"Caption text",
                                 "source":"ai_top_posts"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void delete_withAuthentication_delegatesAndReturns204() throws Exception {
        UUID templateId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken auth = auth("moderator", null);

        mockMvc.perform(delete("/api/v1/post-templates/{id}", templateId)
                        .with(authentication(auth)))
                .andExpect(status().isNoContent());

        verify(postTemplateService).delete(templateId, (JwtUserDetails) auth.getPrincipal());
    }

    private static PostTemplateDto templateDto(String name) {
        PostTemplate template = new PostTemplate();
        template.setId(UUID.randomUUID());
        template.setName(name);
        template.setTarget("Facebook followers");
        template.setCaption("Join our upcoming event.");
        template.setTags("event, community");
        return PostTemplateDto.from(template);
    }

    private static UsernamePasswordAuthenticationToken auth(String role, UUID institutionId) {
        JwtUserDetails principal = new JwtUserDetails(
                UUID.randomUUID(), role + "@example.com", role, institutionId);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
    }
}
