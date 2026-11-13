package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.settings.WatermarkElementDto;
import com.dasigconnect.backend.model.dto.settings.WatermarkElementDto.Position;
import com.dasigconnect.backend.model.dto.settings.WatermarkElementDto.RatioConfig;
import java.util.List;
import java.util.Map;

/**
 * Layout maths for watermark elements. Mirrors
 * {@code frontend/src/components/watermark/watermarkLayout.ts} so the studio,
 * the post preview and the rendered image all place elements identically.
 *
 * <p>Coordinate model: size ({@code widthPercent}, {@code heightPercent}) is a
 * percent of the canvas HEIGHT and can be overridden per format. Position is a
 * percent of canvas width (x) / height (y) and is stored per format.
 */
final class WatermarkLayout {

    /** Formats the studio can lay out. 1:1 is the reference format. */
    enum Format {
        SQUARE("1:1", 1.0),
        STANDARD("4:3", 4.0 / 3.0),
        LANDSCAPE("16:9", 16.0 / 9.0);

        final String key;
        final double aspect;

        Format(String key, double aspect) {
            this.key = key;
            this.aspect = aspect;
        }
    }

    /** On-canvas rectangle in pixels. */
    record Box(int x, int y, int width, int height) {}

    /** Fully resolved configuration for an element in a specific format. */
    record ResolvedConfig(
            double xPercent,
            double yPercent,
            double widthPercent,
            double heightPercent,
            double opacity,
            int order,
            boolean hidden,
            String textColor,
            String fillColor,
            String strokeColor,
            Double fontSizePercent,
            String fontWeight,
            String fontStyle,
            String fontFamily
    ) {}

    /** Widest element that still fits the widest format (16:9). */
    static final double FULL_WIDTH_PERCENT = 178.0;

    static final List<String> OVERRIDE_KEYS = List.of(Format.STANDARD.key, Format.LANDSCAPE.key);

    private WatermarkLayout() {}

    /** Picks the format whose proportions are closest to the image's. */
    static Format nearestFormat(int imageWidth, int imageHeight) {
        if (imageWidth <= 0 || imageHeight <= 0) return Format.SQUARE;
        double aspect = (double) imageWidth / imageHeight;
        Format best = Format.SQUARE;
        for (Format candidate : Format.values()) {
            if (Math.abs(Math.log(aspect / candidate.aspect)) < Math.abs(Math.log(aspect / best.aspect))) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Resolves the full configuration for an element in a given format.
     * Mirrors {@code resolveRatioConfig} in {@code watermarkLayout.ts}.
     */
    static ResolvedConfig resolveConfig(WatermarkElementDto element, Format format, int defaultOrder) {
        Map<String, RatioConfig> ratios = element.getRatios();
        RatioConfig saved = ratios != null ? ratios.get(format.key) : null;
        if (saved != null) {
            return new ResolvedConfig(
                    saved.xPercent() != null ? saved.xPercent() : (format == Format.SQUARE ? element.getXPercent() : element.getXPercent() / format.aspect),
                    saved.yPercent() != null ? saved.yPercent() : element.getYPercent(),
                    saved.widthPercent() != null ? saved.widthPercent() : element.getWidthPercent(),
                    saved.heightPercent() != null ? saved.heightPercent() : element.getHeightPercent(),
                    saved.opacity() != null ? saved.opacity() : (element.getOpacity() > 0 ? element.getOpacity() : 1.0),
                    saved.order() != null ? saved.order() : defaultOrder,
                    saved.hidden() != null ? saved.hidden() : Boolean.TRUE.equals(element.getHidden()),
                    saved.textColor() != null ? saved.textColor() : element.getTextColor(),
                    saved.fillColor() != null ? saved.fillColor() : element.getFillColor(),
                    saved.strokeColor() != null ? saved.strokeColor() : element.getStrokeColor(),
                    saved.fontSizePercent() != null ? saved.fontSizePercent() : element.getFontSizePercent(),
                    saved.fontWeight() != null ? saved.fontWeight() : element.getFontWeight(),
                    saved.fontStyle() != null ? saved.fontStyle() : element.getFontStyle(),
                    saved.fontFamily() != null ? saved.fontFamily() : element.getFontFamily()
            );
        }

        // Untouched ratio: adapt initial 1:1 coordinates
        double initialX;
        double initialY;
        if (format == Format.SQUARE) {
            initialX = element.getXPercent();
            initialY = element.getYPercent();
        } else if (element.getLayouts() != null && element.getLayouts().containsKey(format.key)) {
            // Support legacy layouts map if present
            initialX = element.getLayouts().get(format.key).xPercent();
            initialY = element.getLayouts().get(format.key).yPercent();
        } else {
            // Default: width extends, element pixel position & size stay the same
            initialX = element.getXPercent() / format.aspect;
            initialY = element.getYPercent();
        }

        return new ResolvedConfig(
                initialX,
                initialY,
                element.getWidthPercent(),
                element.getHeightPercent(),
                element.getOpacity() > 0 ? element.getOpacity() : 1.0,
                defaultOrder,
                Boolean.TRUE.equals(element.getHidden()),
                element.getTextColor(),
                element.getFillColor(),
                element.getStrokeColor(),
                element.getFontSizePercent(),
                element.getFontWeight(),
                element.getFontStyle(),
                element.getFontFamily()
        );
    }

    /**
     * Position in a format. Formats the user never touched keep the element at
     * the same pixel spot as in 1:1 (only the background grows).
     */
    static Position resolvePosition(WatermarkElementDto element, Format format) {
        ResolvedConfig config = resolveConfig(element, format, 0);
        return new Position(config.xPercent(), config.yPercent());
    }

    static Box resolveBox(WatermarkElementDto element, Format format, int canvasWidth, int canvasHeight) {
        ResolvedConfig config = resolveConfig(element, format, 0);
        return resolveBox(config, canvasWidth, canvasHeight);
    }

    static Box resolveBox(ResolvedConfig config, int canvasWidth, int canvasHeight) {
        double canvasAspect = (double) canvasWidth / canvasHeight;
        double widthPct = Math.min(config.widthPercent() / canvasAspect, 100.0);
        double heightPct = Math.min(config.heightPercent(), 100.0);
        double left = clamp(config.xPercent(), 0.0, 100.0 - widthPct);
        double top = clamp(config.yPercent(), 0.0, 100.0 - heightPct);

        return new Box(
                (int) Math.round(canvasWidth * left / 100.0),
                (int) Math.round(canvasHeight * top / 100.0),
                Math.max(1, (int) Math.round(canvasWidth * widthPct / 100.0)),
                Math.max(1, (int) Math.round(canvasHeight * heightPct / 100.0)));
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
