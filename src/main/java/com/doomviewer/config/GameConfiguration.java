package com.doomviewer.config;

import java.awt.Color;

public class GameConfiguration {
    // Screen and rendering settings
    private final int doomWidth;
    private final int doomHeight;
    private final double scale;
    private final int width;
    private final int height;
    private final int halfWidth;
    private final int halfHeight;
    private final double fov;
    private final double halfFov;
    private final double screenDistance;
    
    // Texture settings
    private final int textureWidth;
    private final int textureHeight;
    private final int textureMaskX;
    private final int textureMaskY;
    
    // Player settings
    private final double playerSpeed;
    private final double playerRotSpeed;
    private final int playerHeight;
    
    // Color settings
    private final Color colorKey;
    private final int colorKeyRgb;
    private final int colorKeyArgb;
    
    // Audio settings
    private final boolean soundEnabled;
    private final float masterVolume;
    
    // Performance settings
    private final double targetFps;
    
    public GameConfiguration(boolean soundEnabled) {
        this(soundEnabled, 2.25, 60.0, 1.0f);
    }
    
    public GameConfiguration(boolean soundEnabled, double scale, double targetFps, float masterVolume) {
        // Screen settings
        this.doomWidth = 320;
        this.doomHeight = 200;
        this.scale = scale;
        this.width = (int) (doomWidth * scale);
        this.height = (int) (doomHeight * scale);
        this.halfWidth = width / 2;
        this.halfHeight = height / 2;
        this.fov = 90.0;
        this.halfFov = fov / 2.0;
        this.screenDistance = halfWidth / Math.tan(Math.toRadians(halfFov));
        
        // Texture settings
        this.textureWidth = 64;
        this.textureHeight = 64;
        this.textureMaskX = textureWidth - 1;
        this.textureMaskY = textureHeight - 1;
        
        // Player settings
        this.playerSpeed = 0.3;
        this.playerRotSpeed = 0.12;
        this.playerHeight = 41;
        
        // Color settings
        this.colorKey = new Color(152, 0, 136);
        this.colorKeyRgb = (152 << 16) | (0 << 8) | 136;
        this.colorKeyArgb = (255 << 24) | colorKeyRgb;
        
        // Audio settings
        this.soundEnabled = soundEnabled;
        this.masterVolume = masterVolume;
        
        // Performance settings
        this.targetFps = targetFps;
    }
    
    // Getters
    public int getDoomWidth() { return doomWidth; }
    public int getDoomHeight() { return doomHeight; }
    public double getScale() { return scale; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getHalfWidth() { return halfWidth; }
    public int getHalfHeight() { return halfHeight; }
    public double getFov() { return fov; }
    public double getHalfFov() { return halfFov; }
    public double getScreenDistance() { return screenDistance; }
    
    public int getTextureWidth() { return textureWidth; }
    public int getTextureHeight() { return textureHeight; }
    public int getTextureMaskX() { return textureMaskX; }
    public int getTextureMaskY() { return textureMaskY; }
    
    public double getPlayerSpeed() { return playerSpeed; }
    public double getPlayerRotSpeed() { return playerRotSpeed; }
    public int getPlayerHeight() { return playerHeight; }
    
    public Color getColorKey() { return colorKey; }
    public int getColorKeyRgb() { return colorKeyRgb; }
    public int getColorKeyArgb() { return colorKeyArgb; }
    
    public boolean isSoundEnabled() { return soundEnabled; }
    public float getMasterVolume() { return masterVolume; }
    
    public double getTargetFps() { return targetFps; }
    public double getNsPerFrame() { return 1_000_000_000.0 / targetFps; }
}