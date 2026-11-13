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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.dasigconnect.backend.event.WatermarkApplicationFailedEvent;

@Service
public class WatermarkApplicationService {

    private static final Logger log = LoggerFactory.getLogger(WatermarkApplicationService.class);
    private static final float JPEG_COMPRESSION_QUALITY = 0.95f;

    private static final Pattern RGBA_PATTERN = Pattern.compile(
            "rgba?\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})(?:\\s*,\\s*([0-9.]+))?\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern INSTITUTION_LOGO_PATTERN = Pattern.compile(
            "/api/v1/institutions/([a-fA-F0-9\\-]+)/logo");

    private final WatermarkConfigurationRepository configurationRepository;
    private final InstitutionRepository institutionRepository;
    private final MediaStorageService storageService;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public WatermarkApplicationService(
            WatermarkConfigurationRepository configurationRepository,
            InstitutionRepository institutionRepository,
            MediaStorageService storageService,
            ObjectMapper objectMapper,
            AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher) {
        this.configurationRepository = configurationRepository;
        this.institutionRepository = institutionRepository;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
    }

    public String resolvePublishUrl(Submission submission, SubmissionMediaAsset link) {
        MediaAsset asset = link.getMediaAsset();
        if (!isWatermarkable(asset) || link.isSkipWatermark()) {
            return asset.getStorageUrl();
        }

        WatermarkConfiguration config = resolveConfiguration().orElseGet(this::createDefaultFallbackConfig);
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
                String detail = "Could not decode the image for watermarking (unreadable/unsupported format).";
                log.warn("{} Publishing original asset {}.", detail, asset.getId());
                recordWatermarkFailure(submission, asset.getId(), detail);
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
                record ElementWithConfig(WatermarkElementDto element, WatermarkLayout.ResolvedConfig config) {}
                java.util.List<ElementWithConfig> resolvedList = new java.util.ArrayList<>();
                for (int i = 0; i < elements.size(); i++) {
                    WatermarkElementDto el = elements.get(i);
                    if (el == null) continue;
                    WatermarkLayout.ResolvedConfig cfg = WatermarkLayout.resolveConfig(el, i);
                    if (!cfg.hidden()) {
                        resolvedList.add(new ElementWithConfig(el, cfg));
                    }
                }
                resolvedList.sort(java.util.Comparator.comparingInt(item -> item.config().order()));

                for (ElementWithConfig item : resolvedList) {
                    drawElement(g, output, item.element(), item.config(), submission);
                }
            } finally {
                g.dispose();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writeJpeg(output, baos);
            String path = storageService.generatedWatermarkPath(submission.getId(), asset.getId(), "jpg");
            return storageService.uploadPublicObject(path, baos.toByteArray(), "image/jpeg");
        } catch (Exception ex) {
            log.warn("Watermarking failed for asset {}; publishing original image. Cause: {}",
                    asset.getId(), ex.getMessage(), ex);
            recordWatermarkFailure(submission, asset.getId(),
                    ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            return asset.getStorageUrl();
        }
    }

    private void writeJpeg(BufferedImage image, ByteArrayOutputStream output) throws IOException {
        var writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No JPEG image writer is available");
        }

