package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.model.dto.template.PostTemplateDto;
import com.dasigconnect.backend.model.dto.template.PostTemplateRequestDto;
import com.dasigconnect.backend.model.entity.PostTemplate;
import com.dasigconnect.backend.repository.PostTemplateRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

@ExtendWith(MockitoExtension.class)
class PostTemplateServiceTest {

    @Mock
    private PostTemplateRepository repository;

    private PostTemplateService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID institutionId = UUID.randomUUID();
    private JwtUserDetails user;

    @BeforeEach
    void setUp() {
        service = new PostTemplateService(repository);
        user = new JwtUserDetails(userId, "contributor@cit.edu.ph", "contributor", institutionId, false);
    }

    @Test
    void list_returnsOrderedTemplatesForUser() {
        PostTemplate t1 = new PostTemplate();
        t1.setId(UUID.randomUUID());
        t1.setOwnerUserId(userId);
        t1.setName("Template 1");
        t1.setCaption("Caption 1");
        t1.setCategory("Announcement");
        t1.setTarget("Feed");

        when(repository.findByOwnerUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(t1));

        List<PostTemplateDto> result = service.list(user);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Template 1");
        verify(repository).findByOwnerUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    void create_withValidData_normalizesAndSavesTemplate() {
        PostTemplateRequestDto req = new PostTemplateRequestDto();
        req.setName("  Welcome Post  ");
        req.setCaption("  Join us for our annual kickoff event!  ");
        req.setCategory("  Events  ");
        req.setTarget("  Facebook Page  ");
        req.setTags(List.of("tag1", "  tag2  ", "tag1", "tag3,extra", "", "   ", "tag4", "tag5", "tag6", "tag7", "tag8", "tag9"));
        req.setInstitutionId(null); // Should fall back to user institutionId

        when(repository.existsByOwnerUserIdAndNameIgnoreCase(userId, "Welcome Post")).thenReturn(false);
        when(repository.save(any(PostTemplate.class))).thenAnswer(invocation -> {
            PostTemplate saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        PostTemplateDto result = service.create(req, user);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Welcome Post");

        ArgumentCaptor<PostTemplate> captor = ArgumentCaptor.forClass(PostTemplate.class);
        verify(repository).save(captor.capture());
        PostTemplate captured = captor.getValue();

        assertThat(captured.getName()).isEqualTo("Welcome Post");
        assertThat(captured.getCaption()).isEqualTo("Join us for our annual kickoff event!");
        assertThat(captured.getCategory()).isEqualTo("Events");
        assertThat(captured.getTarget()).isEqualTo("Facebook Page");
        assertThat(captured.getInstitutionId()).isEqualTo(institutionId);
        assertThat(captured.getOwnerUserId()).isEqualTo(userId);

        // Tags should be trimmed, commas removed, deduplicated, and limited to 8
        assertThat(captured.getTags()).isNotNull();
        String[] tags = captured.getTags().split(",");
        assertThat(tags).hasSize(8);
        assertThat(tags).contains("tag1", "tag2", "tag3extra");
    }

    @Test
    void create_duplicateName_throwsConflict409() {
        PostTemplateRequestDto req = new PostTemplateRequestDto();
        req.setName("Duplicate Template");
        req.setCaption("Some caption");

        when(repository.existsByOwnerUserIdAndNameIgnoreCase(userId, "Duplicate Template")).thenReturn(true);

        assertThatThrownBy(() -> service.create(req, user))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void create_missingName_throwsIllegalArgumentException() {
        PostTemplateRequestDto req = new PostTemplateRequestDto();
        req.setName("   ");
        req.setCaption("Some caption");

        assertThatThrownBy(() -> service.create(req, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template name is required");
    }

    @Test
    void create_missingCaption_throwsIllegalArgumentException() {
        PostTemplateRequestDto req = new PostTemplateRequestDto();
        req.setName("Valid Name");
        req.setCaption(null);

        when(repository.existsByOwnerUserIdAndNameIgnoreCase(userId, "Valid Name")).thenReturn(false);

        assertThatThrownBy(() -> service.create(req, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Caption is required");
    }

    @Test
    void create_withNullOptionalFields_appliesFallbacks() {
        PostTemplateRequestDto req = new PostTemplateRequestDto();
        req.setName("Minimal Template");
        req.setCaption("Minimal caption");
        req.setTarget(null);
        req.setCategory(null);
        req.setTags(null);

        when(repository.existsByOwnerUserIdAndNameIgnoreCase(userId, "Minimal Template")).thenReturn(false);
        when(repository.save(any(PostTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(req, user);

        ArgumentCaptor<PostTemplate> captor = ArgumentCaptor.forClass(PostTemplate.class);
        verify(repository).save(captor.capture());
        PostTemplate captured = captor.getValue();

        assertThat(captured.getTarget()).isEqualTo("Saved from submission");
        assertThat(captured.getCategory()).isEqualTo("Custom");
        assertThat(captured.getTags()).isNull();
    }

    @Test
    void delete_existingTemplate_deletesSuccessfully() {
        UUID templateId = UUID.randomUUID();
        PostTemplate template = new PostTemplate();
        template.setId(templateId);
        template.setOwnerUserId(userId);

        when(repository.findByIdAndOwnerUserId(templateId, userId)).thenReturn(Optional.of(template));

        service.delete(templateId, user);

        verify(repository).delete(template);
    }

    @Test
    void delete_nonExistentTemplate_throwsNotFound404() {
        UUID templateId = UUID.randomUUID();
        when(repository.findByIdAndOwnerUserId(templateId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(templateId, user))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
