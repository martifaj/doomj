package com.doomviewer.utils;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.Color;

public class ImageUtils {

    public static BufferedImage scaleImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        if (targetWidth <= 0 || targetHeight <= 0) {
            BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = dummy.createGraphics();
            g.setColor(new Color(0,0,0,0)); // Transparent
            g.fillRect(0,0,1,1);
            g.dispose();
            return dummy;
        }
        // Use SCALE_REPLICATE for a pixelated look, similar to nearest-neighbor.
        // Use SCALE_SMOOTH for better quality if upscaling significantly.
        Image resultingImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_REPLICATE);
        BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = outputImage.createGraphics();
        g2d.drawImage(resultingImage, 0, 0, null);
        g2d.dispose();
        return outputImage;
    }

    public static void setPixel(BufferedImage image, int x, int y, int[] rgb) {
        if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight()) return;
        int colorValue = (255 << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
        image.setRGB(x, y, colorValue);
    }

    public static void setPixel(BufferedImage image, int x, int y, int r, int g, int b) {
        if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight()) return;
        int colorValue = (255 << 24) | (r << 16) | (g << 8) | b;
        image.setRGB(x, y, colorValue);
    }

    public static void setPixelWithAlpha(BufferedImage image, int x, int y, int r, int g, int b, int a) {
        if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight()) return;
        int colorValue = (a << 24) | (r << 16) | (g << 8) | b;
        image.setRGB(x, y, colorValue);
    }

    public static int[][] bufferedImageToColumnMajorIntArray(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[][] result = new int[width][height]; // Column-major: result[x][y]
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                result[x][y] = image.getRGB(x, y); // ARGB format
            }
        }
        return result;
    }
}