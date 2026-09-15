package com.dasigconnect.backend.service;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Media object storage, accessed through the S3-compatible API.
 *
 * <p>The concrete host (Cloudflare R2, Supabase Storage, MinIO, plain S3, …) is
 * an implementation/config detail — any S3-compatible endpoint works. Callers
 * depend only on this type.
 *
 * <p>The browser uploads file bytes directly to a short-lived presigned PUT URL
 * ({@link #createSignedUploadUrl}). The URL stored in the database and used for
 * {@code <img>} tags, Claude Vision input, and downloads is {@link #getPublicUrl}
 * — deliberately NOT a direct R2 URL. R2's "Public Development URL" (the
 * {@code pub-*.r2.dev} host {@code app.r2.public-base-url} used to point at)
 * is documented by Cloudflare as unfit for production and can be disabled or
 * silently rotated to a new hash at any time; when that happened here, every
 * previously-stored asset URL died at once (DNS stopped resolving for the old
 * host) and every media preview in the app broke simultaneously. Instead,
 * {@code getPublicUrl} now returns an address on this app's own backend
 * ({@code app.backend.public-base-url} + {@code /api/v1/media-files/<key>}),
 * served by {@code MediaProxyController} which streams the bytes from R2
 * server-side via {@link #downloadObject}. The tradeoff is that every media
 * request now round-trips through this backend instead of being served
 * directly from Cloudflare's edge — acceptable at this project's scale, and
 * immune to the R2 dev-URL failure mode entirely.
 *
 * <p>Configured via {@code app.r2.*} (kept as the stable config key namespace so
 * existing {@code R2_*} environment variables keep working). {@code app.r2.public-base-url}
 * itself is now used only to recognize pre-existing stored URLs from before this
 * change, for {@link #deletePublicObject} to still resolve them back to an object key.
 */
@Service
public class MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);
    private static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(15);

    /** How long a bucket-usage scan is reused before another {@code ListObjectsV2} sweep. */
    private static final Duration USAGE_CACHE_TTL = Duration.ofMinutes(10);
    /** Safety cap so a very large bucket cannot make the scan run unbounded. */
    private static final int USAGE_SCAN_MAX_PAGES = 200; // 200 * 1000 keys

    /**
     * Real bucket footprint from an object listing: summed object sizes and count.
     * {@code partial} is true when the safety cap stopped the sweep early.
     */
    public record StorageUsage(long totalBytes, long objectCount, boolean partial, Instant scannedAt) {}

    /** Path prefix under this app's own backend that {@link MediaProxyController} serves. */
    public static final String MEDIA_PROXY_PATH = "/api/v1/media-files/";

    private final String bucket;
    private final String publicBaseUrl;
    private final String backendPublicBaseUrl;
    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final boolean configured;
    private final AtomicReference<StorageUsage> cachedUsage = new AtomicReference<>();

    public MediaStorageService(
            @Value("${app.r2.account-id:}") String accountId,
            @Value("${app.r2.endpoint:}") String endpoint,
            @Value("${app.r2.access-key-id:}") String accessKeyId,
            @Value("${app.r2.secret-access-key:}") String secretAccessKey,
            @Value("${app.r2.bucket:dasigconnect-media}") String bucket,
            @Value("${app.r2.public-base-url:}") String publicBaseUrl,
            @Value("${app.backend.public-base-url:http://localhost:8080}") String backendPublicBaseUrl) {

        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
        this.backendPublicBaseUrl = backendPublicBaseUrl.replaceAll("/$", "");

        String resolvedEndpoint = endpoint.isBlank() && !accountId.isBlank()
                ? "https://" + accountId + ".r2.cloudflarestorage.com"
                : endpoint.replaceAll("/$", "");

        this.configured = !resolvedEndpoint.isBlank()
                && !accessKeyId.isBlank()
                && !secretAccessKey.isBlank();

        if (!configured) {
            log.warn("Media storage is not configured; media upload/delete will fail until app.r2.* is set.");
            this.s3Client = null;
            this.presigner = null;
            return;
        }

        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        // R2 recommends path-style access and ignores the region, but the SDK requires one.
        S3Configuration serviceConfig = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(resolvedEndpoint))
                .region(Region.of("auto"))
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfig)
                .build();

        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(resolvedEndpoint))
                .region(Region.of("auto"))
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfig)
                .build();
    }

    public boolean isConfigured() {
        return configured;
    }

    @PreDestroy
    void shutdown() {
        if (s3Client != null) {
            s3Client.close();
        }
        if (presigner != null) {
            presigner.close();
        }
    }

    /**
     * Presigned PUT URL the browser uploads the raw file to. Content-Type is
     * deliberately left unsigned so the browser can send whatever it likes
     * without triggering a SignatureDoesNotMatch failure.
     */
    public String createSignedUploadUrl(String objectPath) {
        requireConfigured();
        try {
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectPath)
                    .build();

            PresignedPutObjectRequest presigned = presigner.presignPutObject(
                    PutObjectPresignRequest.builder()
                            .signatureDuration(UPLOAD_URL_TTL)
                            .putObjectRequest(objectRequest)
                            .build());

            return presigned.url().toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create signed upload URL: " + ex.getMessage(), ex);
        }
    }

    /**
     * The URL the browser/AI clients actually fetch — served by
     * {@code MediaProxyController} on this backend, not directly from R2. See
     * the class-level javadoc for why (R2's dev URL is not stable).
     */
    public String getPublicUrl(String objectPath) {
        return backendPublicBaseUrl + MEDIA_PROXY_PATH + objectPath;
    }

    /** Downloaded object bytes plus the content type/length R2 reported for it. */
    public record StoredObject(byte[] content, String contentType, Long contentLength) {}

    public static class MediaObjectNotFoundException extends RuntimeException {
        public MediaObjectNotFoundException(String objectPath) {
            super("Media object not found: " + objectPath);
        }
    }

    /**
     * Fetches an object's bytes from R2 server-side, for {@code MediaProxyController}
     * to stream back to the browser. Loads the whole object into memory (acceptable
     * at this project's upload size cap — 50 MB — and traffic scale; a true
     * streaming response would be the next step if that stops being true).
     */
    public StoredObject downloadObject(String objectPath) {
        requireConfigured();
        try {
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectPath)
                    .build());
            String contentType = object.response().contentType();
            return new StoredObject(
                    object.asByteArray(),
                    contentType != null && !contentType.isBlank() ? contentType : "application/octet-stream",
                    object.response().contentLength());
        } catch (NoSuchKeyException ex) {
            throw new MediaObjectNotFoundException(objectPath);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to download object from media storage: " + ex.getMessage(), ex);
        }
    }

    public String uploadPublicObject(String objectPath, byte[] content, String contentType) {
        requireConfigured();
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectPath)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(content));
            return getPublicUrl(objectPath);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to upload object to media storage: " + ex.getMessage(), ex);
        }
    }

    public String generatedWatermarkPath(UUID submissionId, UUID mediaAssetId, String extension) {
        return "generated/watermarked/" + submissionId + "/" + mediaAssetId + "-" + System.currentTimeMillis() + "." + extension;
    }

    /**
     * Real bucket footprint (summed object sizes + count) from an S3
     * {@code ListObjectsV2} sweep of the whole bucket — this counts every object
     * actually stored, including watermarked derivatives and orphans that have no
     * {@code media_assets} row. The result is cached for {@link #USAGE_CACHE_TTL}
     * because the sweep is O(object count). Returns empty when storage is not
     * configured or the listing fails, so callers can fall back to a DB estimate.
     */
    public Optional<StorageUsage> probeUsage() {
        if (!configured) {
            return Optional.empty();
        }
        StorageUsage cached = cachedUsage.get();
        if (cached != null && cached.scannedAt().isAfter(Instant.now().minus(USAGE_CACHE_TTL))) {
            return Optional.of(cached);
        }
        try {
            long totalBytes = 0;
            long objectCount = 0;
            boolean partial = false;
            String continuationToken = null;
            int pages = 0;
            do {
                ListObjectsV2Response page = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .maxKeys(1000)
                        .continuationToken(continuationToken)
                        .build());
                for (S3Object object : page.contents()) {
                    totalBytes += object.size() == null ? 0 : object.size();
                    objectCount++;
                }
                continuationToken = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
                if (++pages >= USAGE_SCAN_MAX_PAGES && continuationToken != null) {
                    partial = true;
                    continuationToken = null;
                }
            } while (continuationToken != null);

            StorageUsage usage = new StorageUsage(totalBytes, objectCount, partial, Instant.now());
            cachedUsage.set(usage);
            return Optional.of(usage);
        } catch (Exception ex) {
            log.warn("Media storage usage probe failed: {}", ex.getMessage());
            return Optional.ofNullable(cached); // serve a stale reading if we have one, else empty
        }
    }

    /**
     * Cheap connectivity probe for the health dashboard: a one-key
     * {@code ListObjectsV2} against the bucket. Returns the bucket name on
     * success; throws on any credential / endpoint / permission failure so the
     * caller can surface the reason. Not cached — it is meant to reflect the
     * live state each time System Health is opened.
     */
    public String pingBucket() {
        requireConfigured();
        s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucket)
                .maxKeys(1)
                .build());
        return bucket;
    }

    public boolean deletePublicObject(String publicUrl) {
        if (!configured) {
            log.warn("Media storage is not configured; skipping object purge.");
            return false;
        }
        String objectPath = objectPathFromPublicUrl(publicUrl);
        if (objectPath == null || objectPath.isBlank()) {
            log.warn("Could not derive object key from URL; skipping object purge.");
            return false;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectPath).build());
            return true;
        } catch (NoSuchKeyException ex) {
            log.info("Storage object already missing during purge: {}", objectPath);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to purge storage object {}: {}", objectPath, ex.getMessage());
            return false;
        }
    }

    private String objectPathFromPublicUrl(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return null;
        }
        // Current format: this backend's proxy URL.
        int proxyIdx = publicUrl.indexOf(MEDIA_PROXY_PATH);
        if (proxyIdx >= 0) {
            return publicUrl.substring(proxyIdx + MEDIA_PROXY_PATH.length());
        }
        // Legacy: a direct R2 public-base-url URL, from before the proxy switch
        // (either not yet backfilled, or app.r2.public-base-url was set when
        // this asset's row was written).
        if (!publicBaseUrl.isBlank()) {
            String prefix = publicBaseUrl + "/";
            if (publicUrl.startsWith(prefix)) {
                return publicUrl.substring(prefix.length());
            }
        }
        // Fallback: strip scheme + host, keep the path (covers any other legacy/custom-domain URL).
        int schemeIdx = publicUrl.indexOf("://");
        if (schemeIdx < 0) {
            return null;
        }
        int pathIdx = publicUrl.indexOf('/', schemeIdx + 3);
        return pathIdx < 0 ? null : publicUrl.substring(pathIdx + 1);
    }

    private void requireConfigured() {
        if (!configured) {
            throw new IllegalStateException("Media storage is not configured.");
        }
    }
}
