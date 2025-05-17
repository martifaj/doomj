package com.doomengine.rendering;

// Represents a "drawn segment" of a wall or portal, used for sprite occlusion.
public class DrawSeg {
    public final int x1;
    public final int x2;          // Screen space horizontal boundaries (inclusive)
    public final double scale;        // Average scale for depth comparison (larger scale = closer)
    public final boolean isMasked;    // True if this is a masked (transparent) mid-texture
    public final String maskedTextureName; // Name of the texture if isMasked is true

    // Per-column Y-coordinates of the top and bottom edges of this segment on screen.
    // These define what this segment occludes.
    // Array index corresponds to (screen_column - x1).
    public final short[] sprtopclip;
    public final short[] sprbottomclip;

    public DrawSeg(int x1, int x2, double scale, boolean isMasked, String maskedTextureName, int screenHeight) {
        this.x1 = x1;
        this.x2 = x2;
        this.scale = scale;
        this.isMasked = isMasked;
        this.maskedTextureName = isMasked ? maskedTextureName : null;

        int numColumns = (x2 - x1 + 1);
        if (numColumns > 0) {
            this.sprtopclip = new short[numColumns];
            this.sprbottomclip = new short[numColumns];
            // Default initialization (e.g., full height if masked, or will be filled by SegHandler)
            for (int i = 0; i < numColumns; i++) {
                this.sprtopclip[i] = 0; // Default: top of screen
                this.sprbottomclip[i] = (short)(screenHeight - 1); // Default: bottom of screen
            }
        } else {
            // Should not happen for valid segments, but handle defensively
            this.sprtopclip = new short[0];
            this.sprbottomclip = new short[0];
        }
    }
}