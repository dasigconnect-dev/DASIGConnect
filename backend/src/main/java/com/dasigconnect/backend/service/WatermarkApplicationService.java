package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.settings.WatermarkElementDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
import com.dasigconnect.backend.model.entity.WatermarkConfiguration;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.WatermarkConfigurationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WatermarkApplicationService {

    private static final Logger log = LoggerFactory.getLogger(WatermarkApplicationService.class);

    private static final Pattern RGBA_PATTERN = Pattern.compile(
            "rgba?\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})(?:\\s*,\\s*([0-9.]+))?\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern INSTITUTION_LOGO_PATTERN = Pattern.compile(
            "/api/v1/institutions/([a-fA-F0-9\\-]+)/logo");

    private final WatermarkConfigurationRepository configurationRepository;
    private final InstitutionRepository institutionRepository;
    private final R2StorageService storageService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public WatermarkApplicationService(
            WatermarkConfigurationRepository configurationRepository,
            InstitutionRepository institutionRepository,
            R2StorageService storageService,
            ObjectMapper objectMapper) {
        this.configurationRepository = configurationRepository;
        this.institutionRepository = institutionRepository;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    public String resolvePublishUrl(Submission submission, SubmissionMediaAsset link) {
        MediaAsset asset = link.getMediaAsset();
        if (!isWatermarkable(asset) || link.isSkipWatermark()) {
            return asset.getStorageUrl();
        }

        WatermarkConfiguration config = resolveConfiguration(submission).orElseGet(this::createDefaultFallbackConfig);
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
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                for (WatermarkElementDto element : elements) {
                    drawElement(g, output, element, submission);
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
                    asset.getId(), ex.getMessage(), ex);
            return asset.getStorageUrl();
        }
    }

    private Optional<WatermarkConfiguration> resolveConfiguration(Submission submission) {
        return configurationRepository.findByInstitutionIsNull();
    }

    private WatermarkConfiguration createDefaultFallbackConfig() {
        WatermarkConfiguration config = new WatermarkConfiguration();
        config.setEnabled(true);
        config.setElementsJson("""
                [{"id":"default-text","type":"text","xPercent":55.0,"yPercent":92.0,"widthPercent":40.0,"heightPercent":6.0,"opacity":0.9,"text":"@DASIGCentralVisayas","textColor":"#FFFFFF","fontSizePercent":2.8,"fontWeight":"700"}]
                """);
        return config;
    }

    private boolean isWatermarkable(MediaAsset asset) {
        if (asset == null || asset.getFileType() == null) return false;
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

    private void drawElement(Graphics2D g, BufferedImage canvas, WatermarkElementDto element, Submission submission) {
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
                case "text" -> drawText(g, canvas, element, x, y, width, height);
                case "shape" -> drawShape(g, element, x, y, width, height);
                case "image" -> drawImage(g, element, x, y, width, height, submission);
                default -> { }
            }
        } finally {
            g.setComposite(originalComposite);
        }
    }

    private void drawText(Graphics2D g, BufferedImage canvas, WatermarkElementDto element, int x, int y, int width, int height) {
        String text = element.getText();
        if (text == null || text.isBlank()) return;

        // Container height percentage (matching frontend calc(${fontSizePercent} * 1cqh))
        double fontPct = element.getFontSizePercent() != null ? element.getFontSizePercent() : 3.2;
        int fontSize = Math.max(12, percent(canvas.getHeight(), fontPct));

        int style = Font.PLAIN;
        if ("700".equals(element.getFontWeight()) || "bold".equalsIgnoreCase(element.getFontWeight())) {
            style |= Font.BOLD;
        }
        if ("italic".equalsIgnoreCase(element.getFontStyle())) {
            style |= Font.ITALIC;
        }

        String family = element.getFontFamily();
        if (family == null || family.isBlank() || family.contains("inherit") || family.contains("sans-serif")) {
            family = Font.SANS_SERIF;
        }
        Font font = new Font(family, style, fontSize);
        g.setFont(font);

        FontMetrics fm = g.getFontMetrics(font);
        int textY = y + ((height - fm.getHeight()) / 2) + fm.getAscent();
        int textX = x;

        // Draw soft drop shadow for contrast on light backgrounds
        Color mainColor = colorOrDefault(element.getTextColor(), Color.WHITE);
        Color shadowColor = new Color(0, 0, 0, 160);
        g.setColor(shadowColor);
        int shadowOffset = Math.max(1, fontSize / 18);
        g.drawString(text, textX + shadowOffset, textY + shadowOffset);

        // Draw main text
        g.setColor(mainColor);
        g.drawString(text, textX, textY);
    }

    private void drawShape(Graphics2D g, WatermarkElementDto element, int x, int y, int width, int height) {
        if ("line".equalsIgnoreCase(element.getShapeType())) {
            g.setColor(colorOrDefault(element.getStrokeColor(), Color.WHITE));
            g.fillRect(x, y, width, Math.max(2, height));
            return;
        }

        Color fillColor = colorOrDefault(element.getFillColor(), new Color(15, 23, 42, 190));
        g.setColor(fillColor);
        int arc = Math.min(12, Math.min(width, height) / 3);
        g.fillRoundRect(x, y, width, height, arc, arc);

        if (element.getStrokeColor() != null && !element.getStrokeColor().isBlank()) {
            Color strokeColor = colorOrDefault(element.getStrokeColor(), Color.WHITE);
            g.setColor(strokeColor);
            g.drawRoundRect(x, y, width, height, arc, arc);
        }
    }

    private void drawImage(Graphics2D g, WatermarkElementDto element, int x, int y, int width, int height, Submission submission) {
        String imageUrl = element.getImageUrl();
        try {
            BufferedImage watermark = null;

            if (imageUrl != null && imageUrl.startsWith("data:image/")) {
                // Base64 Data URI from upload in studio
                int commaIdx = imageUrl.indexOf(',');
                if (commaIdx != -1) {
                    byte[] decoded = Base64.getDecoder().decode(imageUrl.substring(commaIdx + 1));
                    watermark = ImageIO.read(new ByteArrayInputStream(decoded));
                }
            } else if (imageUrl != null && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
                // Absolute URL
                watermark = ImageIO.read(new ByteArrayInputStream(download(imageUrl)));
            } else if (imageUrl != null && INSTITUTION_LOGO_PATTERN.matcher(imageUrl).find()) {
                // Institution logo endpoint
                Matcher matcher = INSTITUTION_LOGO_PATTERN.matcher(imageUrl);
                if (matcher.find()) {
                    UUID instId = UUID.fromString(matcher.group(1));
                    watermark = loadInstitutionLogo(instId);
                }
            } else if (submission != null && submission.getInstitution() != null) {
                // Fallback to submission institution logo
                watermark = loadInstitutionLogo(submission.getInstitution().getId());
            }

            if (watermark != null) {
                g.drawImage(watermark, x, y, width, height, null);
            }
        } catch (Exception ex) {
            log.warn("Skipping watermark image {}: {}", imageUrl, ex.getMessage());
        }
    }

    private BufferedImage loadInstitutionLogo(UUID institutionId) {
        try {
            Optional<Institution> instOpt = institutionRepository.findById(institutionId);
            if (instOpt.isPresent() && instOpt.get().getLogoData() != null) {
                return ImageIO.read(new ByteArrayInputStream(instOpt.get().getLogoData()));
            }
        } catch (Exception ex) {
            log.warn("Failed to load institution logo for {}: {}", institutionId, ex.getMessage());
        }
        return null;
    }

    private int percent(int value, double percent) {
        return (int) Math.round(value * percent / 100.0);
    }

    private Color colorOrDefault(String raw, Color fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String trimmed = raw.trim();

        // 1. Try rgba(...) / rgb(...)
        Matcher matcher = RGBA_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            try {
                int r = Integer.parseInt(matcher.group(1));
                int g = Integer.parseInt(matcher.group(2));
                int b = Integer.parseInt(matcher.group(3));
                String aStr = matcher.group(4);
                int a = 255;
                if (aStr != null) {
                    float aFloat = Float.parseFloat(aStr);
                    a = Math.round(Math.max(0f, Math.min(1f, aFloat)) * 255);
                }
                return new Color(r, g, b, a);
            } catch (Exception ignored) { }
        }

        // 2. Try hex #RRGGBBAA
        if (trimmed.startsWith("#") && trimmed.length() == 9) {
            try {
                int r = Integer.parseInt(trimmed.substring(1, 3), 16);
                int g = Integer.parseInt(trimmed.substring(3, 5), 16);
                int b = Integer.parseInt(trimmed.substring(5, 7), 16);
                int a = Integer.parseInt(trimmed.substring(7, 9), 16);
                return new Color(r, g, b, a);
            } catch (Exception ignored) { }
        }

        // 3. Try standard hex #RRGGBB / #RGB
        try {
            return Color.decode(trimmed);
        } catch (Exception ex) {
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
