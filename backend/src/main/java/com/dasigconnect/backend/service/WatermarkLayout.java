package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.settings.WatermarkElementDto;
import com.dasigconnect.backend.model.dto.settings.WatermarkElementDto.RatioConfig;

/**
 * Resolves watermark elements against the actual image while keeping element
 * dimensions and anchor insets relative to its shorter side.
 */
final class WatermarkLayout {

    record Box(int x, int y, int width, int height) {}

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
            String fontFamily,
            String anchor,
            String type
    ) {}

    private WatermarkLayout() {}

    static ResolvedConfig resolveConfig(WatermarkElementDto element, int defaultOrder) {
        String type = element.getType() == null ? "text" : element.getType().toLowerCase();
        boolean shape = "shape".equals(type);
        double x = element.getXPercent();
        double y = element.getYPercent();
        double width = clamp(element.getWidthPercent(), 1.0, 100.0);
        double height = clamp(element.getHeightPercent(), 0.2, 100.0);
        String anchor = element.getAnchor();
        boolean legacyPosition = anchor == null || "freeform".equals(anchor);

        if (shape) {
            if (legacyPosition) {
                boolean bottom = y + height / 2.0 >= 50.0;
                anchor = bottom ? "bottom" : "top";
            } else if (isCornerAnchor(anchor)) {
                boolean bottom = anchor.startsWith("bottom");
                anchor = bottom ? "bottom" : "top";
            } else if (!isVerticalAnchor(anchor)) {
                anchor = "bottom";
            }
            x = 0.0;
            y = 0.0;
            width = 100.0;
        } else if (legacyPosition) {
            boolean right = x + width / 2.0 >= 50.0;
            boolean bottom = y + height / 2.0 >= 50.0;
            anchor = cornerAnchor(right, bottom);
            x = right ? Math.max(0.0, 100.0 - x - width) : x;
            y = bottom ? Math.max(0.0, 100.0 - y - height) : y;
        } else if (isVerticalAnchor(anchor)) {
            anchor = cornerAnchor(x + width / 2.0 >= 50.0, "bottom".equals(anchor));
        } else if (!isCornerAnchor(anchor)) {
            anchor = "bottom-right";
        }

        RatioConfig legacySquare = element.getRatios() == null ? null : element.getRatios().get("1:1");
        int order = element.getOrder() != null
                ? element.getOrder()
                : legacySquare != null && legacySquare.order() != null ? legacySquare.order() : defaultOrder;
        boolean hidden = Boolean.TRUE.equals(element.getHidden())
                || element.getHidden() == null && legacySquare != null && Boolean.TRUE.equals(legacySquare.hidden());

        return new ResolvedConfig(
                clamp(x, 0.0, 100.0),
                clamp(y, 0.0, 100.0),
                width,
                height,
                element.getOpacity() > 0 ? element.getOpacity() : 1.0,
                order,
                hidden,
                element.getTextColor() != null ? element.getTextColor()
                        : legacySquare != null ? legacySquare.textColor() : null,
                element.getFillColor() != null ? element.getFillColor()
                        : legacySquare != null ? legacySquare.fillColor() : null,
                element.getStrokeColor() != null ? element.getStrokeColor()
                        : legacySquare != null ? legacySquare.strokeColor() : null,
                element.getFontSizePercent() != null ? element.getFontSizePercent()
                        : legacySquare != null ? legacySquare.fontSizePercent() : null,
                element.getFontWeight() != null ? element.getFontWeight()
                        : legacySquare != null ? legacySquare.fontWeight() : null,
                element.getFontStyle() != null ? element.getFontStyle()
                        : legacySquare != null ? legacySquare.fontStyle() : null,
                element.getFontFamily() != null ? element.getFontFamily()
                        : legacySquare != null ? legacySquare.fontFamily() : null,
                anchor,
                type
        );
    }

    static Box resolveBox(WatermarkElementDto element, int canvasWidth, int canvasHeight) {
        return resolveBox(resolveConfig(element, 0), canvasWidth, canvasHeight);
    }

    static Box resolveBox(ResolvedConfig config, int canvasWidth, int canvasHeight) {
        int referenceSide = Math.min(canvasWidth, canvasHeight);
        int elementWidth = "shape".equals(config.type()) && isVerticalAnchor(config.anchor())
                ? canvasWidth
                : Math.max(1, (int) Math.round(referenceSide * config.widthPercent() / 100.0));
        int elementHeight = Math.max(1, (int) Math.round(referenceSide * config.heightPercent() / 100.0));
        elementWidth = Math.min(elementWidth, canvasWidth);
        elementHeight = Math.min(elementHeight, canvasHeight);

        int horizontalInset = (int) Math.round(referenceSide * config.xPercent() / 100.0);
        int verticalInset = (int) Math.round(referenceSide * config.yPercent() / 100.0);
        int x = isRightAnchor(config.anchor())
                ? canvasWidth - horizontalInset - elementWidth
                : isCenterAnchor(config.anchor()) ? (canvasWidth - elementWidth) / 2 : horizontalInset;
        int y = isBottomAnchor(config.anchor())
                ? canvasHeight - verticalInset - elementHeight
                : isTopAnchor(config.anchor()) ? verticalInset : (canvasHeight - elementHeight) / 2;

        return new Box(
                Math.max(0, Math.min(canvasWidth - elementWidth, x)),
                Math.max(0, Math.min(canvasHeight - elementHeight, y)),
                elementWidth,
                elementHeight);
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isCornerAnchor(String anchor) {
        return "top-left".equals(anchor) || "top-right".equals(anchor)
                || "bottom-left".equals(anchor) || "bottom-right".equals(anchor);
    }

    private static boolean isVerticalAnchor(String anchor) {
        return "top".equals(anchor) || "bottom".equals(anchor);
    }

    private static boolean isRightAnchor(String anchor) {
        return "top-right".equals(anchor) || "bottom-right".equals(anchor);
    }

    private static boolean isCenterAnchor(String anchor) {
        return "top".equals(anchor) || "bottom".equals(anchor);
    }

    private static boolean isTopAnchor(String anchor) {
        return "top".equals(anchor) || "top-left".equals(anchor) || "top-right".equals(anchor);
    }

    private static boolean isBottomAnchor(String anchor) {
        return "bottom".equals(anchor) || "bottom-left".equals(anchor) || "bottom-right".equals(anchor);
    }

    private static String cornerAnchor(boolean right, boolean bottom) {
        if (bottom) return right ? "bottom-right" : "bottom-left";
        return right ? "top-right" : "top-left";
    }
}
