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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final ZoneId PHT_ZONE = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter PHT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).withZone(PHT_ZONE);

    private final AuditLogRepository auditLogRepository;
    private final AuditLogWriter auditLogWriter;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final InstitutionRepository institutionRepository;
    private final FacebookPageTokenRepository facebookPageTokenRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            AuditLogWriter auditLogWriter,
            SubmissionRepository submissionRepository,
            UserRepository userRepository,
            MediaAssetRepository mediaAssetRepository,
            InstitutionRepository institutionRepository,
            FacebookPageTokenRepository facebookPageTokenRepository,
            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.auditLogWriter = auditLogWriter;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.institutionRepository = institutionRepository;
        this.facebookPageTokenRepository = facebookPageTokenRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Writes one audit entry in its own ({@code REQUIRES_NEW}) transaction so a
     * failure here can never roll back — or be rolled back by — the business
     * action that triggered it. Persistence failures are logged and swallowed: a
     * missing audit row is bad, but blocking the underlying admin action because
     * of it would be worse. Not {@code @Transactional} itself — the new
     * transaction lives in {@link AuditLogWriter} so this try/catch can catch a
     * failed commit too.
     */
    public AuditLog record(
            User actor,
            String action,
            String ipAddress,
            String userAgent,
            UUID resourceId,
            Map<String, ?> metadata) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setActor(actor);
            auditLog.setAction(action);
            auditLog.setIpAddress(ipAddress);
            auditLog.setUserAgent(userAgent);
            auditLog.setResourceId(resourceId);
            auditLog.setMetadata(toJson(metadata));
            return auditLogWriter.save(auditLog);
        } catch (RuntimeException ex) {
            log.warn("Failed to write audit entry action={} resourceId={}: {}",
                    action, resourceId, ex.toString());
            return null;
        }
    }

    public AuditLog recordSystemAction(String action, UUID resourceId, Map<String, ?> metadata) {
        return record(null, action, null, null, resourceId, metadata);
    }

    /**
     * Convenience for controllers that only hold the authenticated principal's
     * id (from the JWT) rather than the {@link User} entity.
     */
    public AuditLog recordByActorId(UUID actorId, String action, String ipAddress,
                                    String userAgent, UUID resourceId, Map<String, ?> metadata) {
        User actor = actorId != null ? userRepository.findById(actorId).orElse(null) : null;
        return record(actor, action, ipAddress, userAgent, resourceId, metadata);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> searchAuditLogs(AuditLogFilterCriteria criteria, Pageable pageable) {
        Specification<AuditLog> spec = buildSpecification(criteria);
        Page<AuditLog> page;
        try {
            page = auditLogRepository.findAll(spec, pageable);
        } catch (RuntimeException ex) {
            log.error("Audit log query failed (criteria={}): {}", criteria, ex.toString(), ex);
            throw ex;
        }
        Lookups lookups = buildLookups(page.getContent());
        return page.map(entry -> mapToDtoSafe(entry, lookups));
    }

    /**
     * Batch-resolves every actor and referenced entity for a page of audit rows
     * in a handful of {@code IN (...)} queries instead of one findById per row —
     * the old per-row lookups made a 20-row page dozens of round trips to the
     * (remote) database, slow enough that the client would give up mid-response.
     */
    private Lookups buildLookups(List<AuditLog> rows) {
        Set<UUID> actorIds = new HashSet<>();
        Map<AuditEntityType, Set<UUID>> byType = new HashMap<>();
        for (AuditLog row : rows) {
            if (row.getActor() != null && row.getActor().getId() != null) {
                actorIds.add(row.getActor().getId());
            }
            if (row.getResourceId() != null) {
                byType.computeIfAbsent(AuditEntityType.fromAction(row.getAction()), k -> new HashSet<>())
                        .add(row.getResourceId());
            }
        }

        Map<UUID, User> users = new HashMap<>();
        Set<UUID> userIds = new HashSet<>(actorIds);
        userIds.addAll(byType.getOrDefault(AuditEntityType.USER, Set.of()));
        if (!userIds.isEmpty()) {
            userRepository.findAllByIdWithInstitution(userIds).forEach(u -> users.put(u.getId(), u));
        }

        return new Lookups(
                users,
                indexById(byType.get(AuditEntityType.SUBMISSION), submissionRepository::findAllById, Submission::getId),
                indexById(byType.get(AuditEntityType.MEDIA_ASSET), mediaAssetRepository::findAllById, MediaAsset::getId),
                indexById(byType.get(AuditEntityType.INSTITUTION), institutionRepository::findAllById, Institution::getId));
    }

    private <T> Map<UUID, T> indexById(Set<UUID> ids,
                                       Function<Collection<UUID>, List<T>> loader,
                                       Function<T, UUID> idOf) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return loader.apply(ids).stream().collect(Collectors.toMap(idOf, Function.identity(), (a, b) -> a));
    }

    private record Lookups(
            Map<UUID, User> users,
            Map<UUID, Submission> submissions,
            Map<UUID, MediaAsset> mediaAssets,
            Map<UUID, Institution> institutions) {}

    /** Never let one unmappable row fail the whole page. */
    private AuditLogDto mapToDtoSafe(AuditLog entry, Lookups lookups) {
        try {
            return mapToDto(entry, lookups);
        } catch (RuntimeException ex) {
            UUID id = entry != null ? entry.getId() : null;
            String action = entry != null ? entry.getAction() : null;
            log.warn("Failed to map audit entry id={} action={}: {}", id, action, ex.toString());
            AuditLogCategory category = AuditLogCategory.fromAction(action);
            return new AuditLogDto(
                    id,
                    entry != null ? entry.getCreatedAt() : null,
                    action,
                    formatActionLabel(action),
                    category,
                    category.getLabel(),
                    null,
                    new AuditLogDto.EntityRefDto(null, AuditEntityType.SYSTEM,
                            AuditEntityType.SYSTEM.getLabel(), "—", true, null),
                    new AuditLogDto.ClientInfoDto(null, null),
                    formatActionLabel(action),
                    Collections.emptyMap(),
                    entry != null ? entry.getMetadata() : null,
                    Collections.emptyList());
        }
    }

    @Transactional(readOnly = true)
    public String exportAuditLogsCsv(AuditLogFilterCriteria criteria) {
        Specification<AuditLog> spec = buildSpecification(criteria);
        // Limit export to top 5,000 for safety
        List<AuditLog> entries = auditLogRepository.findAll(spec);
        if (entries.size() > 5000) {
            entries = entries.subList(0, 5000);
        }

        Lookups lookups = buildLookups(entries);

        StringBuilder sb = new StringBuilder();
        // CSV Header
        sb.append("Log ID,Timestamp (PHT),Actor Name,Actor Email,Actor Role,Action Category,Action Type,Entity Type,Entity ID,Entity Reference,Summary / Justification,IP Address\n");

        for (AuditLog entry : entries) {
            AuditLogDto dto = mapToDtoSafe(entry, lookups);
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
                actions.addAll(List.of("USER_STATUS_UPDATED", "USER_AVATAR_UPDATED", "USER_ROLE_CHANGED", "USER_REMOVED", "USER_DELETED", "USER_ANONYMIZED",
                        "SUPER_ADMIN_TRANSFERRED", "ADMIN_OWNER_TRANSFERRED", "ADMIN_TRANSFER_REQUESTED", "ADMIN_OWNER_TRANSFER_REQUESTED", "ADMIN_TRANSFER_CONFIRMED",
                        "CONTRIBUTOR_REASSIGNED", "INVITATION_SENT", "INVITATION_ACCEPTED", "INVITATION_REVOKED",
                        "PASSWORD_RESET", "PASSWORD_CHANGED", "LOGIN_SUCCESS", "LOGIN_FAILED", "LOGOUT"));
            }
            case INSTITUTION_MANAGEMENT -> {
                actions.addAll(List.of("INSTITUTION_CREATED", "INSTITUTION_UPDATED", "INSTITUTION_LOGO_UPDATED", "INSTITUTION_DEACTIVATED", "INSTITUTION_REACTIVATED", "INSTITUTION_PENDING", "INSTITUTION_ACTIVATED", "INSTITUTION_INACTIVE", "INSTITUTION_DELETED"));
            }
            case MEDIA_LIFECYCLE -> {
                actions.addAll(List.of("MEDIA_ASSET_UPLOADED", "MEDIA_ASSET_MOVED", "MEDIA_ASSET_DELETED", "MEDIA_ASSET_PURGED", "MEDIA_ASSET_TAG_ADDED", "MEDIA_ASSET_TAG_REMOVED", "MEDIA_ALBUM_CREATED", "MEDIA_ALBUM_UPDATED", "MEDIA_ALBUM_DELETED"));
            }
            case CONFIGURATION -> {
                actions.addAll(List.of("WATERMARK_CONFIG_UPDATED", "WATERMARK_OVERRIDE_REMOVED", "GUARD_RAIL_CONFIG_UPDATED", "PAGE_SETTINGS_UPDATED", "BACKGROUND_JOB_RUN"));
            }
            case SECURITY -> {
                actions.addAll(List.of("TOKEN_REAUTHORIZED", "TOKEN_EXPIRED", "TOKEN_EXPIRED_BLOCKED", "TOKEN_REVOKED", "ACCOUNT_LOCKED",
                        "AUDIT_LOG_EXPORTED", "ANALYTICS_EXPORTED", "SYSTEM_HEALTH_EXPORTED"));
            }
            case OTHER -> {}
        }
        return actions;
    }

    private AuditLogDto mapToDto(AuditLog logEntry, Lookups lookups) {
        String action = logEntry.getAction();
        AuditLogCategory category = AuditLogCategory.fromAction(action);
        String categoryLabel = category.getLabel();
        String actionLabel = formatActionLabel(action);

        // Actor resolution (from the batch-loaded map, never a lazy proxy)
        AuditLogDto.ActorDto actorDto = null;
        UUID actorId = logEntry.getActor() != null ? logEntry.getActor().getId() : null;
        User u = actorId != null ? lookups.users().get(actorId) : null;
        if (u != null) {
            String name = resolveUserName(u);
            String role = u.getRole() != null ? u.getRole().name() : "user";
            String instName = u.getInstitution() != null ? u.getInstitution().getName() : null;
            actorDto = new AuditLogDto.ActorDto(u.getId(), name, u.getEmail(), role, u.isAdminOwner(), null, instName);
        }

        // Entity resolution
        AuditLogDto.EntityRefDto entityDto = resolveEntity(logEntry.getResourceId(), action, lookups);

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

    private AuditLogDto.EntityRefDto resolveEntity(UUID resourceId, String action, Lookups lookups) {
        AuditEntityType type = AuditEntityType.fromAction(action);
        if (resourceId == null) {
            return new AuditLogDto.EntityRefDto(null, type, type.getLabel(), "System Record", true, null);
        }

        switch (type) {
            case SUBMISSION -> {
                Submission s = lookups.submissions().get(resourceId);
                if (s != null) {
                    String label = s.getEventTitle() != null && !s.getEventTitle().isBlank()
                            ? s.getEventTitle()
                            : (s.getCaption() != null && s.getCaption().length() > 30 ? s.getCaption().substring(0, 30) + "..." : "Submission #" + resourceId.toString().substring(0, 8));
                    return new AuditLogDto.EntityRefDto(resourceId, type, type.getLabel(), label, true, "/submissions?id=" + resourceId);
                }
                return new AuditLogDto.EntityRefDto(resourceId, type, type.getLabel(), "[Entity no longer available]", false, null);
            }
            case USER -> {
                User u = lookups.users().get(resourceId);
                if (u != null) {
                    return new AuditLogDto.EntityRefDto(resourceId, type, type.getLabel(), resolveUserName(u), true, "/admin/moderator-management");
                }
                return new AuditLogDto.EntityRefDto(resourceId, type, type.getLabel(), "[Entity no longer available]", false, null);
            }
            case MEDIA_ASSET -> {
                MediaAsset m = lookups.mediaAssets().get(resourceId);
                if (m != null) {
                    String label = m.getFileName() != null ? m.getFileName() : "Media Asset #" + resourceId.toString().substring(0, 8);
                    return new AuditLogDto.EntityRefDto(resourceId, type, type.getLabel(), label, true, "/media-repository");
                }
                return new AuditLogDto.EntityRefDto(resourceId, type, type.getLabel(), "[Entity no longer available]", false, null);
            }
            case INSTITUTION -> {
                Institution inst = lookups.institutions().get(resourceId);
                if (inst != null) {
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
            case MEDIA_ALBUM -> {
                return new AuditLogDto.EntityRefDto(resourceId, type, type.getLabel(), "Media folder", true, "/media-repository");
            }
            default -> {
                return new AuditLogDto.EntityRefDto(resourceId, type, type.getLabel(), "Entity #" + resourceId.toString().substring(0, 8), true, null);
            }
        }
    }

    /** Plain-English name for an action code, shown as the row title in the audit log. */
    private String formatActionLabel(String action) {
        if (action == null) return "Unknown action";
        return switch (action) {
            // ── Submissions & review ──
            case "SUBMISSION_CREATED" -> "Draft created";
            case "SUBMISSION_UPDATED" -> "Draft edited";
            case "SUBMISSION_SUBMITTED" -> "Submitted for review";
            case "SUBMISSION_WITHDRAWN" -> "Submission withdrawn to draft";
            case "SUBMISSION_APPROVED", "APPROVED", "approved" -> "Approved & scheduled";
            case "SUBMISSION_EDITED_AND_APPROVED", "EDITED_AND_APPROVED", "edited_and_approved" -> "Edited during review, then approved";
            case "edited" -> "Edited during review";
            case "SUBMISSION_REJECTED", "REJECTED", "rejected" -> "Rejected";
            case "SUBMISSION_REVISION_REQUESTED", "REVISION_REQUESTED", "needs_revision" -> "Revision requested";
            case "SUBMISSION_RESCHEDULED", "RESCHEDULE" -> "Rescheduled";
            case "SUBMISSION_PUBLISHED" -> "Published to Facebook";
            case "PUBLISH_FAILED" -> "Publishing failed";
            // ── Guard-rail overrides ──
            case "SCHEDULE_GUARDRAIL_OVERRIDE" -> "Guard rail overridden (schedule edit)";
            case "ADMIN_RESCHEDULE_OVERRIDE" -> "Guard rail overridden (calendar reschedule)";
            case "MANUAL_PUBLISH_RETRY_OVERRIDE" -> "Guard rail overridden (failed-post retry)";
            case "GUARD_RAIL_CONFIG_UPDATED" -> "Guard rail settings changed";
            // ── Manual publishing ──
            case "MANUAL_PUBLISH_STARTED" -> "Manual publish started";
            case "MANUAL_PUBLISH_COMPLETE" -> "Manual publish completed";
            case "MANUAL_PUBLISH_CANCELLED" -> "Manual publish cancelled";
            case "MANUAL_PUBLISH_ABANDONED" -> "Manual publish abandoned (timed out)";
            case "MANUAL_PUBLISH_RETRY_NEW_SCHEDULE" -> "Failed post re-queued on a new slot";
            case "MISSED_REVIEW_RETRY_NEW_SCHEDULE" -> "Missed-review post sent back for approval";
            // ── Accounts & access ──
            case "LOGIN_SUCCESS" -> "Signed in";
            case "LOGIN_FAILED" -> "Failed sign-in attempt";
            case "ACCOUNT_LOCKED" -> "Account locked after repeated failed sign-ins";
            case "ACCESS_DENIED" -> "Access denied";
            case "LOGOUT" -> "Signed out";
            case "PASSWORD_CHANGED" -> "Password changed";
            case "PASSWORD_RESET" -> "Password reset via email link";
            case "USER_ROLE_CHANGED" -> "Account role changed";
            case "USER_STATUS_UPDATED" -> "Account status changed";
            case "USER_AVATAR_UPDATED" -> "Profile photo updated";
            case "USER_SETTINGS_UPDATED" -> "Notification preferences updated";
            case "USER_REMOVED" -> "Account deactivated";
            case "USER_DELETED" -> "Account permanently deleted";
            case "USER_ANONYMIZED" -> "Personal data erased";
            case "INVITATION_SENT" -> "Invitation sent";
            case "INVITATION_ACCEPTED" -> "Invitation accepted";
            case "INVITATION_REVOKED" -> "Invitation cancelled";
            case "ADMIN_TRANSFER_REQUESTED", "ADMIN_OWNER_TRANSFER_REQUESTED" -> "Admin ownership transfer requested";
            case "ADMIN_TRANSFER_CONFIRMED", "ADMIN_OWNER_TRANSFERRED", "SUPER_ADMIN_TRANSFERRED" -> "Admin ownership transferred";
            // ── Institutions ──
            case "INSTITUTION_CREATED" -> "Institution added";
            case "INSTITUTION_UPDATED" -> "Institution details changed";
            case "INSTITUTION_LOGO_UPDATED" -> "Institution logo changed";
            case "INSTITUTION_DEACTIVATED", "INSTITUTION_INACTIVE" -> "Institution deactivated";
            case "INSTITUTION_REACTIVATED", "INSTITUTION_ACTIVATED" -> "Institution reactivated";
            case "INSTITUTION_PENDING" -> "Institution set to pending";
            case "INSTITUTION_DELETED" -> "Institution deleted";
            case "CONTRIBUTOR_REASSIGNED" -> "Contributor moved to another institution";
            // ── Media ──
            case "MEDIA_ASSET_UPLOADED" -> "Media uploaded";
            case "MEDIA_ASSET_DELETED" -> "Media deleted";
            case "MEDIA_BULK_DELETED" -> "Media deleted in bulk";
            case "MEDIA_ASSET_MOVED" -> "Media moved to another folder";
            case "MEDIA_ASSET_TAG_ADDED" -> "Media tag added";
            case "MEDIA_ASSET_TAG_REMOVED" -> "Media tag removed";
            case "MEDIA_ASSET_PURGED" -> "Deleted media permanently purged (retention)";
            case "MEDIA_ALBUM_CREATED" -> "Folder created";
            case "MEDIA_ALBUM_UPDATED" -> "Folder renamed / moved";
            case "MEDIA_ALBUM_DELETED" -> "Folder deleted";
            // ── Config, tokens, system ──
            case "PAGE_SETTINGS_UPDATED" -> "Facebook Page settings changed";
            case "WATERMARK_CONFIG_UPDATED" -> "Watermark settings changed";
            case "WATERMARK_OVERRIDE_REMOVED" -> "Per-institution watermark removed";
            case "TOKEN_REAUTHORIZED" -> "Facebook token re-authenticated";
            case "TOKEN_REVOKED" -> "Facebook token revoked";
            case "TOKEN_EXPIRED", "TOKEN_EXPIRED_BLOCKED" -> "Facebook token expired";
            case "AUDIT_LOG_EXPORTED" -> "Audit log exported";
            case "ANALYTICS_EXPORTED" -> "Analytics report exported";
            case "SYSTEM_HEALTH_EXPORTED" -> "System health snapshot exported";
            case "BACKGROUND_JOB_RUN" -> "Background job run on demand";
            default -> {
                // Fallback: "SOME_ACTION_CODE" -> "Some action code"
                String s = action.replace('_', ' ').toLowerCase(Locale.ENGLISH).trim();
                yield s.isEmpty() ? "System action" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
            }
        };
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

    /**
     * A one-line, plain-English account of what happened — meant to be readable
     * on its own in the audit log row (the actor and timestamp are shown
     * separately, so the sentence stays action-focused).
     */
    private String generateSummary(String action, Map<String, Object> meta, AuditLogDto.EntityRefDto entity) {
        String what = entity != null && entity.label() != null ? "'" + entity.label() + "'" : "this item";
        Map<String, Object> m = meta == null ? Collections.emptyMap() : meta;
        String reason = firstNonBlank(m.get("overrideReason"), m.get("reason"), m.get("decisionReason"));

        return switch (action) {
            case "SUBMISSION_APPROVED", "APPROVED", "approved",
                 "SUBMISSION_EDITED_AND_APPROVED", "EDITED_AND_APPROVED", "edited_and_approved" -> {
                String slot = fmtSlot(m.get("scheduledAt"));
                yield "Approved " + what + (slot != null ? ", scheduled for " + slot : "")
                        + (action.toUpperCase().contains("EDIT") ? " (edited during review)" : "");
            }
            case "edited" -> "Edited " + what + " during review";
            case "SUBMISSION_REJECTED", "REJECTED", "rejected" -> {
                String r = firstNonBlank(m.get("rejectionReason"), m.get("reasonCode"), m.get("remarks"));
                yield "Rejected " + what + (r != null ? " — " + r : "");
            }
            case "SUBMISSION_REVISION_REQUESTED", "REVISION_REQUESTED", "needs_revision" -> {
                String r = firstNonBlank(m.get("remarks"), m.get("reason"));
                yield "Sent " + what + " back for changes" + (r != null ? " — " + r : "");
            }
            case "SUBMISSION_RESCHEDULED", "RESCHEDULE", "MANUAL_PUBLISH_RETRY_NEW_SCHEDULE", "MISSED_REVIEW_RETRY_NEW_SCHEDULE" -> {
                String from = fmtSlot(m.get("originalSlot"));
                String to = fmtSlot(firstNonBlank(m.get("newSlot"), m.get("scheduledAt")));
                yield "Moved " + what + (from != null && to != null ? " from " + from + " to " + to
                        : to != null ? " to " + to : "");
            }
            case "SCHEDULE_GUARDRAIL_OVERRIDE", "ADMIN_RESCHEDULE_OVERRIDE", "MANUAL_PUBLISH_RETRY_OVERRIDE" -> {
                String to = fmtSlot(firstNonBlank(m.get("newSlot"), m.get("scheduledAt")));
                String rule = str(m.get("violations"));
                yield "Bypassed a guard rail to schedule " + what + (to != null ? " for " + to : "")
                        + (rule != null && !rule.isBlank() ? " (" + rule + ")" : "")
                        + (reason != null ? " — reason: " + reason : "");
            }
            case "SUBMISSION_PUBLISHED" -> "Published " + what + " to the DASIG Facebook Page"
                    + (m.get("platformPostUrl") != null ? " (" + m.get("platformPostUrl") + ")" : "");
            case "PUBLISH_FAILED" -> {
                String err = firstNonBlank(m.get("error"), m.get("errorDetail"));
                yield "Automated publishing of " + what + " failed" + (err != null ? " — " + err : "");
            }
            case "USER_ROLE_CHANGED" -> "Changed " + userLabel(m, what) + "'s role"
                    + (m.get("fromRole") != null && m.get("toRole") != null
                        ? " from " + m.get("fromRole") + " to " + m.get("toRole") : "");
            case "USER_STATUS_UPDATED" -> "Set " + userLabel(m, what) + "'s account to " + firstNonBlank(m.get("accountState"), m.get("status"), "a new status");
            case "USER_REMOVED", "USER_DELETED", "USER_ANONYMIZED" ->
                    formatActionLabel(action) + " — " + userLabel(m, what);
            case "INVITATION_SENT" -> "Invited " + firstNonBlank(m.get("recipientEmail"), m.get("email"), what)
                    + (m.get("assignedRole") != null ? " as " + m.get("assignedRole") : "");
            case "ADMIN_TRANSFER_REQUESTED", "ADMIN_OWNER_TRANSFER_REQUESTED",
                 "ADMIN_TRANSFER_CONFIRMED", "ADMIN_OWNER_TRANSFERRED", "SUPER_ADMIN_TRANSFERRED" ->
                    formatActionLabel(action) + (m.get("toEmail") != null ? " to " + m.get("toEmail") : "");
            case "CONTRIBUTOR_REASSIGNED" -> "Moved " + userLabel(m, what) + " from "
                    + firstNonBlank(m.get("fromInstitutionName"), "their institution") + " to "
                    + firstNonBlank(m.get("toInstitutionName"), "another institution");
            case "MANUAL_PUBLISH_COMPLETE" -> "Manually published " + what
                    + (m.get("postUrl") != null ? " (" + m.get("postUrl") + ")" : "");
            case "MEDIA_ASSET_PURGED" -> "Permanently removed a soft-deleted media asset and its stored file after the retention window";
            case "MEDIA_ASSET_TAG_ADDED", "MEDIA_ASSET_TAG_REMOVED" ->
                    formatActionLabel(action) + (m.get("label") != null ? ": " + m.get("label") : "") + " on " + what;
            case "ACCOUNT_LOCKED" -> "Locked " + firstNonBlank(m.get("email"), what)
                    + " for 15 minutes after " + firstNonBlank(m.get("failedAttempts"), "5") + " failed sign-in attempts";
            case "LOGIN_FAILED" -> "Failed sign-in for " + firstNonBlank(m.get("email"), "an account")
                    + (m.get("reason") != null ? " — " + m.get("reason") : "");
            case "ACCESS_DENIED" -> "Blocked " + firstNonBlank(m.get("method"), "a request") + " "
                    + firstNonBlank(m.get("path"), "a protected endpoint")
                    + (m.get("reason") != null ? " — " + m.get("reason") : "");
            case "MEDIA_BULK_DELETED" -> "Deleted " + firstNonBlank(m.get("count"), "several")
                    + " media asset(s) in one bulk action";
            case "AUDIT_LOG_EXPORTED", "ANALYTICS_EXPORTED", "SYSTEM_HEALTH_EXPORTED" ->
                    formatActionLabel(action) + (m.get("rowCount") != null ? " (" + m.get("rowCount") + " rows)" : "")
                    + (m.get("filters") != null ? " with filters " + m.get("filters") : "");
            case "BACKGROUND_JOB_RUN" -> "Ran the '" + firstNonBlank(m.get("jobKey"), "background") + "' job on demand";
            case "GUARD_RAIL_CONFIG_UPDATED", "PAGE_SETTINGS_UPDATED", "WATERMARK_CONFIG_UPDATED" ->
                    formatActionLabel(action) + (entity != null && entity.label() != null ? " for '" + entity.label() + "'" : "");
            default -> {
                if (reason != null) yield formatActionLabel(action) + " — reason: " + reason;
                if (m.get("remarks") != null) yield formatActionLabel(action) + " — " + m.get("remarks");
                yield formatActionLabel(action) + (entity != null && entity.label() != null ? " — " + entity.label() : "");
            }
        };
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String firstNonBlank(Object... vals) {
        for (Object v : vals) {
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty()) return s;
            }
        }
        return null;
    }

    private static String userLabel(Map<String, Object> m, String fallback) {
        return firstNonBlank(m.get("targetEmail"), m.get("email"), m.get("recipientEmail"), fallback);
    }

    /** "2026-08-30T20:00:00Z" -> "Aug 30, 2026 8:00 PM UTC"; leaves non-timestamps untouched. */
    private static String fmtSlot(Object raw) {
        String s = str(raw);
        if (s == null || s.isBlank()) return null;
        try {
            return java.time.ZonedDateTime.parse(s.contains("T") ? s : s + "T00:00:00Z")
                    .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a 'UTC'"));
        } catch (RuntimeException ex) {
            return s;
        }
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
