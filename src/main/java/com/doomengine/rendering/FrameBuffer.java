package com.doomengine.rendering;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Color;

public class FrameBuffer {
    private int width; // Removed final
    private int height; // Removed final
    private BufferedImage imageBuffer;
    private int[] pixelData;
    private final double[] zBuffer; // Added z-buffer

    public FrameBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.imageBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        this.pixelData = ((DataBufferInt) this.imageBuffer.getRaster().getDataBuffer()).getData();
        this.zBuffer = new double[width * height]; // Initialize z-buffer
        Arrays.fill(this.zBuffer, Double.POSITIVE_INFINITY); // Initialize with infinity
    }

    public FrameBuffer(BufferedImage image) { // New constructor, makes a copy
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.imageBuffer = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = this.imageBuffer.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        this.pixelData = ((DataBufferInt) this.imageBuffer.getRaster().getDataBuffer()).getData();
        this.zBuffer = new double[width * height]; // Initialize z-buffer
        Arrays.fill(this.zBuffer, Double.POSITIVE_INFINITY); // Initialize with infinity
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public BufferedImage getImageBuffer() {
        return imageBuffer;
    }

    public int[] getPixelData() {
        return pixelData;
    }

    public void clear(int color) {
        Arrays.fill(pixelData, color);
        Arrays.fill(zBuffer, Double.POSITIVE_INFINITY); // Clear z-buffer
    }

    public void setPixel(int x, int y, int r, int g, int b) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        pixelData[x + y * width] = (255 << 24) | (r << 16) | (g << 8) | b;
    }

    public void setPixel(int x, int y, int[] rgb) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        pixelData[x + y * width] = (255 << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }

    public void setPixelWithAlpha(int x, int y, int r, int g, int b, int a) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        pixelData[x + y * width] = (a << 24) | (r << 16) | (g << 8) | b;
    }

    public void setPixel(int x, int y, int r, int g, int b, double z) { // Added z parameter
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        int index = x + y * width;
        if (z < zBuffer[index]) { // Check z-buffer
            pixelData[index] = (255 << 24) | (r << 16) | (g << 8) | b;
            zBuffer[index] = z; // Update z-buffer
        }
    }

    public void setPixel(int x, int y, int[] rgb, double z) { // Added z parameter
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        int index = x + y * width;
        if (z < zBuffer[index]) { // Check z-buffer
            pixelData[index] = (255 << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
            zBuffer[index] = z; // Update z-buffer
        }
    }

    public void setPixelWithAlpha(int x, int y, int r, int g, int b, int a, double z) { // Added z parameter
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        int index = x + y * width;
        if (z < zBuffer[index]) { // Check z-buffer
            pixelData[index] = (a << 24) | (r << 16) | (g << 8) | b;
            // Alpha blending might need special handling with z-buffer,
            // for now, just update if z is closer.
            zBuffer[index] = z;
        }
    }

    // Methods from ImageUtils, now non-static
    public void scaleSelf(int targetWidth, int targetHeight) {
        if (targetWidth <= 0 || targetHeight <= 0) {
            // Create a 1x1 transparent image if target dimensions are invalid
            this.imageBuffer = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = this.imageBuffer.createGraphics();
            g.setColor(new Color(0,0,0,0)); // Transparent
            g.fillRect(0,0,1,1);
            g.dispose();
            this.width = 1;
            this.height = 1;
        } else {
            Image resultingImage = this.imageBuffer.getScaledInstance(targetWidth, targetHeight, Image.SCALE_REPLICATE);
            this.imageBuffer = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = this.imageBuffer.createGraphics();
            g2d.drawImage(resultingImage, 0, 0, null);
            g2d.dispose();
            this.width = targetWidth;
            this.height = targetHeight;
        }
        // Re-initialize pixelData from the new imageBuffer
        this.pixelData = ((DataBufferInt) this.imageBuffer.getRaster().getDataBuffer()).getData();
    }

    public void applyPixelToImageBuffer(int x, int y, int[] rgb) {
        if (x < 0 || x >= this.width || y < 0 || y >= this.height) return;
        int colorValue = (255 << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
        this.imageBuffer.setRGB(x, y, colorValue);
    }

    public void applyPixelToImageBuffer(int x, int y, int r, int g, int b) {
        if (x < 0 || x >= this.width || y < 0 || y >= this.height) return;
        int colorValue = (255 << 24) | (r << 16) | (g << 8) | b;
        this.imageBuffer.setRGB(x, y, colorValue);
    }

    public void applyPixelWithAlphaToImageBuffer(int x, int y, int r, int g, int b, int a) {
        if (x < 0 || x >= this.width || y < 0 || y >= this.height) return;
        int colorValue = (a << 24) | (r << 16) | (g << 8) | b;
        this.imageBuffer.setRGB(x, y, colorValue);
    }

    public int[][] getImageBufferAsColumnMajorArray() {
        int w = this.width; // Use current width of the framebuffer
        int h = this.height; // Use current height of the framebuffer
        int[][] result = new int[w][h]; // Column-major: result[x][y]
        for (int x_coord = 0; x_coord < w; x_coord++) {
            for (int y_coord = 0; y_coord < h; y_coord++) {
                result[x_coord][y_coord] = this.imageBuffer.getRGB(x_coord, y_coord); // ARGB format
            }
        }
        return result;
    }
}
