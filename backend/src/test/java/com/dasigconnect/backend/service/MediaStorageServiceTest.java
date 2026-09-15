package com.dasigconnect.backend.service;

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
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the R2-dev-URL-outage fix: getPublicUrl now points at this backend's
 * own proxy instead of R2 directly, and deletePublicObject must still resolve
 * an object key from both the new proxy format and old, already-stored URLs
 * (proxy switch is not retroactive without a data backfill).
 */
@ExtendWith(MockitoExtension.class)
class MediaStorageServiceTest {

    @Mock S3Client s3Client;

    private MediaStorageService build(String r2PublicBaseUrl, String backendPublicBaseUrl) {
        MediaStorageService service = new MediaStorageService(
                "account-id", "https://account-id.r2.cloudflarestorage.com",
                "access-key", "secret-key", "dasigconnect-media",
                r2PublicBaseUrl, backendPublicBaseUrl);
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
