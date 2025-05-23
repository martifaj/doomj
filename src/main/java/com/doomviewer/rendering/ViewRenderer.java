package com.doomviewer.rendering;

import com.doomviewer.misc.Constants;
import com.doomviewer.game.Player;
import com.doomviewer.game.objects.MapObject;
import com.doomviewer.game.DoomEngine;
import com.doomviewer.wad.assets.AssetData;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
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

        this.skyId = this.assetData.skyId; // This is "F_SKY1"
        this.skyTexture = this.assetData.skyTex; // This is the actual texture for "SKY1"
        this.skyInvScale = 160.0 / Constants.HEIGHT; // As in Python
        this.skyTextureAltitude = 100; // As in Python
    }

    public void drawSprite(Graphics2D g2d) { // Takes Graphics2D for final screen blit
        // Draw current animated weapon sprite using Doom state machine
        if (player != null) {
            String weaponSprite = player.getCurrentWeaponSprite();
            BufferedImage spriteImg = sprites.get(weaponSprite);
            if (spriteImg != null) {
                int x = Constants.H_WIDTH - spriteImg.getWidth() / 2;
                int y = Constants.HEIGHT - spriteImg.getHeight();
                g2d.drawImage(spriteImg, x, y, null);
            }
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

    public static void drawWallColumn(int[] framebuffer, double[] depthBuffer, int[][] texture, double textureColumn,
                                      int x, int y1, int y2,
                                      double textureAltitude, double invScale, double lightLevel, double depth) {
        if (y1 > y2 || texture == null) return;

        int texWidth = texture.length;    // Number of columns
        int texHeight = texture[0].length; // Height of a column

        int texU = ((int) Math.floor(textureColumn) % texWidth + texWidth) % texWidth; // Ensure positive U

        // Texture V (vertical) coordinate calculation
        // tex_y = tex_alt + (float(y1) - H_HEIGHT) * inv_scale
        double texV = textureAltitude + ((double) y1 - Constants.H_HEIGHT) * invScale;

        for (int y = y1; y <= y2; y++) {
            if (y < 0 || y >= Constants.HEIGHT || x < 0 || x >= Constants.WIDTH) continue;

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

            framebuffer[x + y * Constants.WIDTH] = (alpha << 24) | (red << 16) | (green << 8) | blue;
            depthBuffer[x + y * Constants.WIDTH] = depth; // Write depth value
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
            drawWallColumn(framebuffer, engine.getDepthBuffer(), this.skyTexture, skyTexColumn, x, y1, y2,
                    this.skyTextureAltitude, this.skyInvScale, 1.0, Double.MAX_VALUE); // Sky is full bright and infinitely far
        } else {
            if (!textures.containsKey(textureId)) return;
            int[][] flatTexture = textures.get(textureId); // Flat textures are 64x64
            drawFlatColumn(framebuffer, flatTexture, x, y1, y2, lightLevel, worldZ, // worldZ is now absolute plane Z
                    this.player.getEyeLevelViewZ(), // Correctly use player's world eye Z
                    this.player.angle, this.player.pos.x, this.player.pos.y, screenColumnAngle);
        }
    }

    public void drawWorldSpritesWithOcclusion(int[] framebuffer, List<MapObject> mapObjects) {
        if (player == null || assetData == null || xToAngleTable == null) return;
        
        // Step 1: Generate VisSprites from MapObjects
        List<VisSprite> visSprites = generateVisSprites(mapObjects);
        
        // Step 2: Sort sprites back-to-front by depth (farther = larger distance first)
        visSprites.sort((a, b) -> Double.compare(b.depth, a.depth)); // farther (larger distance) first
        
        // Step 3: Render each sprite with depth buffer occlusion
        double[] depthBuffer = engine.getDepthBuffer();
        for (VisSprite sprite : visSprites) {
            renderSpriteWithDepthTest(framebuffer, depthBuffer, sprite);
        }
    }
    
    private List<VisSprite> generateVisSprites(List<MapObject> mapObjects) {
        List<VisSprite> visSprites = new ArrayList<>();
        
        for (MapObject obj : mapObjects) {
            if (obj.currentSpriteLumpName == null || !assetData.sprites.containsKey(obj.currentSpriteLumpName)) {
                continue;
            }
            BufferedImage spriteImg = assetData.sprites.get(obj.currentSpriteLumpName);
            if (spriteImg == null) continue;
            
            // Transform to camera space
            double dx = obj.pos.x - player.pos.x;
            double dy = obj.pos.y - player.pos.y;
            
            double playerAngleRad = Math.toRadians(player.angle);
            double cosPlayerAngle = Math.cos(playerAngleRad);
            double sinPlayerAngle = Math.sin(playerAngleRad);
            
            double camSpaceX = dx * -sinPlayerAngle + dy * cosPlayerAngle;
            double camSpaceZ_Depth = dx * cosPlayerAngle + dy * sinPlayerAngle;
            
            if (camSpaceZ_Depth <= 0.5) continue; // Behind camera or too close
            
            double scale = Constants.SCREEN_DIST / camSpaceZ_Depth;
            
            // Calculate sprite depth to match wall depth format (smaller = closer)
            double spriteDepth = Constants.SCREEN_DIST / scale; // Same as camSpaceZ_Depth, but explicit calculation
            
            // Get original sprite dimensions
            AssetData.Patch patch = assetData.getSpritePatch(obj.currentSpriteLumpName);
            double spriteOrigWidth = (patch != null) ? patch.header.width : spriteImg.getWidth() / Constants.SCALE;
            double spriteOrigHeight = (patch != null) ? patch.header.height : spriteImg.getHeight() / Constants.SCALE;
            
            double spriteScreenWidth = spriteOrigWidth * scale;
            double spriteScreenHeight = spriteOrigHeight * scale;
            
            // Screen position
            double screenXCenter = Constants.H_WIDTH - camSpaceX * scale;
            int screenX1 = (int) Math.round(screenXCenter - spriteScreenWidth / 2.0);
            int screenX2 = (int) Math.round(screenXCenter + spriteScreenWidth / 2.0);
            
            // Sprite Z calculations
            double objWorldZBase = obj.z;
            double objWorldZCeilRelPlayer = (objWorldZBase + obj.renderHeight) - player.getEyeLevelViewZ();
            double objWorldZFloorRelPlayer = objWorldZBase - player.getEyeLevelViewZ();
            
            int screenY1 = (int) Math.round(Constants.H_HEIGHT - objWorldZCeilRelPlayer * scale);
            int screenY2 = (int) Math.round(Constants.H_HEIGHT - objWorldZFloorRelPlayer * scale);
            
            // Skip if completely off-screen
            if (screenX2 < 0 || screenX1 >= Constants.WIDTH || screenY2 < 0 || screenY1 >= Constants.HEIGHT) {
                continue;
            }
            
            // Get light level from sector or use fullbright
            int sectorLightLevel = 200; // Default good lighting for visibility
            if (engine.getWadData().sectors != null && !engine.getWadData().sectors.isEmpty()) {
                sectorLightLevel = (int)(engine.getWadData().sectors.get(0).lightLevel * 255.0);
            }
            int lightLevel = obj.currentSpriteFullBright ? 255 : sectorLightLevel;
            
            VisSprite visSprite = new VisSprite(obj, screenX1, screenX2, screenY1, screenY2, 
                                              scale, spriteDepth, spriteImg, 
                                              obj.currentSpriteFullBright, lightLevel, Constants.HEIGHT);
            visSprites.add(visSprite);
        }
        
        return visSprites;
    }
    
    private void renderSpriteWithDepthTest(int[] framebuffer, double[] depthBuffer, VisSprite sprite) {
        BufferedImage spriteImg = sprite.image;
        int spriteScreenWidth = sprite.screenX2 - sprite.screenX1 + 1;
        int spriteScreenHeight = sprite.screenY2 - sprite.screenY1 + 1;
        
        
        for (int sx = 0; sx < spriteScreenWidth; sx++) {
            int screenX = sprite.screenX1 + sx;
            if (screenX < 0 || screenX >= Constants.WIDTH) continue;
            
            // Texture U coordinate
            int texU = (int) ((sx / (double) spriteScreenWidth) * spriteImg.getWidth());
            if (texU < 0 || texU >= spriteImg.getWidth()) continue;
            
            for (int sy = 0; sy < spriteScreenHeight; sy++) {
                int screenY = sprite.screenY1 + sy;
                if (screenY < 0 || screenY >= Constants.HEIGHT) continue;
                
                int bufferIndex = screenX + screenY * Constants.WIDTH;
                
                // Depth test: only draw if sprite is closer than what's already drawn
                // Closer = smaller distance values (opposite of scale)
                if (sprite.depth >= depthBuffer[bufferIndex]) {
                    continue;
                }
                
                // Texture V coordinate
                int texV = (int) ((sy / (double) spriteScreenHeight) * spriteImg.getHeight());
                if (texV < 0 || texV >= spriteImg.getHeight()) continue;
                
                int color = spriteImg.getRGB(texU, texV);
                if ((color >> 24) == 0) continue; // Skip transparent pixels
                
                // Apply lighting
                double light = sprite.fullBright ? 1.0 : (sprite.lightLevelInt / 255.0);
                int alpha = (color >> 24) & 0xFF;
                int red = (int) (((color >> 16) & 0xFF) * light);
                int green = (int) (((color >> 8) & 0xFF) * light);
                int blue = (int) ((color & 0xFF) * light);
                red = Math.min(255, Math.max(0, red));
                green = Math.min(255, Math.max(0, green));
                blue = Math.min(255, Math.max(0, blue));
                
                // Draw pixel and update depth buffer
                framebuffer[bufferIndex] = (alpha << 24) | (red << 16) | (green << 8) | blue;
                depthBuffer[bufferIndex] = sprite.depth;
            }
        }
    }

    // Keep the old method for compatibility but mark it as deprecated
    @Deprecated
    public void drawWorldSprites(int[] framebuffer, List<MapObject> sortedMapObjects) {
        if (player == null || assetData == null || xToAngleTable == null) return;

        for (MapObject obj : sortedMapObjects) {
            if (obj.currentSpriteLumpName == null || !assetData.sprites.containsKey(obj.currentSpriteLumpName)) {
                // System.err.println("Sprite not found: " + obj.currentSpriteLumpName);
                continue;
            }
            BufferedImage spriteImg = assetData.sprites.get(obj.currentSpriteLumpName);
            if (spriteImg == null) continue;

            // Simplified sprite z: object's z is its base. Top of sprite is z + object.renderHeight.
            // Sprite anchor (from patch header) is relative to its own top-left.
            // Doom's object Z usually means feet level.
            double objWorldX = obj.pos.x;
            double objWorldY = obj.pos.y;
            double objWorldZBase = obj.z; // Z of the sprite's base (feet)

            // Transform to camera space
            double dx = objWorldX - player.pos.x;
            double dy = objWorldY - player.pos.y;

            double playerAngleRad = Math.toRadians(player.angle);
            double cosPlayerAngle = Math.cos(playerAngleRad);
            double sinPlayerAngle = Math.sin(playerAngleRad);

            // Rotate opposite to player's angle to bring into view space
            // tx: horizontal offset from view center line, ty: depth
            double tx = dx * cosPlayerAngle + dy * sinPlayerAngle; // This is depth in player's X direction
            double ty = dx * -sinPlayerAngle + dy * cosPlayerAngle; // This is horizontal offset in player's Y direction
            // Standard Doom projection: ty is depth, tx is side.
            // Let's use ty for depth, tx for horizontal.
            double camSpaceX = dx * -sinPlayerAngle + dy * cosPlayerAngle; // Perpendicular to view dir (screen X)
            double camSpaceZ_Depth = dx * cosPlayerAngle + dy * sinPlayerAngle; // Along view dir (depth)


            if (camSpaceZ_Depth <= 0.5) continue; // Clip behind or too close (near plane)

            double scale = Constants.SCREEN_DIST / camSpaceZ_Depth;

            // Original sprite dimensions (before engine scaling in AssetData)
            // We need the AssetData.Patch object to get original dimensions if spriteImg is already scaled.
            AssetData.Patch patch = assetData.getSpritePatch(obj.currentSpriteLumpName);
            double spriteOrigWidth = (patch != null) ? patch.header.width : spriteImg.getWidth() / Constants.SCALE;
            double spriteOrigHeight = (patch != null) ? patch.header.height : spriteImg.getHeight() / Constants.SCALE;


            double spriteScreenWidth = spriteOrigWidth * scale;
            double spriteScreenHeight = spriteOrigHeight * scale;

            // Screen X center of sprite
            double screenXCenter = Constants.H_WIDTH - camSpaceX * scale; // Note: camSpaceX was 'tx' if standard coord, or 'ty' if rotated system.
            // With current rotation: camSpaceX is horizontal screen offset.
            // Negative camSpaceX is left of player, positive is right.
            // So, H_WIDTH - camSpaceX * scale seems correct.

            // Sprite Z relative to player's eye
            // obj.spriteYOffset is the original top_offset from patch. It's distance from origin (usually top-left) to visual top.
            // Doom sprites are often anchored at their base. The info.c height is total height.
            // The patch's top_offset is from the image's top-left to the object's logical origin.
            // For a monster standing on floor at obj.z:
            // Its visual top is at obj.z + obj.renderHeight. Its visual bottom is obj.z.
            // Let's simplify: sprite vertical center is based on its world Z.
            // The object's `z` is its base. Its center might be `obj.z + obj.renderHeight / 2`.
            // Relative to player eye: `(obj.z + obj.renderHeight / 2) - player.getEyeLevelViewZ()`.
            // Or, simpler for now: floor based on obj.z, ceiling based on obj.z + obj.renderHeight
            double objWorldZCeilRelPlayer = (objWorldZBase + obj.renderHeight) - player.getEyeLevelViewZ();
            double objWorldZFloorRelPlayer = objWorldZBase - player.getEyeLevelViewZ();

            double screenYCeil = Constants.H_HEIGHT - objWorldZCeilRelPlayer * scale;
            double screenYFloor = Constants.H_HEIGHT - objWorldZFloorRelPlayer * scale;

            // Sprite drawing loop (column by column)
            int iSpriteScreenWidth = (int) Math.round(spriteScreenWidth);
            if (iSpriteScreenWidth <= 0) continue;

            for (int sx = 0; sx < iSpriteScreenWidth; sx++) { // Sprite column
                int screenX = (int) Math.round(screenXCenter - spriteScreenWidth / 2.0 + sx);

                if (screenX < 0 || screenX >= Constants.WIDTH) continue;
                if (engine.getSegHandler().getUpperClip()[screenX] >= engine.getSegHandler().getLowerClip()[screenX] - 1)
                    continue; // Column already filled by wall

                // Texture U coordinate (horizontal in sprite image)
                // If spriteImg is already scaled engine-side (e.g. by Settings.SCALE), use its width.
                // Otherwise, use original width.
                int texU = (int) ((sx / spriteScreenWidth) * spriteImg.getWidth()); // Assumes spriteImg is the one to sample from
                if (texU < 0 || texU >= spriteImg.getWidth()) continue;

                for (int sy = 0; sy < (int) Math.round(spriteScreenHeight); sy++) { // Sprite row in screen space
                    int screenY = (int) Math.round(screenYCeil + sy);

                    if (screenY <= engine.getSegHandler().getUpperClip()[screenX] ||
                            screenY >= engine.getSegHandler().getLowerClip()[screenX]) {
                        continue; // Clipped by wall or other closer sprite part in this column
                    }
                    if (screenY < 0 || screenY >= Constants.HEIGHT) continue;

                    // Texture V coordinate
                    int texV = (int) ((sy / spriteScreenHeight) * spriteImg.getHeight());
                    if (texV < 0 || texV >= spriteImg.getHeight()) continue;

                    int color = spriteImg.getRGB(texU, texV);
                    if ((color >> 24) == 0) continue; // Skip transparent pixels

                    // TODO: Apply lighting based on sector light or fullbright flag
                    double light = obj.currentSpriteFullBright ? 1.0 : engine.getWadData().sectors.get(0).lightLevel; // Simplified light
                    // Needs current sector of obj

                    int alpha = (color >> 24) & 0xFF;
                    int red = (int) (((color >> 16) & 0xFF) * light);
                    int green = (int) (((color >> 8) & 0xFF) * light);
                    int blue = (int) ((color & 0xFF) * light);
                    red = Math.min(255, Math.max(0, red));
                    green = Math.min(255, Math.max(0, green));
                    blue = Math.min(255, Math.max(0, blue));

                    framebuffer[screenX + screenY * Constants.WIDTH] = (alpha << 24) | (red << 16) | (green << 8) | blue;
                }
            }
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
            if (y < 0 || y >= Constants.HEIGHT || x < 0 || x >= Constants.WIDTH) {
                continue;
            }

            // Vertical position of the pixel in camera space (on projection plane), relative to the horizon.
            // Positive for pixels above the horizon (typically ceiling part of screen).
            // Negative for pixels below the horizon (typically floor part of screen).
            // Screen Y coordinates: 0 at top, Settings.HEIGHT-1 at bottom. Horizon at Settings.H_HEIGHT.
            double yCameraSpace = (double) Constants.H_HEIGHT - y;

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
            double z_on_view_axis = (Constants.SCREEN_DIST * planeZRelativeToEye) / yCameraSpace;

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
            int texU = ((int) Math.floor(world_hit_x) & Constants.TEXTURE_MASK_X);
            int texV = ((int) Math.floor(world_hit_y) & Constants.TEXTURE_MASK_Y);
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

            framebuffer[x + y * Constants.WIDTH] = (alpha << 24) | (red << 16) | (green << 8) | blue;
        }
    }
}

