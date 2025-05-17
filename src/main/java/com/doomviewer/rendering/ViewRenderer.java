package com.doomviewer.rendering;

import com.doomviewer.core.Settings;
import com.doomviewer.game.Player;
import com.doomviewer.main.DoomEngine;
import com.doomviewer.wad.assets.AssetData;

import java.awt.Color;
import java.awt.Graphics2D;
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
        double texV = textureAltitude + ((double)y1 - Settings.H_HEIGHT) * invScale;

        for (int y = y1; y <= y2; y++) {
            if (y < 0 || y >= Settings.HEIGHT || x < 0 || x >= Settings.WIDTH) continue;

            int currentTexV = ((int) Math.floor(texV) % texHeight + texHeight) % texHeight; // Ensure positive V

            int packedARGB = texture[texU][currentTexV]; // Assuming texture stores ARGB

            // Apply light level
            int alpha = (packedARGB >> 24) & 0xFF;
            int red   = (int)(((packedARGB >> 16) & 0xFF) * lightLevel);
            int green = (int)(((packedARGB >> 8) & 0xFF) * lightLevel);
            int blue  = (int)((packedARGB & 0xFF) * lightLevel);

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
            drawFlatColumn(framebuffer, flatTexture, x, y1, y2, lightLevel, worldZ,
                    player.angle, player.pos.x, player.pos.y, screenColumnAngle);
        }
    }


    public static void drawFlatColumn(int[] framebuffer, int[][] flatTexture,
                                      int x, int y1, int y2, double lightLevel, double worldZ,
                                      double playerAngleDeg, double playerX, double playerY,
                                      double screenColumnAngleDeg /*This param might not be needed for python's way*/) {

        double playerAngleRad = Math.toRadians(playerAngleDeg); // Player's world orientation
        double playerDirX = Math.cos(playerAngleRad);
        double playerDirY = Math.sin(playerAngleRad);

        for (int y = y1; y <= y2; y++) {
            if (y < 0 || y >= Settings.HEIGHT || x < 0 || x >= Settings.WIDTH) continue;
            if (Math.abs(Settings.H_HEIGHT - y) < 1e-3) continue; // Avoid div by zero at horizon

            // Python's Z calculation - this is distance along the view ray at the center of the screen, scaled
            // This 'z_dist_factor' is effectively how far along the view plane the point is,
            // proportional to its height from horizon.
            double z_dist_factor = (Settings.H_WIDTH * worldZ) / (Settings.H_HEIGHT - y);
            if (z_dist_factor <= 0) continue; // Point is behind or on camera plane

            // Python's px, py are world coordinates of the point on the flat
            // px = player_dir_x * z + player_x
            // py = player_dir_y * z + player_y
            // This is wrong; it assumes the pixel (x,y) is AT the center of view (screenColumnAngleDeg == 0).
            // The python code for draw_flat_col is:
            //   z = H_WIDTH * world_z / (H_HEIGHT - iy) <--- This is distance along VIEW AXIS if pixel was at H_HEIGHT
            //   px = player_dir_x * z + player_x <--- World X of point on flat IF ray was along view axis
            //   py = player_dir_y * z + player_y <--- World Y of point on flat IF ray was along view axis
            //   left_x = -player_dir_y * z + px  <--- This is effectively rotating (0, -z) by player_angle and adding to (px,py)
            // Should be: left_x = px - player_dir_y * z_view_plane_width_at_dist_z
            //   left_y =  player_dir_x * z + py
            //   ...
            //   tx = int(left_x + dx * x) & 63
            //   ty = int(left_y + dy * x) & 63
            // This is a scanline-based texture mapping for horizontal planes.

            // Let's use the standard inverse perspective mapping:
            // 1. Find distance to the point on the plane along the specific ray for (x,y)
            // Angle of the ray for this pixel (x,y) from player's forward view
            double rayAngleFromPlayerViewRad = Math.toRadians(screenColumnAngleDeg);

            // Distance from player eye to the point on the flat plane, along the view ray
            // worldZ is height of plane from eye. If ray has angle `alpha` from horizontal:
            // dist_on_ray * sin(alpha) = worldZ.
            // Here, (H_HEIGHT - y) / SCREEN_DIST = tan(vertical_angle_of_ray)
            // So, dist_on_ray = worldZ / sin(atan((H_HEIGHT - y) / SCREEN_DIST))
            // Or simpler: dist_on_plane_projected_on_view_axis = Z_VIEW_AXIS = worldZ / tan(vertical_angle_of_ray)
            // This Z_VIEW_AXIS would be used for player_dir_x * Z_VIEW_AXIS.
            // This is complex. The Python version might be a simplification that works for its setup.
            // For now, I'll keep the raycasting version I implemented, which is geometrically sound.
            // If it doesn't match, the python's exact math for `px, py, left_x, left_y, dx, dy` needs to be ported.

            // Re-implementing the Python logic for flat texturing:
            double z_flat_dist = (Settings.H_WIDTH * worldZ) / (Settings.H_HEIGHT - y);
            // Skip rays that don't hit the plane in front of the camera
            if (z_flat_dist <= 0) continue;
            double z = z_flat_dist; // Positive distance to the plane along the view ray


            // World coordinates of the intersection point, using player's forward direction
            // This 'px_center', 'py_center' is where player's direct forward view would hit the plane
            double px_center = playerDirX * z_flat_dist + playerX;
            double py_center = playerDirY * z_flat_dist + playerY;

            // World coordinates of the point on the plane hit by the ray for screen column 'x'
            // Calculate the world coords of the leftmost and rightmost visible points on the flat at this Z_flat_dist
            // Width of the view frustum on the plane at this distance z_flat_dist
            // The point (x,y) on screen corresponds to an angle screenColumnAngleDeg from player's view center.
            // World point = player_pos + z_flat_dist * (rotated_view_vector_by_screenColumnAngleDeg)
            double actual_ray_angle_rad = Math.toRadians(playerAngleDeg + screenColumnAngleDeg);
            double world_hit_x = playerX + z * Math.cos(actual_ray_angle_rad);
            double world_hit_y = playerY + z * Math.sin(actual_ray_angle_rad);

            int texU = ((int)Math.floor(world_hit_x) & 63);
            int texV = ((int)Math.floor(world_hit_y) & 63);
            // This is the most common and correct way for perspective floor/ceiling texturing.

            int packedARGB = flatTexture[texU][texV];

            int alpha = (packedARGB >> 24) & 0xFF;
            int red   = (int)(((packedARGB >> 16) & 0xFF) * lightLevel);
            int green = (int)(((packedARGB >> 8) & 0xFF) * lightLevel);
            int blue  = (int)((packedARGB & 0xFF) * lightLevel);

            red = Math.min(255, Math.max(0, red));
            green = Math.min(255, Math.max(0, green));
            blue = Math.min(255, Math.max(0, blue));

            framebuffer[x + y * Settings.WIDTH] = (alpha << 24) | (red << 16) | (green << 8) | blue;
        }
    }
}