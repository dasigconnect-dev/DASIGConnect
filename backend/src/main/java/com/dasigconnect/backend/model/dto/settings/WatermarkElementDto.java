package com.dasigconnect.backend.model.dto.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WatermarkElementDto {
    private String id;
    private String type; // "image" | "text" | "shape"
    private double xPercent; // 0 - 100
    private double yPercent; // 0 - 100
    private double widthPercent; // 0 - 100
    private double heightPercent; // 0 - 100
    private double opacity = 1.0; // 0.0 - 1.0

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
    private String shapeType; // "rectangle" | "line"
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

    public double getXPercent() {
        return xPercent;
    }

    public void setXPercent(double xPercent) {
        this.xPercent = xPercent;
    }

    public double getYPercent() {
        return yPercent;
    }

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
