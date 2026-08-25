package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
import com.dasigconnect.backend.model.entity.WatermarkConfiguration;
import com.dasigconnect.backend.repository.WatermarkConfigurationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WatermarkApplicationServiceTest {

    @Mock
    private WatermarkConfigurationRepository configurationRepository;

    @Mock
    private SupabaseStorageService storageService;

    private WatermarkApplicationService service;
    private HttpServer imageServer;
    private String imageUrl;

    @BeforeEach
    void setUp() throws Exception {
        service = new WatermarkApplicationService(configurationRepository, storageService, new ObjectMapper());

        imageServer = HttpServer.create(new InetSocketAddress(0), 0);
        byte[] imageBytes = sampleImage();
        imageServer.createContext("/image.jpg", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, imageBytes.length);
            exchange.getResponseBody().write(imageBytes);
            exchange.close();
        });
        imageServer.start();
        imageUrl = "http://localhost:" + imageServer.getAddress().getPort() + "/image.jpg";
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
                "widthPercent":60,"heightPercent":20,"opacity":0.9,"textColor":"#FFFFFF"}]
                """);

        when(configurationRepository.findByInstitutionId(institutionId)).thenReturn(Optional.of(config));
        when(storageService.generatedWatermarkPath(eq(submission.getId()), eq(link.getMediaAsset().getId()), eq("jpg")))
                .thenReturn("generated/watermarked/sample.jpg");
        when(storageService.uploadPublicObject(eq("generated/watermarked/sample.jpg"), any(byte[].class), eq("image/jpeg")))
                .thenReturn("https://storage.example/watermarked.jpg");

        String result = service.resolvePublishUrl(submission, link);

        assertThat(result).isEqualTo("https://storage.example/watermarked.jpg");
        verify(storageService).uploadPublicObject(eq("generated/watermarked/sample.jpg"), any(byte[].class), eq("image/jpeg"));
    }

    @Test
    void resolvePublishUrl_usesOriginalImageWhenAssetSkipsWatermark() {
        Submission submission = submission(UUID.randomUUID());
        SubmissionMediaAsset link = mediaLink(imageUrl, true);

        String result = service.resolvePublishUrl(submission, link);

        assertThat(result).isEqualTo(imageUrl);
        verify(configurationRepository, never()).findByInstitutionId(any());
        verify(storageService, never()).uploadPublicObject(any(), any(), any());
    }

    private Submission submission(UUID institutionId) {
        Institution institution = new Institution();
        institution.setId(institutionId);
        Submission submission = new Submission();
        submission.setId(UUID.randomUUID());
        submission.setInstitution(institution);
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
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try {
            g.setColor(Color.BLUE);
            g.fillRect(0, 0, 24, 24);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }
}
