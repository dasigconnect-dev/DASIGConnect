package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.event.WatermarkApplicationFailedEvent;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
import com.dasigconnect.backend.model.entity.WatermarkConfiguration;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.WatermarkConfigurationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class WatermarkApplicationServiceTest {

    @Mock
    private WatermarkConfigurationRepository configurationRepository;

    @Mock
    private InstitutionRepository institutionRepository;

    @Mock
    private MediaStorageService storageService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private WatermarkApplicationService service;
    private HttpServer imageServer;
    private String imageUrl;
    private byte[] detailRichImageBytes;
    private String detailRichImageUrl;
    private String corruptImageUrl;

    @BeforeEach
    void setUp() throws Exception {
        service = new WatermarkApplicationService(
                configurationRepository,
                institutionRepository,
                storageService,
                new ObjectMapper(),
                auditLogService,
                eventPublisher);

        imageServer = HttpServer.create(new InetSocketAddress(0), 0);
        byte[] imageBytes = sampleImage();
        imageServer.createContext("/image.jpg", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, imageBytes.length);
            exchange.getResponseBody().write(imageBytes);
            exchange.close();
        });
        detailRichImageBytes = detailRichImage();
        imageServer.createContext("/detail-rich.png", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, detailRichImageBytes.length);
            exchange.getResponseBody().write(detailRichImageBytes);
            exchange.close();
        });
        byte[] corruptBytes = "not actually an image".getBytes();
        imageServer.createContext("/corrupt.jpg", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, corruptBytes.length);
            exchange.getResponseBody().write(corruptBytes);
            exchange.close();
        });
        imageServer.start();
        imageUrl = "http://localhost:" + imageServer.getAddress().getPort() + "/image.jpg";
        detailRichImageUrl = "http://localhost:" + imageServer.getAddress().getPort() + "/detail-rich.png";
        corruptImageUrl = "http://localhost:" + imageServer.getAddress().getPort() + "/corrupt.jpg";
    }

    @AfterEach
    void tearDown() {
        if (imageServer != null) {
            imageServer.stop(0);
        }
    }

    @Test
    void resolvePublishUrl_appliesEnabledWatermarkAndUploadsGeneratedImage() {
        UUID institutionId = UUID.randomUUID();
        Submission submission = submission(institutionId);
        SubmissionMediaAsset link = mediaLink(imageUrl, false);
        WatermarkConfiguration config = new WatermarkConfiguration();
        config.setEnabled(true);
        config.setElementsJson("""
                [{"id":"txt","type":"text","text":"@DASIG","xPercent":10,"yPercent":70,
                "widthPercent":60,"heightPercent":20,"opacity":0.9,"textColor":"#FFFFFF","fontSizePercent":3.2}]
                """);

        when(configurationRepository.findByInstitutionIsNull()).thenReturn(Optional.of(config));
        when(storageService.generatedWatermarkPath(eq(submission.getId()), eq(link.getMediaAsset().getId()), eq("jpg")))
                .thenReturn("generated/watermarked/sample.jpg");
        when(storageService.uploadPublicObject(eq("generated/watermarked/sample.jpg"), any(byte[].class), eq("image/jpeg")))
                .thenReturn("https://storage.example/watermarked.jpg");

        String result = service.resolvePublishUrl(submission, link);

        assertThat(result).isEqualTo("https://storage.example/watermarked.jpg");
        verify(storageService).uploadPublicObject(eq("generated/watermarked/sample.jpg"), any(byte[].class), eq("image/jpeg"));
    }

    @Test
    void resolvePublishUrl_preservesPhotoDetailWhenEncodingWatermarkedImage() throws Exception {
        Submission submission = submission(UUID.randomUUID());
        SubmissionMediaAsset link = mediaLink(detailRichImageUrl, false);
        link.getMediaAsset().setFileType(MediaFileType.png);

        WatermarkConfiguration config = new WatermarkConfiguration();
        config.setEnabled(true);
        config.setElementsJson("""
                [{"id":"txt","type":"text","text":"@DASIG","xPercent":0,"yPercent":0,
                "widthPercent":20,"heightPercent":10,"opacity":0.9,"textColor":"#FFFFFF","fontSizePercent":3.2}]
                """);

        when(configurationRepository.findByInstitutionIsNull()).thenReturn(Optional.of(config));
        when(storageService.generatedWatermarkPath(any(), any(), eq("jpg"))).thenReturn("generated/watermarked/detail.jpg");
        when(storageService.uploadPublicObject(any(), any(), eq("image/jpeg"))).thenReturn("https://storage.example/detail.jpg");
        ArgumentCaptor<byte[]> imageCaptor = ArgumentCaptor.forClass(byte[].class);

        service.resolvePublishUrl(submission, link);

        verify(storageService).uploadPublicObject(any(), imageCaptor.capture(), eq("image/jpeg"));
        BufferedImage original = ImageIO.read(new java.io.ByteArrayInputStream(detailRichImageBytes));
        BufferedImage encoded = ImageIO.read(new java.io.ByteArrayInputStream(imageCaptor.getValue()));
        ByteArrayOutputStream defaultQualityBytes = new ByteArrayOutputStream();
        ImageIO.write(original, "jpg", defaultQualityBytes);
        BufferedImage defaultQuality = ImageIO.read(new java.io.ByteArrayInputStream(defaultQualityBytes.toByteArray()));

        assertThat(meanAbsoluteError(original, encoded))
                .isLessThan(meanAbsoluteError(original, defaultQuality) * 0.9);
    }

    @Test
    void resolvePublishUrl_drawsRibbonWithSquareCorners() throws Exception {
        Submission submission = submission(UUID.randomUUID());
        SubmissionMediaAsset link = mediaLink(imageUrl, false);
        WatermarkConfiguration config = new WatermarkConfiguration();
        config.setEnabled(true);
        config.setElementsJson("""
                [{"id":"ribbon","type":"shape","shapeType":"rectangle","xPercent":0,"yPercent":80,
                "widthPercent":100,"heightPercent":20,"opacity":1,"fillColor":"#FF0000","strokeColor":"transparent"}]
                """);

        when(configurationRepository.findByInstitutionIsNull()).thenReturn(Optional.of(config));
        when(storageService.generatedWatermarkPath(any(), any(), eq("jpg"))).thenReturn("generated/watermarked/ribbon.jpg");
        when(storageService.uploadPublicObject(any(), any(), eq("image/jpeg"))).thenAnswer(invocation -> {
            byte[] imageBytes = invocation.getArgument(1);
            BufferedImage output = ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
            int corner = output.getRGB(3, 96);
            assertThat((corner >> 16) & 0xFF).isGreaterThan(150);
            assertThat(corner & 0xFF).isLessThan(100);
            return "https://storage.example/ribbon.jpg";
        });

        assertThat(service.resolvePublishUrl(submission, link))
                .isEqualTo("https://storage.example/ribbon.jpg");
    }

    @Test
    void resolvePublishUrl_keepsAnchoredTextInsideItsBox() throws Exception {
        Submission submission = submission(UUID.randomUUID());
        SubmissionMediaAsset link = mediaLink(imageUrl, false);
        WatermarkConfiguration config = new WatermarkConfiguration();
        config.setEnabled(true);
        config.setElementsJson("""
                [{"id":"text","type":"text","anchor":"bottom-right","xPercent":3,"yPercent":3,
                "widthPercent":20,"heightPercent":10,"text":"@DASIGConnect-Long-Organization-Handle-That-Would-Overflow",
                "fontSizePercent":20,"textColor":"#FFFFFF","opacity":1}]
                """);

        when(configurationRepository.findByInstitutionIsNull()).thenReturn(Optional.of(config));
        when(storageService.generatedWatermarkPath(any(), any(), eq("jpg"))).thenReturn("generated/watermarked/anchored-text.jpg");
        when(storageService.uploadPublicObject(any(), any(), eq("image/jpeg"))).thenAnswer(invocation -> {
            BufferedImage output = ImageIO.read(new java.io.ByteArrayInputStream(invocation.getArgument(1)));
            int edgePixel = output.getRGB(99, 90);
            assertThat((edgePixel >> 16) & 0xFF).isLessThan(30);
            assertThat((edgePixel >> 8) & 0xFF).isLessThan(30);
            assertThat(edgePixel & 0xFF).isGreaterThan(200);
            return "https://storage.example/anchored-text.jpg";
        });

        assertThat(service.resolvePublishUrl(submission, link))
                .isEqualTo("https://storage.example/anchored-text.jpg");
    }

    @Test
    void resolvePublishUrl_handlesBase64AndRgbaColorsAndShapes() throws Exception {
        UUID institutionId = UUID.randomUUID();
        Submission submission = submission(institutionId);
        SubmissionMediaAsset link = mediaLink(imageUrl, false);

        byte[] logoBytes = sampleImage();
        String base64Logo = "data:image/png;base64," + Base64.getEncoder().encodeToString(logoBytes);

        WatermarkConfiguration config = new WatermarkConfiguration();
        config.setEnabled(true);
        config.setElementsJson(String.format("""
                [
                  {"id":"shape","type":"shape","shapeType":"rectangle","xPercent":0,"yPercent":80,"widthPercent":100,"heightPercent":20,"fillColor":"rgba(15, 23, 42, 0.75)"},
                  {"id":"logo","type":"image","xPercent":75,"yPercent":82,"widthPercent":20,"heightPercent":15,"imageUrl":"%s"},
                  {"id":"text","type":"text","text":"@DASIG","xPercent":10,"yPercent":85,"widthPercent":60,"heightPercent":10,"textColor":"#FFFFFF","fontSizePercent":3.5}
                ]
                """, base64Logo));

        when(configurationRepository.findByInstitutionIsNull()).thenReturn(Optional.of(config));
        when(storageService.generatedWatermarkPath(any(), any(), eq("jpg"))).thenReturn("generated/watermarked/composite.jpg");
        when(storageService.uploadPublicObject(any(), any(), eq("image/jpeg"))).thenReturn("https://storage.example/composite.jpg");

        String result = service.resolvePublishUrl(submission, link);

        assertThat(result).isEqualTo("https://storage.example/composite.jpg");
        verify(storageService).uploadPublicObject(eq("generated/watermarked/composite.jpg"), any(byte[].class), eq("image/jpeg"));
    }

    @Test
    void resolvePublishUrl_fallsBackToDefaultConfigWhenNoDbRowFound() {
        Submission submission = submission(null);
        SubmissionMediaAsset link = mediaLink(imageUrl, false);

        when(configurationRepository.findByInstitutionIsNull()).thenReturn(Optional.empty());
        when(storageService.generatedWatermarkPath(any(), any(), eq("jpg"))).thenReturn("generated/watermarked/fallback.jpg");
        when(storageService.uploadPublicObject(any(), any(), eq("image/jpeg"))).thenReturn("https://storage.example/fallback.jpg");

        String result = service.resolvePublishUrl(submission, link);

        assertThat(result).isEqualTo("https://storage.example/fallback.jpg");
        verify(storageService).uploadPublicObject(eq("generated/watermarked/fallback.jpg"), any(byte[].class), eq("image/jpeg"));
    }

    @Test
    void resolvePublishUrl_undecodableImage_fallsBackAndRecordsFailure() {
        // A4: watermarking failures are non-blocking (the post still publishes
        // with the original image) but must not go unnoticed — an audit entry
        // is recorded and an admin notification event is fired.
        Submission submission = submission(UUID.randomUUID());
        SubmissionMediaAsset link = mediaLink(corruptImageUrl, false);
        WatermarkConfiguration config = new WatermarkConfiguration();
        config.setEnabled(true);
        config.setElementsJson("""
                [{"id":"txt","type":"text","text":"@DASIG","xPercent":10,"yPercent":70,
                "widthPercent":60,"heightPercent":20,"opacity":0.9,"textColor":"#FFFFFF","fontSizePercent":3.2}]
                """);
        when(configurationRepository.findByInstitutionIsNull()).thenReturn(Optional.of(config));

        String result = service.resolvePublishUrl(submission, link);

        assertThat(result).isEqualTo(corruptImageUrl);
        verify(storageService, never()).uploadPublicObject(any(), any(), any());
        verify(auditLogService).recordSystemAction(eq("PUBLISH_WATERMARK_FAILED"), eq(submission.getId()), any());

        ArgumentCaptor<WatermarkApplicationFailedEvent> eventCaptor =
                ArgumentCaptor.forClass(WatermarkApplicationFailedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().submission()).isEqualTo(submission);
        assertThat(eventCaptor.getValue().mediaAssetId()).isEqualTo(link.getMediaAsset().getId());
    }

    @Test
    void resolvePublishUrl_usesOriginalImageWhenAssetSkipsWatermark() {
        Submission submission = submission(UUID.randomUUID());
        SubmissionMediaAsset link = mediaLink(imageUrl, true);

        String result = service.resolvePublishUrl(submission, link);

        assertThat(result).isEqualTo(imageUrl);
        verify(configurationRepository, never()).findByInstitutionIsNull();
        verify(storageService, never()).uploadPublicObject(any(), any(), any());
    }

    private Submission submission(UUID institutionId) {
        Submission submission = new Submission();
        submission.setId(UUID.randomUUID());
        if (institutionId != null) {
            Institution institution = new Institution();
            institution.setId(institutionId);
            submission.setInstitution(institution);
        }
        return submission;
    }

    private SubmissionMediaAsset mediaLink(String storageUrl, boolean skipWatermark) {
        MediaAsset asset = new MediaAsset();
        asset.setId(UUID.randomUUID());
        asset.setFileType(MediaFileType.jpeg);
        asset.setStorageUrl(storageUrl);

        SubmissionMediaAsset link = new SubmissionMediaAsset();
        link.setMediaAsset(asset);
        link.setSkipWatermark(skipWatermark);
        return link;
    }

    private byte[] sampleImage() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try {
            g.setColor(Color.BLUE);
            g.fillRect(0, 0, 100, 100);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    private byte[] detailRichImage() throws Exception {
        BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int red = Math.max(0, Math.min(255, x * 255 / 511 + random.nextInt(25) - 12));
                int green = Math.max(0, Math.min(255, y * 255 / 511 + random.nextInt(25) - 12));
                int blue = Math.max(0, Math.min(255, (x + y) * 255 / 1022 + random.nextInt(25) - 12));
                image.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    private double meanAbsoluteError(BufferedImage expected, BufferedImage actual) {
        long absoluteError = 0;
        long pixels = 0;
        for (int y = 128; y < 384; y++) {
            for (int x = 128; x < 384; x++) {
                int expectedRgb = expected.getRGB(x, y);
                int actualRgb = actual.getRGB(x, y);
                absoluteError += Math.abs(((expectedRgb >> 16) & 0xFF) - ((actualRgb >> 16) & 0xFF));
                absoluteError += Math.abs(((expectedRgb >> 8) & 0xFF) - ((actualRgb >> 8) & 0xFF));
                absoluteError += Math.abs((expectedRgb & 0xFF) - (actualRgb & 0xFF));
                pixels += 3;
            }
        }
        return (double) absoluteError / pixels;
    }
}
