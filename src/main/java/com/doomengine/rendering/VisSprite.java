package com.doomengine.rendering;

import com.doomengine.game.objects.MapObject;
import java.awt.image.BufferedImage;

// Represents a visible sprite with screen-space data for rendering and occlusion.
public class VisSprite {
    public final MapObject mo;           // Reference to the original game object
    public final int screenX1;
    public final int screenX2; // Projected screen X boundaries (inclusive)
    public final int screenY1;
    public final int screenY2; // Projected screen Y boundaries (full sprite extent before world clipping)
    public final double scale;           // For depth sorting and comparison (larger scale = closer)
    public final double depth;           // Actual depth value (e.g., distance to sprite center)
    public final BufferedImage image;    // The sprite image to draw (already scaled by AssetData)
    public final boolean fullBright;
    public final int lightLevelInt;      // Original sector light level (0-255) for the sprite

    // Note: Per-column clipping arrays removed - now using depth buffer for occlusion

    // Linked list for sorting (as per Doom's vissprites_t internal structure)
    // Not strictly needed if using Java's List.sort(), but kept for conceptual mapping.
    public VisSprite prev = null;
    public VisSprite next = null;

    public VisSprite(MapObject mo, int screenX1, int screenX2, int screenY1, int screenY2,
                     double scale, double depth, BufferedImage image, boolean fullBright, int lightLevelInt, int screenHeight) {
        this.mo = mo;
        this.screenX1 = screenX1;
        this.screenX2 = screenX2;
        this.screenY1 = screenY1;
        this.screenY2 = screenY2;
        this.scale = scale;
        this.depth = depth;
        this.image = image;
        this.fullBright = fullBright;
        this.lightLevelInt = lightLevelInt;
        
        // No need for clipping arrays - using depth buffer instead
    }
}