package com.doomviewer.misc;

import java.awt.Color;

public class Constants {
    // Original DOOM resolution
    public static final int DOOM_W = 320;
    public static final int DOOM_H = 200;

    // Screen scale
    public static final double SCALE = 2.25;

    // Window resolution
    public static final int WIDTH = (int) (DOOM_W * SCALE);
    public static final int HEIGHT = (int) (DOOM_H * SCALE);
    public static final int H_WIDTH = WIDTH / 2;
    public static final int H_HEIGHT = HEIGHT / 2;

    public static final int TEXTURE_WIDTH = 64;  // Standard width for Doom flat textures
    public static final int TEXTURE_HEIGHT = 64; // Standard height for Doom flat textures
    public static final int TEXTURE_MASK_X = TEXTURE_WIDTH - 1; // = 63 (for power-of-2 dimensions)
    public static final int TEXTURE_MASK_Y = TEXTURE_HEIGHT - 1; // = 63 (for power-of-2 dimensions)

    // Field of View
    public static final double FOV = 90.0;
    public static final double H_FOV = FOV / 2.0;

    // Player settings
    public static final double PLAYER_SPEED = 0.3;
    public static final double PLAYER_ROT_SPEED = 0.12; // Radians or degrees? Py uses 0.12, seems like factor for dt
    public static final int PLAYER_HEIGHT = 41;

    // Rendering
    public static final double SCREEN_DIST = H_WIDTH / Math.tan(Math.toRadians(H_FOV));

    // Color key for transparency (palette index 247 in DOOM)
    // Python: COLOR_KEY = (152, 0, 136)
    public static final Color COLOR_KEY_AWT = new Color(152, 0, 136);
    // RGB integer representation of COLOR_KEY (alpha will be handled separately or assumed opaque)
    public static final int COLOR_KEY_INT_RGB = (152 << 16) | (0 << 8) | 136;
    public static final int COLOR_KEY_INT_ARGB_OPAQUE = (255 << 24) | COLOR_KEY_INT_RGB;
}