package com.dasigconnect.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class MediaQualityAnalysisServiceTest {

    @Test
    void computeDifferenceHash_identicalImages_match() {
        BufferedImage image = gradient(false);

        long first = MediaQualityAnalysisService.computeDifferenceHash(image);
        long second = MediaQualityAnalysisService.computeDifferenceHash(image);

        assertEquals(0, MediaQualityAnalysisService.hammingDistance(first, second));
    }

    @Test
    void computeDifferenceHash_differentGradients_areFarApart() {
        long leftToRight = MediaQualityAnalysisService.computeDifferenceHash(gradient(false));
        long rightToLeft = MediaQualityAnalysisService.computeDifferenceHash(gradient(true));

        assertTrue(MediaQualityAnalysisService.hammingDistance(leftToRight, rightToLeft) > 40);
    }

    @Test
    void computeBlurScore_flatImageIsLowerThanEdgedImage() {
        var flat = MediaQualityAnalysisService.computeBlurScore(flatImage());
        var edged = MediaQualityAnalysisService.computeBlurScore(checkerboard());

        assertTrue(edged.compareTo(flat) > 0);
        assertEquals(0, flat.signum());
    }

    private static BufferedImage gradient(boolean reverse) {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            for (int x = 0; x < image.getWidth(); x++) {
                int value = reverse ? 255 - (x * 255 / (image.getWidth() - 1)) : x * 255 / (image.getWidth() - 1);
                graphics.setColor(new Color(value, value, value));
                graphics.drawLine(x, 0, x, image.getHeight());
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage flatImage() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.GRAY);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage checkerboard() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    graphics.setColor(((x + y) % 2 == 0) ? Color.WHITE : Color.BLACK);
                    graphics.fillRect(x, y, 1, 1);
                }
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
