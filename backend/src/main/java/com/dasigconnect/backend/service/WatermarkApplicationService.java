package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.settings.WatermarkElementDto;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
import com.dasigconnect.backend.model.entity.WatermarkConfiguration;
import com.dasigconnect.backend.repository.WatermarkConfigurationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WatermarkApplicationService {

    private static final Logger log = LoggerFactory.getLogger(WatermarkApplicationService.class);

    private final WatermarkConfigurationRepository configurationRepository;
    private final SupabaseStorageService storageService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public WatermarkApplicationService(
            WatermarkConfigurationRepository configurationRepository,
            SupabaseStorageService storageService,
            ObjectMapper objectMapper) {
        this.configurationRepository = configurationRepository;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    public String resolvePublishUrl(Submission submission, SubmissionMediaAsset link) {
        MediaAsset asset = link.getMediaAsset();
        if (!isWatermarkable(asset) || link.isSkipWatermark()) {
            return asset.getStorageUrl();
        }

        WatermarkConfiguration config = resolveConfiguration(submission).orElse(null);
        if (config == null || !config.isEnabled()) {
            return asset.getStorageUrl();
        }

        List<WatermarkElementDto> elements = parseElements(config.getElementsJson());
        if (elements.isEmpty()) {
            return asset.getStorageUrl();
        }

        try {
            byte[] originalBytes = download(asset.getStorageUrl());
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(originalBytes));
            if (original == null) {
                log.warn("Could not read image for watermarking; publishing original asset {}.", asset.getId());
                return asset.getStorageUrl();
            }

            BufferedImage output = new BufferedImage(
                    original.getWidth(),
                    original.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = output.createGraphics();
            try {
                g.drawImage(original, 0, 0, null);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                for (WatermarkElementDto element : elements) {
                    drawElement(g, output, element);
                }
            } finally {
                g.dispose();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(output, "jpg", baos);
            String path = storageService.generatedWatermarkPath(submission.getId(), asset.getId(), "jpg");
            return storageService.uploadPublicObject(path, baos.toByteArray(), "image/jpeg");
        } catch (Exception ex) {
            log.warn("Watermarking failed for asset {}; publishing original image. Cause: {}",
                    asset.getId(), ex.getMessage());
            return asset.getStorageUrl();
        }
    }

    private Optional<WatermarkConfiguration> resolveConfiguration(Submission submission) {
        UUID institutionId = submission.getInstitution() != null ? submission.getInstitution().getId() : null;
        if (institutionId != null) {
            Optional<WatermarkConfiguration> override = configurationRepository.findByInstitutionId(institutionId);
            if (override.isPresent()) {
                return override;
            }
        }
        return configurationRepository.findByInstitutionIsNull();
    }

    private boolean isWatermarkable(MediaAsset asset) {
        MediaFileType type = asset.getFileType();
        return type == MediaFileType.jpeg
                || type == MediaFileType.png
                || type == MediaFileType.gif
                || type == MediaFileType.webp;
    }

    private byte[] download(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Image download returned " + response.statusCode());
        }
        return response.body();
    }

    private void drawElement(Graphics2D g, BufferedImage canvas, WatermarkElementDto element) {
        if (element == null || element.getType() == null) return;
        int x = percent(canvas.getWidth(), element.getXPercent());
        int y = percent(canvas.getHeight(), element.getYPercent());
        int width = Math.max(1, percent(canvas.getWidth(), element.getWidthPercent()));
        int height = Math.max(1, percent(canvas.getHeight(), element.getHeightPercent()));
        float opacity = (float) Math.max(0.05, Math.min(1.0, element.getOpacity()));

        var originalComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        try {
            switch (element.getType().toLowerCase()) {
                case "text" -> drawText(g, element, x, y, width, height);
                case "shape" -> drawShape(g, element, x, y, width, height);
                case "image" -> drawImage(g, element, x, y, width, height);
                default -> { }
            }
        } finally {
            g.setComposite(originalComposite);
        }
    }

    private void drawText(Graphics2D g, WatermarkElementDto element, int x, int y, int width, int height) {
        String text = element.getText();
        if (text == null || text.isBlank()) return;
        int fontSize = Math.max(10, element.getFontSizePercent() != null
                ? percent(height * 100, element.getFontSizePercent()) / 100
                : Math.max(14, height));
        int style = Font.PLAIN;
        if ("700".equals(element.getFontWeight()) || "bold".equalsIgnoreCase(element.getFontWeight())) {
            style |= Font.BOLD;
        }
        if ("italic".equalsIgnoreCase(element.getFontStyle())) {
            style |= Font.ITALIC;
        }
        g.setFont(new Font(
                element.getFontFamily() == null || element.getFontFamily().isBlank() ? "SansSerif" : element.getFontFamily(),
                style,
                fontSize));
        g.setColor(colorOrDefault(element.getTextColor(), Color.WHITE));
        g.drawString(text, x, y + Math.max(fontSize, height / 2));
    }

    private void drawShape(Graphics2D g, WatermarkElementDto element, int x, int y, int width, int height) {
        if ("line".equalsIgnoreCase(element.getShapeType())) {
            g.setColor(colorOrDefault(element.getStrokeColor(), Color.WHITE));
            g.drawLine(x, y, x + width, y + height);
            return;
        }
        g.setColor(colorOrDefault(element.getFillColor(), new Color(0, 0, 0, 128)));
        g.fillRect(x, y, width, height);
    }

    private void drawImage(Graphics2D g, WatermarkElementDto element, int x, int y, int width, int height) {
        String imageUrl = element.getImageUrl();
        if (imageUrl == null || imageUrl.isBlank() || !imageUrl.startsWith("http")) {
            return;
        }
        try {
            BufferedImage watermark = ImageIO.read(new ByteArrayInputStream(download(imageUrl)));
            if (watermark != null) {
                g.drawImage(watermark, x, y, width, height, null);
            }
        } catch (Exception ex) {
            log.warn("Skipping watermark image {}: {}", imageUrl, ex.getMessage());
        }
    }

    private int percent(int value, double percent) {
        return (int) Math.round(value * percent / 100.0);
    }

    private Color colorOrDefault(String raw, Color fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Color.decode(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private List<WatermarkElementDto> parseElements(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<WatermarkElementDto>>() {});
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }
}
