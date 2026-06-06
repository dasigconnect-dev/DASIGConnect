package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.media.RepositoryHealthDto;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.RepositoryHealthCounts;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * UC-4.12 Phase 7C: tenant-scoped Repository Health aggregates.
 *
 * <p>Scope resolution mirrors {@code MediaAssetService.list}: an administrator with an explicit
 * {@code institutionId} sees that institution; an administrator without one sees the whole
 * network; a validator sees only their own institution. Contributors are excluded at the
 * controller. A short in-process TTL cache shields the single aggregate query from
 * dashboard-refresh spam without holding a connection.
 */
@Service
public class RepositoryHealthService {

    /** Assets are flagged "approaching purge" once they fall inside this window before purge. */
    private static final long APPROACHING_PURGE_WINDOW_DAYS = 7;
    private static final long CACHE_TTL_MILLIS = 30_000;

    private final MediaAssetRepository mediaAssetRepository;
    private final long retentionDays;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(long at, RepositoryHealthDto dto) {
    }

    public RepositoryHealthService(
            MediaAssetRepository mediaAssetRepository,
            @Value("${app.media-assets.deleted-retention-days:30}") long retentionDays) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.retentionDays = retentionDays;
    }

    @Transactional(readOnly = true)
    public RepositoryHealthDto health(UUID requestedInstitutionId, JwtUserDetails user) {
        boolean admin = isAdmin(user);
        UUID effectiveInstitution;
        String scope;
        if (admin) {
            effectiveInstitution = requestedInstitutionId; // null => network-wide
            scope = effectiveInstitution == null ? "network" : "institution";
        } else {
            if (user.institutionId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Institution-scoped user required to view repository health.");
            }
            if (requestedInstitutionId != null && !requestedInstitutionId.equals(user.institutionId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You cannot view repository health for another institution.");
            }
            effectiveInstitution = user.institutionId();
            scope = "institution";
        }

        String key = scope + ":" + (effectiveInstitution == null ? "network" : effectiveInstitution);
        long now = System.currentTimeMillis();
        Cached cached = cache.get(key);
        if (cached != null && now - cached.at() < CACHE_TTL_MILLIS) {
            return cached.dto();
        }

        Instant purgeSoonCutoff = Instant.now()
                .minus(Duration.ofDays(Math.max(retentionDays - APPROACHING_PURGE_WINDOW_DAYS, 0)));
        RepositoryHealthCounts counts =
                mediaAssetRepository.aggregateRepositoryHealth(effectiveInstitution, purgeSoonCutoff);
        RepositoryHealthDto dto = RepositoryHealthDto.from(scope, effectiveInstitution, counts);
        cache.put(key, new Cached(now, dto));
        return dto;
    }

    private boolean isAdmin(JwtUserDetails user) {
        return user.role() != null && user.role().toLowerCase(Locale.ROOT).contains("admin");
    }
}
