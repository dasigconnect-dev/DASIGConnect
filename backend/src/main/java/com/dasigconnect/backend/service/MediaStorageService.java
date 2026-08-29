package com.dasigconnect.backend.service;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
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
 * ({@link #createSignedUploadUrl}); the URL stored in the database and used for
 * {@code <img>} tags, Claude Vision input, and downloads is the public read URL
 * ({@link #getPublicUrl}), served from the bucket's public development URL or a
 * connected custom domain.
 *
 * <p>Configured via {@code app.r2.*} (kept as the stable config key namespace so
 * existing {@code R2_*} environment variables keep working).
 */
@Service
public class MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);
    private static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(15);

    private final String bucket;
    private final String publicBaseUrl;
    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final boolean configured;

    public MediaStorageService(
            @Value("${app.r2.account-id:}") String accountId,
            @Value("${app.r2.endpoint:}") String endpoint,
            @Value("${app.r2.access-key-id:}") String accessKeyId,
            @Value("${app.r2.secret-access-key:}") String secretAccessKey,
            @Value("${app.r2.bucket:dasigconnect-media}") String bucket,
            @Value("${app.r2.public-base-url:}") String publicBaseUrl) {

        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");

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

    public String getPublicUrl(String objectPath) {
        return publicBaseUrl + "/" + objectPath;
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
        String prefix = publicBaseUrl + "/";
        if (publicUrl.startsWith(prefix)) {
            return publicUrl.substring(prefix.length());
        }
        // Fallback: strip scheme + host, keep the path (covers legacy/custom-domain URLs).
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
