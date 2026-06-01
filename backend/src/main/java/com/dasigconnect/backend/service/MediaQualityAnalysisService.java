package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Deterministic media quality signals for UC-4.4.
 *
 * <p>This intentionally uses a perceptual hash for duplicate detection rather than
 * embeddings. Embeddings answer "visually/semantically similar"; dHash answers
 * "near-identical image" much more cheaply and predictably.
 */
@Service
public class MediaQualityAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(MediaQualityAnalysisService.class);
    private static final int DUPLICATE_HAMMING_THRESHOLD = 6;
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(15);
    // Cap the Laplacian analysis resolution. Blur variance is scale-tolerant, so a
    // full-resolution pass on a 4000x3000 photo would allocate/iterate ~12M pixels per
    // asset — an OOM/CPU risk during a 200-asset dump on a constrained host.
    private static final int MAX_BLUR_ANALYSIS_DIM = 512;

    private final MediaAssetRepository mediaAssetRepository;
    private final HttpClient httpClient;

    @Autowired
    public MediaQualityAnalysisService(MediaAssetRepository mediaAssetRepository) {
        this(mediaAssetRepository, HttpClient.newBuilder()
                .connectTimeout(DOWNLOAD_TIMEOUT)
                .build());
    }

    MediaQualityAnalysisService(MediaAssetRepository mediaAssetRepository, HttpClient httpClient) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.httpClient = httpClient;
    }

    public void analyzeImage(UUID assetId, String storageUrl) {
        try {
            BufferedImage image = downloadImage(storageUrl);
            if (image == null) {
                log.warn("Quality analysis skipped for asset {} because the image could not be decoded.", assetId);
                return;
            }

            long perceptualHash = computeDifferenceHash(image);
            BigDecimal blurScore = computeBlurScore(image);
            UUID duplicateOfId = findLikelyDuplicate(assetId, perceptualHash);

            MediaAsset asset = mediaAssetRepository.findActiveById(assetId).orElse(null);
            if (asset == null) {
                return;
            }
            asset.setPerceptualHash(perceptualHash);
            asset.setBlurScore(blurScore);
            asset.setDuplicateOfId(duplicateOfId);
            mediaAssetRepository.save(asset);
        } catch (Exception e) {
            log.warn("Quality analysis failed for asset {}: {}", assetId, e.getMessage());
        }
    }

    private BufferedImage downloadImage(String storageUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(storageUrl))
                .timeout(DOWNLOAD_TIMEOUT)
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Image download returned " + response.statusCode());
        }
        try (InputStream body = response.body()) {
            return ImageIO.read(body);
        }
    }

    private UUID findLikelyDuplicate(UUID assetId, long perceptualHash) {
        MediaAsset current = mediaAssetRepository.findActiveById(assetId).orElse(null);
        if (current == null || current.getInstitution() == null) {
            return null;
        }

        List<MediaAsset> candidates = mediaAssetRepository.findDuplicateHashCandidates(
                current.getInstitution().getId(),
                assetId);

        return candidates.stream()
                .filter(candidate -> candidate.getPerceptualHash() != null)
                .map(candidate -> new DuplicateCandidate(candidate.getId(),
                        hammingDistance(perceptualHash, candidate.getPerceptualHash())))
                .filter(candidate -> candidate.distance() <= DUPLICATE_HAMMING_THRESHOLD)
                .min(Comparator.comparingInt(DuplicateCandidate::distance))
                .map(DuplicateCandidate::assetId)
                .orElse(null);
    }

    static long computeDifferenceHash(BufferedImage source) {
        BufferedImage scaled = resizeGrayscale(source, 9, 8);
        long hash = 0L;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int left = grayAt(scaled, x, y);
                int right = grayAt(scaled, x + 1, y);
                hash <<= 1;
                if (left > right) {
                    hash |= 1L;
                }
            }
        }
        return hash;
    }

    static BigDecimal computeBlurScore(BufferedImage source) {
        int srcWidth = Math.max(1, source.getWidth());
        int srcHeight = Math.max(1, source.getHeight());
        // Downscale large images before the Laplacian pass (variance is scale-tolerant).
        double scale = Math.min(1.0, (double) MAX_BLUR_ANALYSIS_DIM / Math.max(srcWidth, srcHeight));
        int targetWidth = Math.max(3, (int) Math.round(srcWidth * scale));
        int targetHeight = Math.max(3, (int) Math.round(srcHeight * scale));
        BufferedImage gray = resizeGrayscale(source, targetWidth, targetHeight);
        int width = gray.getWidth();
        int height = gray.getHeight();
        if (width < 3 || height < 3) {
            return BigDecimal.ZERO;
        }

        int count = 0;
        double mean = 0.0;
        double m2 = 0.0;
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int laplacian =
                        grayAt(gray, x, y - 1)
                        + grayAt(gray, x - 1, y)
                        - (4 * grayAt(gray, x, y))
                        + grayAt(gray, x + 1, y)
                        + grayAt(gray, x, y + 1);
                count++;
                double delta = laplacian - mean;
                mean += delta / count;
                m2 += delta * (laplacian - mean);
            }
        }
        double variance = count > 1 ? m2 / (count - 1) : 0.0;
        return BigDecimal.valueOf(variance).setScale(4, RoundingMode.HALF_UP);
    }

    static int hammingDistance(long first, long second) {
        return Long.bitCount(first ^ second);
    }

    private static BufferedImage resizeGrayscale(BufferedImage source, int width, int height) {
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    private static int grayAt(BufferedImage image, int x, int y) {
        return image.getRaster().getSample(x, y, 0);
    }

    private record DuplicateCandidate(UUID assetId, int distance) {}
}
