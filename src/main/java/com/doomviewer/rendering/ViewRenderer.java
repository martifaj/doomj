package com.doomviewer.rendering;

import com.doomviewer.core.Settings;
import com.doomviewer.game.Player;
import com.doomviewer.main.DoomEngine;
import com.doomviewer.wad.assets.AssetData;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


public class ViewRenderer {
    private DoomEngine engine;
    private AssetData assetData;
    private List<int[]> currentPalette;
    private Map<String, BufferedImage> sprites;
    private Map<String, int[][]> textures; // TextureName -> int[width][height] ARGB
    private Player player;
    // private int[] framebuffer; // Direct reference to engine's framebuffer array (int[WIDTH*HEIGHT])
    private double[] xToAngleTable; // From SegHandler

    private Map<String, Integer> colorCache; // For flat debugging colors: texName+lightLevel -> ARGB int
    private Random randomColorGen = new Random();

    // Sky settings
    private String skyId;
    private int[][] skyTexture; // int[width][height] ARGB
    private final double skyInvScale; // Precomputed scaling factor
    private final double skyTextureAltitude; // Relative Y anchor for sky

    public ViewRenderer(DoomEngine engine) {
        this.engine = engine;
        this.assetData = engine.getWadData().assetData;
        this.currentPalette = this.assetData.currentPalette;
        this.sprites = this.assetData.sprites;
        this.textures = this.assetData.textures;
        this.player = engine.getPlayer();
        // this.framebuffer = engine.getFramebuffer();
        this.xToAngleTable = engine.getSegHandler().xToAngleTable; // Get from SegHandler after it's created

        this.colorCache = new HashMap<>();

        this.skyId = this.assetData.skyId; // This is "F_SKY1"
        this.skyTexture = this.assetData.skyTex; // This is the actual texture for "SKY1"
        this.skyInvScale = 160.0 / Settings.HEIGHT; // As in Python
        this.skyTextureAltitude = 100; // As in Python
    }

    public void drawSprite(Graphics2D g2d) { // Takes Graphics2D for final screen blit
        BufferedImage spriteImg = sprites.get("SHTGA0"); // Example sprite
        if (spriteImg != null) {
            int x = Settings.H_WIDTH - spriteImg.getWidth() / 2;
            int y = Settings.HEIGHT - spriteImg.getHeight();
            g2d.drawImage(spriteImg, x, y, null);
        }
    }

    public void drawPalette(Graphics2D g2d) { // For debugging
        int palSize = 10;
        for (int ix = 0; ix < 16; ix++) {
            for (int iy = 0; iy < 16; iy++) {
                int[] color = currentPalette.get(iy * 16 + ix);
                g2d.setColor(new Color(color[0], color[1], color[2]));
                g2d.fillRect(ix * palSize, iy * palSize, palSize, palSize);
            }
        }
    }


    // Numba JIT methods become static Java methods
    // These will operate on the engine's int[] framebuffer directly

    public static void drawWallColumn(int[] framebuffer, int[][] texture, double textureColumn,
                                      int x, int y1, int y2,
                                      double textureAltitude, double invScale, double lightLevel) {
        if (y1 > y2 || texture == null) return;

        int texWidth = texture.length;    // Number of columns
        int texHeight = texture[0].length; // Height of a column

        int texU = ((int) Math.floor(textureColumn) % texWidth + texWidth) % texWidth; // Ensure positive U

        // Texture V (vertical) coordinate calculation
        // tex_y = tex_alt + (float(y1) - H_HEIGHT) * inv_scale
        double texV = textureAltitude + ((double) y1 - Settings.H_HEIGHT) * invScale;

        for (int y = y1; y <= y2; y++) {
            if (y < 0 || y >= Settings.HEIGHT || x < 0 || x >= Settings.WIDTH) continue;

            int currentTexV = ((int) Math.floor(texV) % texHeight + texHeight) % texHeight; // Ensure positive V

            int packedARGB = texture[texU][currentTexV]; // Assuming texture stores ARGB

            // Apply light level
            int alpha = (packedARGB >> 24) & 0xFF;
            int red = (int) (((packedARGB >> 16) & 0xFF) * lightLevel);
            int green = (int) (((packedARGB >> 8) & 0xFF) * lightLevel);
            int blue = (int) ((packedARGB & 0xFF) * lightLevel);

            // Clamp colors
            red = Math.min(255, Math.max(0, red));
            green = Math.min(255, Math.max(0, green));
            blue = Math.min(255, Math.max(0, blue));

            framebuffer[x + y * Settings.WIDTH] = (alpha << 24) | (red << 16) | (green << 8) | blue;
            texV += invScale;
        }
    }


