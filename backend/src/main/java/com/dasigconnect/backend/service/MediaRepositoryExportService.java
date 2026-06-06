package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.media.MediaExportRecordDto;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetRelation;
import com.dasigconnect.backend.model.entity.MediaAssetRights;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetRelationRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaAssetRightsRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * UC-4.12 Phase 7G: portable, Dublin Core-aligned inventory export. Tenant-scoped (administrators
 * see the network or one institution; validators see their own). The export deliberately omits
 * {@code storage_url} and any signed/private URL or credential, contains no cross-tenant rows, and
 * is recorded as an audited administrative event.
 */
@Service
public class MediaRepositoryExportService {

    private static final Logger log = LoggerFactory.getLogger(MediaRepositoryExportService.class);

    /** Documented Dublin Core mapping, embedded in JSON exports for portability. */
    private static final Map<String, String> DUBLIN_CORE_MAPPING = Map.ofEntries(
            Map.entry("identifier", "asset_code"),
            Map.entry("title", "human-confirmed title"),
            Map.entry("creator", "uploader email and rights holder (distinguished)"),
            Map.entry("date", "created_at"),
            Map.entry("subject", "manual tags plus confirmed AI category"),
            Map.entry("rights", "rights record (basis/expiry/state) plus visibility"),
            Map.entry("relation", "lineage links (parents and children)"),
            Map.entry("format", "file type and size in bytes"));

    public record ExportPayload(String filename, String contentType, String body) {
    }

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAssetRightsRepository rightsRepository;
    private final MediaAssetRelationRepository relationRepository;
    private final AssetTagRepository assetTagRepository;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public MediaRepositoryExportService(MediaAssetRepository mediaAssetRepository,
                                        MediaAssetRightsRepository rightsRepository,
                                        MediaAssetRelationRepository relationRepository,
                                        AssetTagRepository assetTagRepository,
                                        AuditLogService auditLogService,
                                        UserRepository userRepository,
                                        ObjectMapper objectMapper) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.rightsRepository = rightsRepository;
        this.relationRepository = relationRepository;
        this.assetTagRepository = assetTagRepository;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ExportPayload export(String format, UUID requestedInstitutionId, JwtUserDetails user) {
        String fmt = format == null ? "csv" : format.trim().toLowerCase(Locale.ROOT);
        if (!fmt.equals("csv") && !fmt.equals("json")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "format must be csv or json.");
        }
        UUID institutionId = resolveScope(requestedInstitutionId, user);

        List<MediaAsset> assets = institutionId == null
                ? mediaAssetRepository.findAllActive()
                : mediaAssetRepository.findActiveByInstitution(institutionId);
        List<UUID> ids = assets.stream().map(MediaAsset::getId).toList();