        ImageWriter writer = writers.next();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(JPEG_COMPRESSION_QUALITY);
            writer.write(null, new IIOImage(image, null, null), parameters);
            imageOutput.flush();
        } finally {
            writer.dispose();
        }
    }

    private Optional<WatermarkConfiguration> resolveConfiguration() {
        return configurationRepository.findByInstitutionIsNull();
    }

    /**
     * UC-2.5 A4: watermarking is intentionally non-blocking — the post still
     * publishes with the unwatermarked original — but that must not mean the
     * failure goes unnoticed. Records it to the immutable audit log and
     * notifies Admins, same as any other publish-time failure in this
     * codebase (e.g. FacebookPublisherService's PUBLISH_FAILED).
     */
    private void recordWatermarkFailure(Submission submission, UUID mediaAssetId, String detail) {
        try {
            auditLogService.recordSystemAction("PUBLISH_WATERMARK_FAILED", submission.getId(), Map.of(
                    "mediaAssetId", mediaAssetId.toString(),
                    "error", detail));
        } catch (Exception ignored) {
            // Audit logging must never block or fail the publish path.
        }
        eventPublisher.publishEvent(new WatermarkApplicationFailedEvent(submission, mediaAssetId, detail));
    }

    private WatermarkConfiguration createDefaultFallbackConfig() {
        WatermarkConfiguration config = new WatermarkConfiguration();
        config.setEnabled(true);
        config.setElementsJson("""
                [{"id":"default-text","type":"text","anchor":"bottom-right","xPercent":3.0,"yPercent":3.0,"widthPercent":40.0,"heightPercent":6.0,"opacity":0.9,"text":"@DASIGCentralVisayas","textColor":"#FFFFFF","fontSizePercent":2.8,"fontWeight":"700"}]
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

        private void drawElement(
            Graphics2D g,
            BufferedImage canvas,
            WatermarkElementDto element,
            WatermarkLayout.ResolvedConfig config,
            Submission submission) {
        if (element == null || element.getType() == null || config.hidden()) return;

        WatermarkLayout.Box box = WatermarkLayout.resolveBox(config, canvas.getWidth(), canvas.getHeight());
        float opacity = (float) WatermarkLayout.clamp(config.opacity(), 0.05, 1.0);

        var originalComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        try {
            switch (element.getType().toLowerCase()) {
                case "text" -> drawText(g, canvas, element, config, box);
                case "shape" -> drawShape(g, element, config, box);
                case "image" -> drawImage(g, element, config, box, submission);
                default -> { }
            }
        } finally {
            g.setComposite(originalComposite);
        }
    }

        private void drawText(Graphics2D g, BufferedImage canvas, WatermarkElementDto element, WatermarkLayout.ResolvedConfig config, WatermarkLayout.Box box) {
        String text = element.getText();
        if (text == null || text.isBlank()) return;

        // Font size is a percent of the canvas height (frontend: calc(<n> * 1cqh)).
        double fontPct = config.fontSizePercent() != null ? config.fontSizePercent() : 3.2;
        int fontSize = Math.max(1, percent(Math.min(canvas.getWidth(), canvas.getHeight()), fontPct));

        int style = Font.PLAIN;
        String weight = config.fontWeight();
        if (weight == null || !("400".equals(weight) || "normal".equalsIgnoreCase(weight))) {
            style |= Font.BOLD;
        }
        if ("italic".equalsIgnoreCase(config.fontStyle())) {
            style |= Font.ITALIC;
        }

        String[] lines = text.split("\n", -1);
        Font font = new Font(resolveFontFamily(config.fontFamily()), style, fontSize);
        FontMetrics fm = g.getFontMetrics(font);
        int lineHeight = Math.max(fm.getHeight(), (int) Math.round(fontSize * 1.25));
        int shadowOffset = config.textShadow() ? Math.max(1, fontSize / 18) : 0;
        while (fontSize > 1 && !textFits(lines, fm, box, lineHeight, shadowOffset)) {
            fontSize--;
            font = new Font(resolveFontFamily(config.fontFamily()), style, fontSize);
            fm = g.getFontMetrics(font);
            lineHeight = Math.max(fm.getHeight(), (int) Math.round(fontSize * 1.25));
            shadowOffset = config.textShadow() ? Math.max(1, fontSize / 18) : 0;
        }

        g.setFont(font);
        Color mainColor = colorOrDefault(config.textColor(), Color.WHITE);
        Color shadowColor = new Color(0, 0, 0, 160);
        int blockTop = box.y() + (box.height() - lineHeight * lines.length) / 2;

        java.awt.Shape originalClip = g.getClip();
        g.clipRect(box.x(), box.y(), box.width(), box.height());
        boolean rightAligned = "top-right".equals(config.anchor()) || "bottom-right".equals(config.anchor());
        boolean leftAligned = "top-left".equals(config.anchor()) || "bottom-left".equals(config.anchor());
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineWidth = fm.stringWidth(line);
            int lineX = rightAligned
                    ? Math.max(box.x(), box.x() + box.width() - lineWidth - shadowOffset)
                    : leftAligned
                            ? box.x()
                            : box.x() + (box.width() - lineWidth) / 2;
            int baseline = blockTop + i * lineHeight + (lineHeight - fm.getHeight()) / 2 + fm.getAscent();
            if (config.textShadow()) {
                g.setColor(shadowColor);
                g.drawString(line, lineX + shadowOffset, baseline + shadowOffset);
            }
            g.setColor(mainColor);
            g.drawString(line, lineX, baseline);
        }
        g.setClip(originalClip);
    }

    private boolean textFits(String[] lines, FontMetrics metrics, WatermarkLayout.Box box, int lineHeight, int shadowOffset) {
        if ((long) lineHeight * lines.length > box.height()) return false;
        int maxLineWidth = 0;
        for (String line : lines) {
            maxLineWidth = Math.max(maxLineWidth, metrics.stringWidth(line));
        }
        return (long) maxLineWidth + shadowOffset * 2L <= box.width();
    }

    private String resolveFontFamily(String cssFontFamily) {
        if (cssFontFamily == null || cssFontFamily.isBlank()) return Font.SANS_SERIF;
        String lower = cssFontFamily.toLowerCase();
        if (lower.contains("monospace")) return Font.MONOSPACED;
        if (lower.contains("serif") && !lower.contains("sans-serif")) return Font.SERIF;
        return Font.SANS_SERIF;
    }

        private void drawShape(Graphics2D g, WatermarkElementDto element, WatermarkLayout.ResolvedConfig config, WatermarkLayout.Box box) {
        int x = box.x();
        int y = box.y();
        int width = box.width();
        int height = box.height();

        if ("line".equalsIgnoreCase(element.getShapeType())) {
            g.setColor(colorOrDefault(config.strokeColor(), Color.WHITE));
            g.fillRect(x, y, width, height);
            return;
        }

        if ("gradient".equalsIgnoreCase(element.getShapeType()) || "scrim".equalsIgnoreCase(element.getShapeType())) {
            Color baseColor = colorOrDefault(config.fillColor(), new Color(7, 17, 42));
            Color startColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 0);
            Color endColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), baseColor.getAlpha());
            boolean topAnchored = "top".equals(config.anchor());
            GradientPaint gp = topAnchored
                    ? new GradientPaint(x, y, endColor, x, y + height, startColor)
                    : new GradientPaint(x, y, startColor, x, y + height, endColor);
            Paint oldPaint = g.getPaint();
            g.setPaint(gp);
            g.fillRect(x, y, width, height);
            g.setPaint(oldPaint);
            return;
        }

        g.setColor(colorOrDefault(config.fillColor(), new Color(15, 23, 42, 190)));
        g.fillRect(x, y, width, height);

        // Preview draws a 2px outline unless the stroke is transparent or the default white.
        String stroke = config.strokeColor();
        if (stroke != null && !stroke.isBlank()
                && !"transparent".equalsIgnoreCase(stroke.trim())
                && !"#ffffff".equalsIgnoreCase(stroke.trim())) {
            Color strokeColor = colorOrDefault(stroke, null);
            if (strokeColor != null) {
                var oldStroke = g.getStroke();
                g.setStroke(new BasicStroke(2f));
                g.setColor(strokeColor);
                g.drawRect(x + 1, y + 1, Math.max(1, width - 2), Math.max(1, height - 2));
                g.setStroke(oldStroke);
            }
        }
    }

    private void drawImage(Graphics2D g, WatermarkElementDto element, WatermarkLayout.ResolvedConfig config, WatermarkLayout.Box box, Submission submission) {
        String imageUrl = element.getImageUrl();
        try {
            BufferedImage watermark = loadWatermarkImage(imageUrl, submission);
            if (watermark == null) return;

            int origW = watermark.getWidth();
            int origH = watermark.getHeight();
            if (origW <= 0 || origH <= 0) return;

            // object-fit: contain, aligned to the element's anchored corner (same as the preview).
            double scale = Math.min((double) box.width() / origW, (double) box.height() / origH);
            int drawW = Math.max(1, (int) Math.round(origW * scale));
            int drawH = Math.max(1, (int) Math.round(origH * scale));
            boolean rightAligned = "top-right".equals(config.anchor()) || "bottom-right".equals(config.anchor());
            boolean leftAligned = "top-left".equals(config.anchor()) || "bottom-left".equals(config.anchor());
            int drawX = rightAligned
                    ? box.x() + box.width() - drawW
                    : leftAligned
                            ? box.x()
                            : box.x() + (box.width() - drawW) / 2;
            int drawY = box.y() + (box.height() - drawH) / 2;

            g.drawImage(watermark, drawX, drawY, drawW, drawH, null);
        } catch (Exception ex) {
            log.warn("Skipping watermark image {}: {}", imageUrl, ex.getMessage());
        }
    }

    private BufferedImage loadWatermarkImage(String imageUrl, Submission submission) throws Exception {
        if (imageUrl != null && imageUrl.startsWith("data:image/")) {
            // Base64 data URI uploaded in the studio
            int commaIdx = imageUrl.indexOf(',');
            if (commaIdx == -1) return null;
            byte[] decoded = Base64.getDecoder().decode(imageUrl.substring(commaIdx + 1));
            return ImageIO.read(new ByteArrayInputStream(decoded));
        }
        if (imageUrl != null && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
            return ImageIO.read(new ByteArrayInputStream(download(imageUrl)));
        }
        if ("/dasig-logo.png".equals(imageUrl)) {
            try (var is = getClass().getResourceAsStream("/dasig-logo.png")) {
                return is != null ? ImageIO.read(is) : null;
            }
        }
        if (imageUrl != null) {
            Matcher matcher = INSTITUTION_LOGO_PATTERN.matcher(imageUrl);
            if (matcher.find()) {
                return loadInstitutionLogo(UUID.fromString(matcher.group(1)));
            }
        }
        if (submission != null && submission.getInstitution() != null) {
            return loadInstitutionLogo(submission.getInstitution().getId());
        }
        return null;
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
