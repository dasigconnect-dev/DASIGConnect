package com.dasigconnect.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the R2-dev-URL-outage fix: getPublicUrl now points at this backend's
 * own proxy instead of R2 directly, and deletePublicObject must still resolve
 * an object key from both the new proxy format and old, already-stored URLs
 * (proxy switch is not retroactive without a data backfill). Also covers the
 * later app.r2.custom-domain-url option, which bypasses the backend proxy
 * entirely (and this backend's own bandwidth with it) once a stable custom
 * domain is attached to the R2 bucket via Cloudflare.
 */
@ExtendWith(MockitoExtension.class)
class MediaStorageServiceTest {

    @Mock S3Client s3Client;

    private MediaStorageService build(String r2PublicBaseUrl, String backendPublicBaseUrl) {
        return build(r2PublicBaseUrl, backendPublicBaseUrl, "");
    }

    private MediaStorageService build(String r2PublicBaseUrl, String backendPublicBaseUrl, String customDomainUrl) {
        MediaStorageService service = new MediaStorageService(
                "account-id", "https://account-id.r2.cloudflarestorage.com",
                "access-key", "secret-key", "dasigconnect-media",
                r2PublicBaseUrl, backendPublicBaseUrl, customDomainUrl);
        ReflectionTestUtils.setField(service, "s3Client", s3Client);
        return service;
    }

    @Test
    void getPublicUrl_pointsAtThisBackendsProxy_notR2Directly() {
        MediaStorageService service = build("https://pub-oldhash.r2.dev", "https://api.dasigconnect.com");

        String url = service.getPublicUrl("media/inst-1/asset-1/photo.jpg");

        assertThat(url).isEqualTo("https://api.dasigconnect.com/api/v1/media-files/media/inst-1/asset-1/photo.jpg");
    }

    @Test
    void getPublicUrl_whenCustomDomainConfigured_pointsAtItDirectly_bypassingTheBackendProxy() {
        MediaStorageService service = build(
                "https://pub-oldhash.r2.dev", "https://api.dasigconnect.com", "https://media.dasigconnect.org");

        String url = service.getPublicUrl("media/inst-1/asset-1/photo.jpg");

        assertThat(url).isEqualTo("https://media.dasigconnect.org/media/inst-1/asset-1/photo.jpg");
    }

    @Test
    void deletePublicObject_customDomainFormat_stillExtractsKeyCorrectly() {
        MediaStorageService service = build(
                "https://pub-oldhash.r2.dev", "https://api.dasigconnect.com", "https://media.dasigconnect.org");

        boolean result = service.deletePublicObject(
                "https://media.dasigconnect.org/media/inst-1/asset-1/photo.jpg");

        assertThat(result).isTrue();
        verify(s3Client).deleteObject((DeleteObjectRequest) org.mockito.ArgumentMatchers.argThat(req ->
                ((DeleteObjectRequest) req).key().equals("media/inst-1/asset-1/photo.jpg")));
    }

    @Test
    void deletePublicObject_currentProxyFormat_extractsKeyCorrectly() {
        MediaStorageService service = build("https://pub-oldhash.r2.dev", "https://api.dasigconnect.com");

        boolean result = service.deletePublicObject(
                "https://api.dasigconnect.com/api/v1/media-files/media/inst-1/asset-1/photo.jpg");

        assertThat(result).isTrue();
        verify(s3Client).deleteObject((DeleteObjectRequest) org.mockito.ArgumentMatchers.argThat(req ->
                ((DeleteObjectRequest) req).key().equals("media/inst-1/asset-1/photo.jpg")
                        && ((DeleteObjectRequest) req).bucket().equals("dasigconnect-media")));
    }

    @Test
    void deletePublicObject_legacyR2Format_stillExtractsKeyCorrectly() {
        // Assets uploaded before the proxy switch still have the old R2 URL stored
        // until backfilled -- deletion must keep working for those too.
        MediaStorageService service = build("https://pub-oldhash.r2.dev", "https://api.dasigconnect.com");

        boolean result = service.deletePublicObject(
                "https://pub-oldhash.r2.dev/media/inst-1/asset-1/photo.jpg");

        assertThat(result).isTrue();
        verify(s3Client).deleteObject((DeleteObjectRequest) org.mockito.ArgumentMatchers.argThat(req ->
                ((DeleteObjectRequest) req).key().equals("media/inst-1/asset-1/photo.jpg")));
    }

    @Test
    void purgeExpiredGeneratedWatermarks_deletesOnlyObjectsOlderThanRetention() {
        MediaStorageService service = build("", "https://api.dasigconnect.com");
        Instant now = Instant.now();
        S3Object stale = S3Object.builder()
                .key("generated/watermarked/sub-1/asset-1-1000.jpg")
                .lastModified(now.minus(Duration.ofDays(10)))
                .build();
        S3Object fresh = S3Object.builder()
                .key("generated/watermarked/sub-2/asset-2-2000.jpg")
                .lastModified(now.minus(Duration.ofHours(1)))
                .build();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder().contents(List.of(stale, fresh)).isTruncated(false).build());

        int purged = service.purgeExpiredGeneratedWatermarks(Duration.ofDays(3));

        assertThat(purged).isEqualTo(1);
        verify(s3Client).deleteObject((DeleteObjectRequest) org.mockito.ArgumentMatchers.argThat(req ->
                ((DeleteObjectRequest) req).key().equals("generated/watermarked/sub-1/asset-1-1000.jpg")));
        verify(s3Client, never()).deleteObject((DeleteObjectRequest) org.mockito.ArgumentMatchers.argThat(req ->
                ((DeleteObjectRequest) req).key().equals("generated/watermarked/sub-2/asset-2-2000.jpg")));
    }

    @Test
    void purgeExpiredGeneratedWatermarks_onlySweepsTheGeneratedWatermarkPrefix() {
        MediaStorageService service = build("", "https://api.dasigconnect.com");
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder().contents(List.of()).isTruncated(false).build());

        service.purgeExpiredGeneratedWatermarks(Duration.ofDays(3));

        verify(s3Client).listObjectsV2((ListObjectsV2Request) org.mockito.ArgumentMatchers.argThat(req ->
                ((ListObjectsV2Request) req).prefix().equals("generated/watermarked/")));
    }

    @Test
    void purgeExpiredGeneratedWatermarks_oneObjectDeleteFailure_doesNotAbortTheSweep() {
        MediaStorageService service = build("", "https://api.dasigconnect.com");
        Instant now = Instant.now();
        S3Object first = S3Object.builder().key("generated/watermarked/a/1.jpg")
                .lastModified(now.minus(Duration.ofDays(10))).build();
        S3Object second = S3Object.builder().key("generated/watermarked/b/2.jpg")
                .lastModified(now.minus(Duration.ofDays(10))).build();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder().contents(List.of(first, second)).isTruncated(false).build());
        when(s3Client.deleteObject((DeleteObjectRequest) org.mockito.ArgumentMatchers.argThat(req ->
                ((DeleteObjectRequest) req).key().equals("generated/watermarked/a/1.jpg"))))
                .thenThrow(RuntimeException.class);

        int purged = service.purgeExpiredGeneratedWatermarks(Duration.ofDays(3));

        assertThat(purged).isEqualTo(1);
        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void downloadObject_notFound_throwsMediaObjectNotFoundException() {
        MediaStorageService service = build("", "https://api.dasigconnect.com");
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());

        assertThatThrownBy(() -> service.downloadObject("media/missing.png"))
                .isInstanceOf(MediaStorageService.MediaObjectNotFoundException.class);
    }

    @Test
    void downloadObject_found_returnsBytesAndContentType() {
        MediaStorageService service = build("", "https://api.dasigconnect.com");
        GetObjectResponse response = GetObjectResponse.builder()
                .contentType("image/png")
                .contentLength(4L)
                .build();
        ResponseBytes<GetObjectResponse> responseBytes =
                ResponseBytes.fromByteArray(response, new byte[] {1, 2, 3, 4});
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        var result = service.downloadObject("media/photo.png");

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.contentLength()).isEqualTo(4L);
        assertThat(result.content()).containsExactly(1, 2, 3, 4);
    }
}
