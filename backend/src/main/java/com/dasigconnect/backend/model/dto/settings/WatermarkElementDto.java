package com.dasigconnect.backend.model.dto.settings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WatermarkElementDto {

    /** Position of an element on one format's canvas, in percent of its width / height. */
    public record Position(double xPercent, double yPercent) {}

    /**
     * Per-format configuration for an element (1:1, 4:3, or 16:9).
     * Layering (order), positioning, size, opacity, and styles are stored independently.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RatioConfig(
            @JsonProperty("xPercent") Double xPercent,
            @JsonProperty("yPercent") Double yPercent,
            @JsonProperty("widthPercent") Double widthPercent,
            @JsonProperty("heightPercent") Double heightPercent,
            @JsonProperty("opacity") Double opacity,
            @JsonProperty("order") Integer order,
            @JsonProperty("hidden") Boolean hidden,
            @JsonProperty("textColor") String textColor,
            @JsonProperty("fillColor") String fillColor,
            @JsonProperty("strokeColor") String strokeColor,
            @JsonProperty("fontSizePercent") Double fontSizePercent,
            @JsonProperty("fontWeight") String fontWeight,
            @JsonProperty("fontStyle") String fontStyle,
            @JsonProperty("fontFamily") String fontFamily
    ) {}

    private String id;
    private String type; // "image" | "text" | "shape"
    private String anchor;
    private double xPercent; // 1:1 position or horizontal inset, % of canvas width
    private double yPercent; // 1:1 position or vertical inset, % of canvas height
    private double widthPercent; // % of canvas HEIGHT, shared by every format
    private double heightPercent; // % of canvas HEIGHT, shared by every format
    private double opacity = 1.0; // 0.0 - 1.0
    private Boolean hidden;

    /** Position overrides keyed by "4:3" / "16:9". Legacy fallback. */
    private Map<String, Position> layouts;

    /** Ratio-specific configurations keyed by "1:1", "4:3", "16:9". */
    private Map<String, RatioConfig> ratios;

    // Text specific
    private String text;
    private String textColor;
    private Double fontSizePercent;
    private String fontWeight;
    private String fontStyle;
    private String fontFamily;

    // Image specific
    private String imageUrl;

    // Shape specific
    private String shapeType; // "rectangle" | "line" | "gradient"
    private String fillColor;
    private String strokeColor;

    public WatermarkElementDto() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAnchor() {
        return anchor;
    }

    public void setAnchor(String anchor) {
        this.anchor = anchor;
    }

    // Explicit names: Jackson's default mangling would turn "getXPercent" into "xpercent".
    @JsonProperty("xPercent")
    public double getXPercent() {
        return xPercent;
    }

    @JsonProperty("xPercent")
    public void setXPercent(double xPercent) {
        this.xPercent = xPercent;
    }

    @JsonProperty("yPercent")
    public double getYPercent() {
        return yPercent;
    }

    @JsonProperty("yPercent")
    public void setYPercent(double yPercent) {
        this.yPercent = yPercent;
    }

    public double getWidthPercent() {
        return widthPercent;
    }

    public void setWidthPercent(double widthPercent) {
        this.widthPercent = widthPercent;
    }

    public double getHeightPercent() {
        return heightPercent;
    }

    public void setHeightPercent(double heightPercent) {
        this.heightPercent = heightPercent;
    }

    public double getOpacity() {
        return opacity;
    }

    public void setOpacity(double opacity) {
        this.opacity = opacity;
    }

    public Boolean getHidden() {
        return hidden;
    }

    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
    }

    @JsonIgnore
    public boolean isHiddenElement() {
        return Boolean.TRUE.equals(hidden);
    }

    public Map<String, Position> getLayouts() {
        return layouts;
    }

    public void setLayouts(Map<String, Position> layouts) {
        this.layouts = layouts;
    }

    @JsonProperty("ratios")
    public Map<String, RatioConfig> getRatios() {
        return ratios;
    }

    @JsonProperty("ratios")
    public void setRatios(Map<String, RatioConfig> ratios) {
        this.ratios = ratios;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTextColor() {
        return textColor;
    }

    public void setTextColor(String textColor) {
        this.textColor = textColor;
    }

    public Double getFontSizePercent() {
        return fontSizePercent;
    }

    public void setFontSizePercent(Double fontSizePercent) {
        this.fontSizePercent = fontSizePercent;
    }

    public String getFontWeight() {
        return fontWeight;
    }

    public void setFontWeight(String fontWeight) {
        this.fontWeight = fontWeight;
    }

    public String getFontStyle() {
        return fontStyle;
    }

    public void setFontStyle(String fontStyle) {
        this.fontStyle = fontStyle;
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getShapeType() {
        return shapeType;
    }

    public void setShapeType(String shapeType) {
        this.shapeType = shapeType;
    }

    public String getFillColor() {
        return fillColor;
    }

    public void setFillColor(String fillColor) {
        this.fillColor = fillColor;
    }

    public String getStrokeColor() {
        return strokeColor;
    }

    public void setStrokeColor(String strokeColor) {
        this.strokeColor = strokeColor;
    }
}
