package com.doomengine.rendering;

import com.doomengine.misc.Constants;
import com.doomengine.geometry.*;
import com.doomengine.game.Player;
import com.doomengine.game.objects.MapObject;
import com.doomengine.game.DoomEngine;
import com.doomengine.wad.assets.AssetData;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class ViewRenderer {
    private final DoomEngine engine;
    private final AssetData assetData;
    private final Map<String, BufferedImage> sprites;
    private final Map<String, int[][]> textures; // TextureName -> int[width][height] ARGB
    private final Player player;
    private final double[] xToAngleTable; // From SegHandler

    // Sky settings
    private final String skyId;
    private final int[][] skyTexture; // int[width][height] ARGB
    private final double skyInvScale; // Precomputed scaling factor
    private final double skyTextureAltitude; // Relative Y anchor for sky

    public ViewRenderer(DoomEngine engine) {
        this.engine = engine;
        this.assetData = engine.getWadData().assetData;
        this.sprites = this.assetData.sprites;
        this.textures = this.assetData.textures;
        this.player = engine.getPlayer();
        this.xToAngleTable = engine.getSegHandler().getXToAngleTable(); // Get from GeometricSegHandler after it's created

        this.skyId = this.assetData.skyId; // This is "F_SKY1"
        this.skyTexture = this.assetData.skyTex; // This is the actual texture for "SKY1"
        this.skyInvScale = 160.0 / Constants.HEIGHT;
        this.skyTextureAltitude = 100;
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
            // Enhanced sky rendering using geometry classes
            Angle playerAngle = Angle.degrees(player.angle);
            Angle screenAngle = Angle.degrees(screenColumnAngle);
            Angle totalAngle = playerAngle.add(screenAngle);
            double skyTexColumn = 2.2 * totalAngle.degrees();
            drawWallColumn(framebuffer, engine.getDepthBuffer(), this.skyTexture, skyTexColumn, x, y1, y2,
                    this.skyTextureAltitude, this.skyInvScale, 1.0, Double.MAX_VALUE); // Sky is full bright and infinitely far
        } else {
            if (!textures.containsKey(textureId)) return;
            int[][] flatTexture = textures.get(textureId); // Flat textures are 64x64
            // Enhanced flat rendering using geometry classes
            Point2D playerPos = new Point2D(this.player.pos.x(), this.player.pos.y());
            Angle playerAngle = Angle.degrees(this.player.angle);
            Angle screenAngle = Angle.degrees(screenColumnAngle);
            drawFlatColumn(framebuffer, flatTexture, x, y1, y2, lightLevel, worldZ, // worldZ is now absolute plane Z
                    this.player.getEyeLevelViewZ(), // Correctly use player's world eye Z
                    playerAngle, playerPos, screenAngle);
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
            
            // Enhanced camera space transformation using geometry classes
            Point2D objPos = new Point2D(obj.pos.x(), obj.pos.y());
            Point2D playerPos = new Point2D(player.pos.x(), player.pos.y());
            Angle playerAngle = Angle.degrees(player.angle);
            
            // Use Doom-specific camera transformation
            Vector2D camSpaceVector = DoomGeometryUtils.worldPositionToCameraSpace(objPos, playerPos, playerAngle);
            double camSpaceX = camSpaceVector.x(); // Horizontal screen offset
            double camSpaceZ_Depth = camSpaceVector.y(); // Depth
            
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

    public static void drawFlatColumn(int[] framebuffer, int[][] flatTexture,
                                      int x, int y1, int y2, double lightLevel,
                                      double planeZWorld, double playerEyeZWorld,
                                      Angle playerAngle, Point2D playerPos,
                                      Angle screenColumnAngle) {

        // Z-coordinate of the plane relative to the player's eye.
        // Positive if plane is "above" player's eye Z, negative if "below".
        double planeZRelativeToEye = planeZWorld - playerEyeZWorld;

        // Enhanced angle calculations using geometry classes
        Angle worldRayAngle = playerAngle.add(screenColumnAngle);

        // Pre-calculate components using geometry classes
        double cosRayHorizontalOffsetAngle = screenColumnAngle.cos();
        // Avoid division by zero if ray is exactly perpendicular to view axis (e.g., FOV approaching 180 deg)
        if (Math.abs(cosRayHorizontalOffsetAngle) < 1e-9) { // 1e-9 is a small epsilon
            return;
        }
        double cosWorldRayAngle = worldRayAngle.cos();
        double sinWorldRayAngle = worldRayAngle.sin();

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

            // World coordinates of the intersection point on the plane using geometry classes
            Vector2D rayDirection = Vector2D.fromAngle(worldRayAngle);
            Point2D worldHitPoint = playerPos.add(rayDirection.multiply(true_dist_along_ray));
            int packedARGB = getPackedARGB(flatTexture, worldHitPoint);

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

    private static int getPackedARGB(int[][] flatTexture, Point2D worldHitPoint) {
        double world_hit_x = worldHitPoint.x();
        double world_hit_y = worldHitPoint.y();

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
        return flatTexture[texU][texV];
    }
}