        Map<UUID, String> codeById = new LinkedHashMap<>();
        for (MediaAsset a : assets) {
            codeById.put(a.getId(), a.getAssetCode());
        }
        Map<UUID, List<String>> manualTags = manualTagsByAsset(ids);
        Map<UUID, MediaAssetRights> rightsByAsset = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            for (MediaAssetRights r : rightsRepository.findByAssetIdIn(ids)) {
                rightsByAsset.put(r.getAssetId(), r);
            }
        }
        Map<UUID, List<String>> parentsByChild = new LinkedHashMap<>();
        Map<UUID, List<String>> childrenByParent = new LinkedHashMap<>();
        for (MediaAssetRelation rel : relationRepository.findForExport(institutionId)) {
            String parentCode = codeById.getOrDefault(rel.getParentAssetId(), rel.getParentAssetId().toString());
            String childCode = codeById.getOrDefault(rel.getChildAssetId(), rel.getChildAssetId().toString());
            parentsByChild.computeIfAbsent(rel.getChildAssetId(), k -> new ArrayList<>())
                    .add(parentCode + ":" + rel.getRelationType());
            childrenByParent.computeIfAbsent(rel.getParentAssetId(), k -> new ArrayList<>())
                    .add(childCode + ":" + rel.getRelationType());
        }

        LocalDate today = LocalDate.now();
        List<MediaExportRecordDto> records = new ArrayList<>(assets.size());
        for (MediaAsset a : assets) {
            records.add(toRecord(a, rightsByAsset.get(a.getId()),
                    manualTags.getOrDefault(a.getId(), List.of()),
                    parentsByChild.getOrDefault(a.getId(), List.of()),
                    childrenByParent.getOrDefault(a.getId(), List.of()),
                    today));
        }

        String scope = institutionId == null ? "network" : "institution";
        String body = fmt.equals("csv") ? toCsv(records) : toJson(scope, institutionId, records);
        String ext = fmt.equals("csv") ? "csv" : "json";
        String filename = "media-inventory-" + scope + "-"
                + DateTimeFormatter.ofPattern("yyyyMMdd").format(today) + "." + ext;
        String contentType = fmt.equals("csv") ? "text/csv" : "application/json";

        audit(user, fmt, scope, institutionId, records.size());
        return new ExportPayload(filename, contentType, body);
    }

    private Map<UUID, List<String>> manualTagsByAsset(List<UUID> ids) {
        Map<UUID, List<String>> map = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (Object[] row : assetTagRepository.findLabelsAndSourcesByMediaAssetIds(ids)) {
            UUID assetId = (UUID) row[0];
            String label = (String) row[1];
            String source = row[2] == null ? "manual" : (String) row[2];
            if ("manual".equalsIgnoreCase(source)) {
                map.computeIfAbsent(assetId, k -> new ArrayList<>()).add(label);
            }
        }
        return map;
    }

    private MediaExportRecordDto toRecord(MediaAsset a, MediaAssetRights rights, List<String> tags,
                                          List<String> parents, List<String> children, LocalDate today) {
        return new MediaExportRecordDto(
                a.getAssetCode(),
                a.getTitle(),
                a.getUploader() == null ? null : a.getUploader().getEmail(),
                rights == null ? null : rights.getRightsHolder(),
                a.getCreatedAt() == null ? null : a.getCreatedAt().toString(),
                String.join("; ", tags),
                a.getAiCategory(),
                rights == null ? "unknown" : rights.getRightsBasis(),
                a.getVisibility(),
                rights == null || rights.getExpiresAt() == null ? null : rights.getExpiresAt().toString(),
                rights == null ? "incomplete" : rights.derivedState(today),
                String.join("; ", parents),
                String.join("; ", children),
                a.getFileType() == null ? null : a.getFileType().name(),
                a.getFileSizeBytes(),
                a.getContentSha256(),
                a.getIntegrityStatus() == null ? null : a.getIntegrityStatus().name(),
                a.getCuratedAt() == null ? null : a.getCuratedAt().toString(),
                a.getAiClassificationModel(),
                a.getStatus() == null ? null : a.getStatus().name());
    }

    private String toCsv(List<MediaExportRecordDto> records) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", MediaExportRecordDto.CSV_HEADERS)).append("\r\n");
        for (MediaExportRecordDto r : records) {
            String[] row = r.toRow();
            for (int i = 0; i < row.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(csv(row[i]));
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    private String toJson(String scope, UUID institutionId, List<MediaExportRecordDto> records) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("generatedAt", Instant.now().toString());
        envelope.put("scope", scope);
        envelope.put("institutionId", institutionId);
        envelope.put("count", records.size());
        envelope.put("dublinCoreMapping", DUBLIN_CORE_MAPPING);
        envelope.put("assets", records);
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize export.");
        }
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void audit(JwtUserDetails user, String format, String scope, UUID institutionId, int count) {
        try {
            User actor = userRepository.findById(user.userId()).orElse(null);
            Map<String, Object> metadata = Map.of(
                    "format", format,
                    "scope", scope,
                    "institutionId", String.valueOf(institutionId),
                    "count", count);
            auditLogService.record(actor, "MEDIA_REPOSITORY_EXPORTED", null, null, institutionId, metadata);
        } catch (Exception ex) {
            log.warn("Failed to audit repository export: {}", ex.getMessage());
        }
    }

    private UUID resolveScope(UUID requestedInstitutionId, JwtUserDetails user) {
        boolean admin = user.role() != null && user.role().toLowerCase(Locale.ROOT).contains("admin");
        if (admin) {
            return requestedInstitutionId; // null => network-wide
        }
        if (user.institutionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Institution-scoped user required to export the inventory.");
        }
        if (requestedInstitutionId != null && !requestedInstitutionId.equals(user.institutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You cannot export another institution's inventory.");
        }
        return user.institutionId();
    }
}
