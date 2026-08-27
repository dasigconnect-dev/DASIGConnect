package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.audit.AuditEntityType;
import com.dasigconnect.backend.model.dto.audit.AuditLogCategory;
import com.dasigconnect.backend.model.dto.audit.AuditLogDto;
import com.dasigconnect.backend.model.dto.audit.AuditLogFilterCriteria;
import com.dasigconnect.backend.model.entity.AuditLog;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AuditLogRepository;
import com.dasigconnect.backend.repository.FacebookPageTokenRepository;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final ZoneId PHT_ZONE = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter PHT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).withZone(PHT_ZONE);

    private final AuditLogRepository auditLogRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final InstitutionRepository institutionRepository;
    private final FacebookPageTokenRepository facebookPageTokenRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            SubmissionRepository submissionRepository,
            UserRepository userRepository,
            MediaAssetRepository mediaAssetRepository,
            InstitutionRepository institutionRepository,
            FacebookPageTokenRepository facebookPageTokenRepository,
            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.institutionRepository = institutionRepository;
        this.facebookPageTokenRepository = facebookPageTokenRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog record(
            User actor,
            String action,
            String ipAddress,
            String userAgent,
            UUID resourceId,
            Map<String, ?> metadata) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActor(actor);
        auditLog.setAction(action);
        auditLog.setIpAddress(ipAddress);
        auditLog.setUserAgent(userAgent);
        auditLog.setResourceId(resourceId);
        auditLog.setMetadata(toJson(metadata));
        return auditLogRepository.save(auditLog);
    }

    public AuditLog recordSystemAction(String action, UUID resourceId, Map<String, ?> metadata) {
        return record(null, action, null, null, resourceId, metadata);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> searchAuditLogs(AuditLogFilterCriteria criteria, Pageable pageable) {
        Specification<AuditLog> spec = buildSpecification(criteria);
        Page<AuditLog> page = auditLogRepository.findAll(spec, pageable);
        return page.map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public String exportAuditLogsCsv(AuditLogFilterCriteria criteria) {
        Specification<AuditLog> spec = buildSpecification(criteria);
        // Limit export to top 5,000 for safety
        List<AuditLog> entries = auditLogRepository.findAll(spec);
        if (entries.size() > 5000) {
            entries = entries.subList(0, 5000);
        }

        StringBuilder sb = new StringBuilder();
        // CSV Header
        sb.append("Log ID,Timestamp (PHT),Actor Name,Actor Email,Actor Role,Action Category,Action Type,Entity Type,Entity ID,Entity Reference,Summary / Justification,IP Address\n");

        for (AuditLog entry : entries) {
            AuditLogDto dto = mapToDto(entry);
            sb.append(escapeCsv(dto.id() != null ? dto.id().toString() : "")).append(",");
            sb.append(escapeCsv(dto.timestamp() != null ? PHT_FORMATTER.format(dto.timestamp()) : "")).append(",");
            sb.append(escapeCsv(dto.actor() != null ? dto.actor().name() : "System / Automated")).append(",");
            sb.append(escapeCsv(dto.actor() != null ? dto.actor().email() : "system@dasigconnect.gov.ph")).append(",");
            sb.append(escapeCsv(dto.actor() != null ? dto.actor().role() : "SYSTEM")).append(",");
            sb.append(escapeCsv(dto.categoryLabel())).append(",");
            sb.append(escapeCsv(dto.action() != null ? dto.action() : dto.actionLabel())).append(",");
            sb.append(escapeCsv(dto.entity() != null ? dto.entity().typeLabel() : "System")).append(",");
            sb.append(escapeCsv(dto.entity() != null && dto.entity().id() != null ? dto.entity().id().toString() : "")).append(",");
            sb.append(escapeCsv(dto.entity() != null ? dto.entity().label() : "—")).append(",");
            sb.append(escapeCsv(dto.summary() != null ? dto.summary() : "—")).append(",");
            sb.append(escapeCsv(dto.clientInfo() != null && dto.clientInfo().ipAddress() != null ? dto.clientInfo().ipAddress() : "—")).append("\n");
        }

        return sb.toString();
    }

    public Map<String, Object> getMetadataOptions() {
        Map<String, Object> options = new HashMap<>();

        List<Map<String, String>> categories = new ArrayList<>();
        for (AuditLogCategory cat : AuditLogCategory.values()) {
            categories.add(Map.of("key", cat.name(), "label", cat.getLabel()));
        }
        options.put("categories", categories);

        List<Map<String, String>> entityTypes = new ArrayList<>();
        for (AuditEntityType type : AuditEntityType.values()) {
            entityTypes.add(Map.of("key", type.name(), "label", type.getLabel()));
        }
        options.put("entityTypes", entityTypes);

        return options;
    }

    private Specification<AuditLog> buildSpecification(AuditLogFilterCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria == null) {
                return cb.conjunction();
            }

            if (criteria.startDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), criteria.startDate()));
            }

            if (criteria.endDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), criteria.endDate()));
            }

            if (criteria.actorId() != null) {
                predicates.add(cb.equal(root.get("actor").get("id"), criteria.actorId()));
            }

            if (criteria.actorQuery() != null && !criteria.actorQuery().isBlank()) {
                String term = "%" + criteria.actorQuery().trim().toLowerCase() + "%";
                Join<AuditLog, User> actorJoin = root.join("actor", JoinType.LEFT);
                Predicate emailMatch = cb.like(cb.lower(actorJoin.get("email")), term);
                Predicate firstMatch = cb.like(cb.lower(actorJoin.get("firstName")), term);
                Predicate lastMatch = cb.like(cb.lower(actorJoin.get("lastName")), term);
                Predicate displayMatch = cb.like(cb.lower(actorJoin.get("displayName")), term);
                predicates.add(cb.or(emailMatch, firstMatch, lastMatch, displayMatch));
            }

            if (criteria.action() != null && !criteria.action().isBlank()) {
                predicates.add(cb.equal(root.get("action"), criteria.action().trim()));
            }

            if (criteria.category() != null) {
                List<String> matchingActions = getActionsForCategory(criteria.category());
                if (!matchingActions.isEmpty()) {
                    predicates.add(root.get("action").in(matchingActions));
                }
            }

            if (criteria.resourceId() != null) {
                predicates.add(cb.equal(root.get("resourceId"), criteria.resourceId()));
            }

            if (criteria.search() != null && !criteria.search().isBlank()) {
                String term = "%" + criteria.search().trim().toLowerCase() + "%";
                Join<AuditLog, User> actorJoin = root.join("actor", JoinType.LEFT);
                Predicate emailMatch = cb.like(cb.lower(actorJoin.get("email")), term);
                Predicate nameMatch = cb.like(cb.lower(actorJoin.get("firstName")), term);
                Predicate actionMatch = cb.like(cb.lower(root.get("action")), term);
                Predicate ipMatch = cb.like(cb.lower(root.get("ipAddress")), term);
                predicates.add(cb.or(emailMatch, nameMatch, actionMatch, ipMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<String> getActionsForCategory(AuditLogCategory category) {
        // Collect recognized action codes by category
        List<String> actions = new ArrayList<>();
        switch (category) {
            case APPROVAL -> {
                actions.addAll(List.of("APPROVED", "SUBMISSION_APPROVED", "TIMEOUT_APPROVED_AS_FALLBACK", "OVERRIDE_APPROVED", "DIRECT_POST_CREATED"));
            }
            case REJECTION -> {
                actions.addAll(List.of("REJECTED", "SUBMISSION_REJECTED", "TIMEOUT_REJECTED_ON_BEHALF", "OVERRIDE_DENIED"));
            }
            case EDIT_AND_REVISION -> {
                actions.addAll(List.of("EDITED_AND_APPROVED", "SUBMISSION_EDITED_AND_APPROVED", "REVISION_REQUESTED", "SUBMISSION_REVISION_REQUESTED", "SUBMISSION_UPDATED"));
            }
            case RESCHEDULE_AND_OVERRIDE -> {
                actions.addAll(List.of("SUBMISSION_RESCHEDULED", "OVERRIDE_SLOT_SUGGESTED", "TIMEOUT_DEFERRED"));
            }
            case PUBLISHING -> {
                actions.addAll(List.of("MANUAL_PUBLISH_STARTED", "MANUAL_PUBLISH_COMPLETE", "MANUAL_PUBLISH_CANCELLED", "MANUAL_PUBLISH_ABANDONED", "MANUAL_PUBLISH_RETRY_OVERRIDE", "MANUAL_PUBLISH_RETRY_NEW_SCHEDULE", "MISSED_REVIEW_RETRY_NEW_SCHEDULE", "SUBMISSION_PUBLISHED", "PUBLISH_FAILED", "TOKEN_REAUTHORIZED"));
            }
            case ACCOUNT_MANAGEMENT -> {
                actions.addAll(List.of("USER_STATUS_UPDATED", "USER_AVATAR_UPDATED", "USER_REMOVED", "USER_DELETED", "SUPER_ADMIN_TRANSFERRED", "INVITATION_SENT", "INVITATION_ACCEPTED", "INVITATION_REVOKED", "PASSWORD_RESET", "PASSWORD_CHANGED", "LOGIN_SUCCESS", "LOGIN_FAILED", "LOGOUT"));
            }
            case INSTITUTION_MANAGEMENT -> {
                actions.addAll(List.of("INSTITUTION_CREATED", "INSTITUTION_UPDATED", "INSTITUTION_LOGO_UPDATED", "INSTITUTION_DEACTIVATED", "INSTITUTION_REACTIVATED", "INSTITUTION_PENDING", "INSTITUTION_ACTIVATED", "INSTITUTION_INACTIVE", "INSTITUTION_DELETED"));
            }
            case MEDIA_LIFECYCLE -> {
                actions.addAll(List.of("MEDIA_ASSET_UPLOADED", "MEDIA_ASSET_MOVED", "MEDIA_ASSET_DELETED", "MEDIA_ASSET_TAG_ADDED", "MEDIA_ASSET_TAG_REMOVED", "MEDIA_ALBUM_CREATED", "MEDIA_ALBUM_UPDATED", "MEDIA_ALBUM_DELETED"));
            }
            case CONFIGURATION -> {
                actions.addAll(List.of("WATERMARK_CONFIG_UPDATED", "WATERMARK_OVERRIDE_REMOVED", "GUARD_RAIL_CONFIG_UPDATED", "PAGE_SETTINGS_UPDATED"));
            }
            case SECURITY -> {
                actions.addAll(List.of("TOKEN_REAUTHORIZED", "TOKEN_EXPIRED", "TOKEN_REVOKED"));
            }
            case OTHER -> {}
        }
        return actions;
    }

    private AuditLogDto mapToDto(AuditLog logEntry) {
        String action = logEntry.getAction();
        AuditLogCategory category = AuditLogCategory.fromAction(action);
        String categoryLabel = category.getLabel();
        String actionLabel = formatActionLabel(action);

        // Actor resolution
        AuditLogDto.ActorDto actorDto = null;
        if (logEntry.getActor() != null) {
            User u = logEntry.getActor();
            String name = resolveUserName(u);
            String role = u.getRole() != null ? u.getRole().name() : "USER";
            if (u.isSuperAdministrator()) {
                role = "SUPER_ADMINISTRATOR";
            }
            String instName = u.getInstitution() != null ? u.getInstitution().getName() : null;
            actorDto = new AuditLogDto.ActorDto(u.getId(), name, u.getEmail(), role, null, instName);
        }

        // Entity resolution
        AuditLogDto.EntityRefDto entityDto = resolveEntity(logEntry.getResourceId(), action);

        // Client info
        AuditLogDto.ClientInfoDto clientInfo = new AuditLogDto.ClientInfoDto(
                logEntry.getIpAddress(),
                logEntry.getUserAgent()
        );

        // Metadata parsing & diff extraction
        Map<String, Object> metaMap = parseMetadata(logEntry.getMetadata());
        List<AuditLogDto.AuditDiffEntryDto> diffs = extractDiffs(metaMap, action);
        String summary = generateSummary(action, metaMap, entityDto);

        return new AuditLogDto(
                logEntry.getId(),
                logEntry.getCreatedAt(),
                action,
                actionLabel,
                category,
                categoryLabel,
                actorDto,
                entityDto,
                clientInfo,
                summary,
                metaMap,
                logEntry.getMetadata(),
                diffs
        );
    }

    private String resolveUserName(User user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        String first = user.getFirstName() != null ? user.getFirstName() : "";
        String last = user.getLastName() != null ? user.getLastName() : "";
        String full = (first + " " + last).trim();
        return full.isBlank() ? user.getEmail() : full;
    }

    private AuditLogDto.EntityRefDto resolveEntity(UUID resourceId, String action) {
        AuditEntityType type = AuditEntityType.fromAction(action);
        if (resourceId == null) {
            return new AuditLogDto.EntityRefDto(null, type, type.getLabel(), "System Record", true, null);
        }

        switch (type) {
            case SUBMISSION -> {
                Optional<Submission> opt = submissionRepository.findById(resourceId);
                if (opt.isPresent()) {
                    Submission s = opt.get();
                    String label = s.getEventTitle() != null && !s.getEventTitle().isBlank()
                            ? s.getEventTitle()
                            : (s.getCaption() != null && s.getCaption().length() > 30 ? s.getCaption().substring(0, 30) + "..." : "Submission #" + resourceId.toString().substring(0, 8));
                    return new AuditLogDto.EntityRefDto(resourceId, type, type.getLabel(), label, true, "/submissions?id=" + resourceId);
                }
                return new AuditLogDto.EntityRefDto(resourceId, type, type.getLabel(), "[Entity no longer available]", false, null);
            }
            case USER -> {
                Optional<User> opt = userRepository.findById(resourceId);
                if (opt.isPresent()) {
                    User u = opt.get();
                    return new AuditLogDto.EntityRefDto(resourceId, type, type.getLabel(), resolveUserName(u), true, "/admin/administrator-management");
                }
                return new AuditLogDto.EntityRefDto(resourceId, type, type.getLabel(), "[Entity no longer available]", false, null);
            }
            case MEDIA_ASSET -> {
                Optional<MediaAsset> opt = mediaAssetRepository.findById(resourceId);
                if (opt.isPresent()) {
                    MediaAsset m = opt.get();
                    String label = m.getFileName() != null ? m.getFileName() : "Media Asset #" + resourceId.toString().substring(0, 8);
                    return new AuditLogDto.EntityRefDto(resourceId, type, type.getLabel(), label, true, "/media-repository");
                }
                return new AuditLogDto.EntityRefDto(resourceId, type, type.getLabel(), "[Entity no longer available]", false, null);
            }
            case INSTITUTION -> {
                Optional<Institution> opt = institutionRepository.findById(resourceId);
                if (opt.isPresent()) {
                    Institution inst = opt.get();
                    return new AuditLogDto.EntityRefDto(resourceId, type, type.getLabel(), inst.getName(), true, "/admin/institution-management");
                }
                return new AuditLogDto.EntityRefDto(resourceId, type, type.getLabel(), "[Entity no longer available]", false, null);
            }
            case FACEBOOK_TOKEN -> {
                return new AuditLogDto.EntityRefDto(resourceId, type, type.getLabel(), "Facebook Page Token #" + resourceId.toString().substring(0, 8), true, "/admin/system-health");
            }
            case WATERMARK_CONFIG -> {
                return new AuditLogDto.EntityRefDto(resourceId, type, type.getLabel(), "Watermark Configuration", true, "/settings");
            }
            default -> {
                return new AuditLogDto.EntityRefDto(resourceId, type, type.getLabel(), "Entity #" + resourceId.toString().substring(0, 8), true, null);
            }
        }
    }

    private String formatActionLabel(String action) {
        if (action == null) return "Unknown Action";
        return action.replace('_', ' ')
                .toLowerCase(Locale.ENGLISH)
                .replace("submission ", "")
                .replace("media asset ", "")
                .replace("institution ", "")
                .replace("user ", "")
                .trim();
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private List<AuditLogDto.AuditDiffEntryDto> extractDiffs(Map<String, Object> meta, String action) {
        List<AuditLogDto.AuditDiffEntryDto> diffs = new ArrayList<>();
        if (meta == null || meta.isEmpty()) return diffs;

        // Check if meta contains a "diff" or "editDiff" object
        Object editDiffObj = meta.get("editDiff");
        if (editDiffObj == null) {
            editDiffObj = meta.get("diff");
        }

        if (editDiffObj instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (entry.getValue() instanceof Map<?, ?> valueMap) {
                    String from = valueMap.get("from") != null ? String.valueOf(valueMap.get("from")) : "—";
                    String to = valueMap.get("to") != null ? String.valueOf(valueMap.get("to")) : "—";
                    diffs.add(new AuditLogDto.AuditDiffEntryDto(key, formatFieldLabel(key), from, to));
                } else {
                    diffs.add(new AuditLogDto.AuditDiffEntryDto(key, formatFieldLabel(key), "—", String.valueOf(entry.getValue())));
                }
            }
        } else if (editDiffObj instanceof String diffStr && diffStr.startsWith("{")) {
            try {
                JsonNode root = objectMapper.readTree(diffStr);
                root.fields().forEachRemaining(field -> {
                    JsonNode val = field.getValue();
                    String from = val.has("from") ? val.get("from").asText() : "—";
                    String to = val.has("to") ? val.get("to").asText() : "—";
                    diffs.add(new AuditLogDto.AuditDiffEntryDto(field.getKey(), formatFieldLabel(field.getKey()), from, to));
                });
            } catch (Exception ignored) {}
        }

        // Direct before/after checks for schedule / status changes
        if (meta.containsKey("originalScheduledAt") || meta.containsKey("newScheduledAt")) {
            String from = meta.get("originalScheduledAt") != null ? String.valueOf(meta.get("originalScheduledAt")) : "—";
            String to = meta.get("newScheduledAt") != null ? String.valueOf(meta.get("newScheduledAt")) : "—";
            diffs.add(new AuditLogDto.AuditDiffEntryDto("scheduledAt", "Scheduled Slot", from, to));
        }
        if (meta.containsKey("priorStatus") || meta.containsKey("newStatus")) {
            String from = meta.get("priorStatus") != null ? String.valueOf(meta.get("priorStatus")) : "—";
            String to = meta.get("newStatus") != null ? String.valueOf(meta.get("newStatus")) : "—";
            diffs.add(new AuditLogDto.AuditDiffEntryDto("status", "Status Transition", from, to));
        }
        if (meta.containsKey("oldRole") || meta.containsKey("newRole")) {
            String from = meta.get("oldRole") != null ? String.valueOf(meta.get("oldRole")) : "—";
            String to = meta.get("newRole") != null ? String.valueOf(meta.get("newRole")) : "—";
            diffs.add(new AuditLogDto.AuditDiffEntryDto("role", "Account Role", from, to));
        }

        return diffs;
    }

    private String generateSummary(String action, Map<String, Object> meta, AuditLogDto.EntityRefDto entity) {
        if (meta == null || meta.isEmpty()) {
            return formatActionLabel(action) + " on " + (entity != null ? entity.label() : "entity");
        }

        if (meta.containsKey("overrideReason") && meta.get("overrideReason") != null) {
            return "Override Justification: " + meta.get("overrideReason");
        }
        if (meta.containsKey("rejectionReason") && meta.get("rejectionReason") != null) {
            String reason = String.valueOf(meta.get("rejectionReason"));
            String remarks = meta.get("remarks") != null ? " (" + meta.get("remarks") + ")" : "";
            return "Rejected: " + reason + remarks;
        }
        if (meta.containsKey("remarks") && meta.get("remarks") != null) {
            return "Remarks: " + meta.get("remarks");
        }
        if (meta.containsKey("platformPostUrl") && meta.get("platformPostUrl") != null) {
            return "Published to Facebook: " + meta.get("platformPostUrl");
        }
        if (meta.containsKey("reason") && meta.get("reason") != null) {
            return "Reason: " + meta.get("reason");
        }
        if (meta.containsKey("label") && meta.get("label") != null) {
            return "Tag: " + meta.get("label");
        }
        return formatActionLabel(action);
    }

    private String formatFieldLabel(String key) {
        if (key == null) return "Field";
        return switch (key) {
            case "caption" -> "Post Caption";
            case "eventTitle" -> "Event Title";
            case "scheduledAt" -> "Scheduled Time";
            case "targetAudience" -> "Target Audience";
            case "institutionId" -> "Institution";
            case "accountState" -> "Account Status";
            default -> key.substring(0, 1).toUpperCase() + key.substring(1).replaceAll("([A-Z])", " $1");
        };
    }

    private String toJson(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Audit metadata must be JSON serializable", ex);
        }
    }

    private String escapeCsv(String val) {
        if (val == null) return "\"\"";
        String escaped = val.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
