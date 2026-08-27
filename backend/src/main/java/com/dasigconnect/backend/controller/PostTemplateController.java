package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.model.dto.template.PostTemplateDto;
import com.dasigconnect.backend.model.dto.template.PostTemplateRequestDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.PostTemplateService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/post-templates")
public class PostTemplateController {

    private final PostTemplateService service;

    public PostTemplateController(PostTemplateService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<PostTemplateDto>>> list(
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(service.list(user)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PostTemplateDto>> create(
            @Valid @RequestBody PostTemplateRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.status(201).body(ApiResponse.success(service.create(dto, user)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        service.delete(id, user);
        return ResponseEntity.noContent().build();
    }
}