    public void drawFlat(int[] framebuffer, String textureId, double lightLevel,
                         int x, int y1, int y2, double worldZ, double screenColumnAngle) {
        if (y1 > y2) return;

        if (textureId.equals(this.skyId)) {
            // Sky rendering uses its own texture and parameters
            // tex_column = 2.2 * (self.player.angle + self.engine.seg_handler.x_to_angle[x])
            // xToAngleTable[x] IS self.engine.seg_handler.x_to_angle[x]
            double skyTexColumn = 2.2 * (player.angle + screenColumnAngle); // player.angle should be in degrees
            drawWallColumn(framebuffer, this.skyTexture, skyTexColumn, x, y1, y2,
                    this.skyTextureAltitude, this.skyInvScale, 1.0); // Sky is full bright
        } else {
            if (!textures.containsKey(textureId)) return;
            int[][] flatTexture = textures.get(textureId); // Flat textures are 64x64
            drawFlatColumn(framebuffer, flatTexture, x, y1, y2, lightLevel, worldZ, // worldZ is now absolute plane Z
                    this.player.height, // This is playerEyeZWorld
                    this.player.angle, this.player.pos.x, this.player.pos.y, screenColumnAngle);
        }
    }


    /**
     * Draws a vertical column of a flat textured surface (floor or ceiling).
     *
     * @param framebuffer          The target framebuffer array (ARGB packed integers).
     * @param flatTexture          The texture for the flat surface (e.g., int[TEXTURE_WIDTH][TEXTURE_HEIGHT] of ARGB).
     * @param x                    The current screen column to render.
     * @param y1                   The starting screen row (inclusive) for this column segment.
     * @param y2                   The ending screen row (inclusive) for this column segment.
     * @param lightLevel           Light multiplier (0.0 to 1.0).
     * @param planeZWorld          The Z coordinate of the horizontal plane in world space (e.g., 0.0 for a floor at Z=0).
     * @param playerEyeZWorld      The Z coordinate of the player's eye in world space (e.g., 0.5 if player eye is 0.5 units above Z=0).
     * @param playerAngleDeg       Player's view angle in degrees (0 is often positive X-axis, 90 positive Y-axis).
     * @param playerX              Player's X coordinate in world space.
     * @param playerY              Player's Y coordinate in world space.
     * @param screenColumnAngleDeg Angle of the ray for this screen column 'x' relative to the player's
     *                             forward direction (in degrees). Positive for right, negative for left.
     *                             This should typically be calculated as:
     *                             Math.toDegrees(Math.atan2( (x_screen - Settings.H_WIDTH), Settings.PROJECTION_DISTANCE ))
     */
    public static void drawFlatColumn(int[] framebuffer, int[][] flatTexture,
                                      int x, int y1, int y2, double lightLevel,
                                      double planeZWorld, double playerEyeZWorld,
                                      double playerAngleDeg, double playerX, double playerY,
                                      double screenColumnAngleDeg) {

        // Z-coordinate of the plane relative to the player's eye.
        // Positive if plane is "above" player's eye Z, negative if "below".
        double planeZRelativeToEye = planeZWorld - playerEyeZWorld;

        // Player's orientation in radians
        double playerAngleRad = Math.toRadians(playerAngleDeg);

        // Angle of the current ray (for screen column x) relative to player's forward direction
        double rayHorizontalOffsetAngleRad = Math.toRadians(screenColumnAngleDeg);

        // Absolute world angle of the current ray's projection on the XY plane
        double worldRayAngleRad = playerAngleRad + rayHorizontalOffsetAngleRad;

        // Pre-calculate components that depend only on the ray's horizontal angle
        double cosRayHorizontalOffsetAngle = Math.cos(rayHorizontalOffsetAngleRad);
        // Avoid division by zero if ray is exactly perpendicular to view axis (e.g., FOV approaching 180 deg)
        if (Math.abs(cosRayHorizontalOffsetAngle) < 1e-9) { // 1e-9 is a small epsilon
            return;
        }
        double cosWorldRayAngle = Math.cos(worldRayAngleRad);
        double sinWorldRayAngle = Math.sin(worldRayAngleRad);

        for (int y = y1; y <= y2; y++) {
            // Boundary checks for screen coordinates
            if (y < 0 || y >= Settings.HEIGHT || x < 0 || x >= Settings.WIDTH) {
                continue;
            }

            // Vertical position of the pixel in camera space (on projection plane), relative to the horizon.
            // Positive for pixels above the horizon (typically ceiling part of screen).
            // Negative for pixels below the horizon (typically floor part of screen).
            // Screen Y coordinates: 0 at top, Settings.HEIGHT-1 at bottom. Horizon at Settings.H_HEIGHT.
            double yCameraSpace = (double) Settings.H_HEIGHT - y;

            // Avoid division by zero or extreme values at/near the horizon line.
            // A pixel on the horizon line (yCameraSpace == 0) implies the plane is infinitely far
            // or parallel to the view direction at that point.
            if (Math.abs(yCameraSpace) < 0.5) { // Use 0.5 pixels as a threshold
                continue;
            }

            // Calculate distance to the intersection point on the plane, as projected onto the camera's Z-axis (forward view direction).
            // Formula: z_on_view_axis = (D_proj * plane_Z_relative_to_eye) / y_on_projection_plane_from_horizon
            // - For a floor: planeZRelativeToEye is negative, yCameraSpace is negative => z_on_view_axis is positive.
            // - For a ceiling: planeZRelativeToEye is positive, yCameraSpace is positive => z_on_view_axis is positive.
            double z_on_view_axis = (Settings.SCREEN_DIST * planeZRelativeToEye) / yCameraSpace;

            // If z_on_view_axis is non-positive, the intersection point is behind the camera,
            // or on a plane that shouldn't be visible from this pixel (e.g., trying to render a floor above the horizon).
            if (z_on_view_axis <= 0) {
                continue;
            }

            // Correct for perspective: get the true distance along the actual ray to the intersection point.
            // This accounts for rays not being parallel to the view axis (i.e., for pixels not at screen center x).
            double true_dist_along_ray = z_on_view_axis / cosRayHorizontalOffsetAngle;

            // World coordinates of the intersection point on the plane
            double world_hit_x = playerX + true_dist_along_ray * cosWorldRayAngle;
            double world_hit_y = playerY + true_dist_along_ray * sinWorldRayAngle;

            // Texture mapping: get texture coordinates from world coordinates.
            // Assumes texture is aligned with world axes and repeats.
            // Math.floor ensures correct behavior for negative world coordinates.
            // Masking (& Settings.TEXTURE_MASK_X/Y) assumes texture dimensions are powers of 2.
            int texU = ((int) Math.floor(world_hit_x) & Settings.TEXTURE_MASK_X);
            int texV = ((int) Math.floor(world_hit_y) & Settings.TEXTURE_MASK_Y);
            // If texture sizes can vary or are not powers of 2, use modulo arithmetic:
            // texU = (int)Math.floor(world_hit_x) % Settings.TEXTURE_WIDTH;
            // if (texU < 0) texU += Settings.TEXTURE_WIDTH;
            // (Similarly for texV and Settings.TEXTURE_HEIGHT)

            // Assuming flatTexture is [TextureWidth][TextureHeight] or [U][V]
            // Change to flatTexture[texV][texU] if your texture array is [Height][Width] or [V][U]
            int packedARGB = flatTexture[texU][texV];

            // Apply lighting (simple multiplicative)
            int alpha = (packedARGB >> 24) & 0xFF;
            int red = (int) (((packedARGB >> 16) & 0xFF) * lightLevel);
            int green = (int) (((packedARGB >> 8) & 0xFF) * lightLevel);
            int blue = (int) ((packedARGB & 0xFF) * lightLevel);

            // Clamp color components to the valid [0, 255] range
            red = Math.min(255, Math.max(0, red));
            green = Math.min(255, Math.max(0, green));
            blue = Math.min(255, Math.max(0, blue));

            framebuffer[x + y * Settings.WIDTH] = (alpha << 24) | (red << 16) | (green << 8) | blue;
        }
    }
}