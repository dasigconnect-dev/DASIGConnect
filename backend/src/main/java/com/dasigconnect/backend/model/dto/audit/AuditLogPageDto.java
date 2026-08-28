package com.dasigconnect.backend.model.dto.audit;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Stable pagination envelope for the audit log. Mirrors the field names the
 * frontend already consumes (content / totalElements / totalPages / size /
 * number / first / last / empty) so we never serialize a raw {@code PageImpl},
 * whose JSON shape Spring Data does not guarantee across versions.
 */
public record AuditLogPageDto(
        List<AuditLogDto> content,
        long totalElements,
        int totalPages,
        int size,
        int number,
        boolean first,
        boolean last,
        boolean empty) {

    public static AuditLogPageDto from(Page<AuditLogDto> page) {
        return new AuditLogPageDto(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getSize(),
                page.getNumber(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty());
    }
}
