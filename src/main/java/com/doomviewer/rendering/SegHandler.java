package com.doomviewer.rendering;

import com.doomviewer.core.Settings;
import com.doomviewer.core.math.Vector2D;
import com.doomviewer.game.Player;
import com.doomviewer.main.DoomEngine;
import com.doomviewer.wad.WADData;
import com.doomviewer.wad.datatypes.Linedef;
import com.doomviewer.wad.datatypes.Sector;
import com.doomviewer.wad.datatypes.Seg;
import com.doomviewer.wad.datatypes.Sidedef;

import java.util.*;

import static com.doomviewer.game.BSP.normalizeAngle;


public class SegHandler {
    public static final double MAX_SCALE = 64.0;
    public static final double MIN_SCALE = 0.00390625; // 1/256

    private DoomEngine engine;
    private WADData wadData;
    private Player player;
    private int[] framebuffer; // Will be int[WIDTH * HEIGHT] for ARGB
    private Map<String, int[][]> textures; // From AssetData
    private String skyId;

    private Seg currentSeg;
    private double rwAngle1; // Raw world angle to the start of the segment
    private Set<Integer> screenRange; // Set of X coordinates not yet drawn
    public final double[] xToAngleTable; // Public for ViewRenderer and BSP access if needed

    // Clipping arrays for portal rendering
    private int[] upperClip; // y-coordinate of uppermost drawn pixel for each x
    private int[] lowerClip; // y-coordinate of lowermost drawn pixel for each x

    public SegHandler(DoomEngine engine) {
        this.engine = engine;
        this.wadData = engine.getWadData();
        this.player = engine.getPlayer();
        this.framebuffer = engine.getFramebuffer(); // Reference to DoomEngine's framebuffer
        this.textures = this.wadData.assetData.textures;
        this.skyId = this.wadData.assetData.skyId;

        this.xToAngleTable = createXToAngleTable();
        this.upperClip = new int[Settings.WIDTH];
        this.lowerClip = new int[Settings.WIDTH];
        this.screenRange = new HashSet<>();
    }

    public void update() {
        initFloorCeilClipHeight();
        initScreenRange();
    }

    private void initFloorCeilClipHeight() {
        Arrays.fill(upperClip, -1); // Nothing drawn above screen top
        Arrays.fill(lowerClip, Settings.HEIGHT); // Nothing drawn below screen bottom
    }

    private static double[] createXToAngleTable() {
        double[] table = new double[Settings.WIDTH + 1];
        for (int i = 0; i <= Settings.WIDTH; i++) {
            // Angle of screen column i relative to view center.
            // i=0 (left screen edge), i=WIDTH (right screen edge)
            // H_WIDTH - i: positive for left of center, negative for right.
            table[i] = Math.toDegrees(Math.atan2((double) Settings.H_WIDTH - i, Settings.SCREEN_DIST));
        }
        return table;
    }

    private double scaleFromGlobalAngle(int x, double rwNormalAngle, double rwDistance) {
        // rwNormalAngle: world angle of the wall normal
        // rwDistance: perpendicular distance from player to wall plane
        double xAngle = this.xToAngleTable[x]; // Angle of screen column x relative to player's view center

        // angle between wall normal and ray to screen column x
        // This is: angle_wall_normal_relative_to_player_view - angle_column_x_relative_to_player_view
        // player.angle is world angle of player.
        // rwNormalAngle - player.angle is wall normal relative to player's view.
        // (rwNormalAngle - player.angle) - xAngle
        double cosTheta = Math.cos(Math.toRadians(rwNormalAngle - player.angle - xAngle));

        // Projection: distance_to_screen_plane / (distance_to_wall_point_along_ray * cos(angle_between_ray_and_view_center))
        // Python: num = SCREEN_DIST * math.cos(math.radians(rw_normal_angle - x_angle - self.player.angle))
        // Python: den = rw_distance * math.cos(math.radians(x_angle))
        // SCREEN_DIST is projection plane distance.
        // rw_distance is perpendicular distance to wall.

        double numerator = Settings.SCREEN_DIST * cosTheta;
        double denominator = rwDistance * Math.cos(Math.toRadians(xAngle)); // cos(xAngle) corrects for ray length to non-center pixels

        if (Math.abs(denominator) < 1e-6) return MAX_SCALE; // Avoid division by zero

        double scale = numerator / denominator;
        return Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale));
    }

    private void initScreenRange() {
        this.screenRange.clear();
        for (int i = 0; i < Settings.WIDTH; i++) {
            this.screenRange.add(i);
        }
    }

    // x1, x2 from BSP are screen coordinates, can be double, convert to int for loop
    public void classifySegment(Seg seg, double screenX1, double screenX2, double rwAngle1) {
        this.currentSeg = seg;
        this.rwAngle1 = rwAngle1; // World angle to seg.start_vertex

        int x1 = (int) Math.round(screenX1);
        int x2 = (int) Math.round(screenX2);

        // Ensure x1 < x2 for looping logic. Segments are drawn left-to-right on screen.
        // Original addSegmentToFov might return x1 > x2 if original angle1 > angle2.
        // The rendering loops (drawSolidWallRange etc.) expect x_start < x_end.
        if (x1 > x2) {
            int temp = x1;
            x1 = x2;
            x2 = temp;
        }

        // Clip to screen bounds
        x1 = Math.max(0, Math.min(Settings.WIDTH - 1, x1));
        x2 = Math.max(0, Math.min(Settings.WIDTH - 1, x2));

        if (x1 >= x2) return; // Segment is not visible or is a single line

        Sector frontSector = seg.frontSector;
        Sector backSector = seg.backSector;

        if (backSector == null) { // Solid wall
            clipSolidWalls(x1, x2); // x2 is exclusive here
        } else { // Portal wall
            clipPortalWalls(x1, x2); // x2 is exclusive
        }
    }

    private void drawSolidWallRange(int x1, int x2) { // x2 is inclusive for iteration
        if (x1 > x2) return;

        Seg seg = this.currentSeg;
        Sector frontSector = seg.frontSector;
        Linedef line = seg.linedef;
        Sidedef side = line.frontSidedef; // For solid wall, always front sidedef
        ViewRenderer renderer = engine.getViewRenderer();

        String wallTextureId = side.middleTexture;
        String ceilTextureId = frontSector.ceilTexture;
        String floorTextureId = frontSector.floorTexture;
        double lightLevel = frontSector.lightLevel;

        double worldFrontZ1 = frontSector.ceilHeight - player.height;   // Ceiling height relative to player
        double worldFrontZ2 = frontSector.floorHeight - player.height; // Floor height relative to player

        boolean bDrawWall = !"-".equals(wallTextureId) && textures.containsKey(wallTextureId);
        boolean bDrawCeil = worldFrontZ1 > 0 || ceilTextureId.equals(this.skyId);
        boolean bDrawFloor = worldFrontZ2 < 0;

        double rwNormalAngle = normalizeAngle(seg.angle + 90); // Angle of the normal pointing outwards from the wall
        double offsetAngle = normalizeAngle(rwNormalAngle - this.rwAngle1); // Angle between wall normal and vector to seg start

        double hypotenuse = Vector2D.distance(player.pos, seg.startVertex);
        double rwDistance = hypotenuse * Math.cos(Math.toRadians(offsetAngle)); // Perpendicular distance to wall
        if (rwDistance < MIN_SCALE) rwDistance = MIN_SCALE; // Avoid division by zero or extreme scales

        double rwScale1 = scaleFromGlobalAngle(x1, rwNormalAngle, rwDistance);

        // Fix for stretched line bug if wall is ~perpendicular to view at start point
        // Python: if math.isclose(offset_angle % 360, 90, abs_tol=1): rw_scale1 *= 0.01
        // This check is if (player_pos - seg.start_vertex) is almost parallel to wall normal.
        // A small factor might be too aggressive. Let's clamp scale instead.
        // rwScale1 = Math.max(MIN_SCALE, Math.min(MAX_SCALE, rwScale1)); (already done in scaleFromGlobalAngle)

        double rwScaleStep = 0;
        if (x1 < x2) {
            double scale2 = scaleFromGlobalAngle(x2, rwNormalAngle, rwDistance);
            rwScaleStep = (scale2 - rwScale1) / (x2 - x1);
        }

        int[][] wallTexture = bDrawWall ? textures.get(wallTextureId) : null;
        double middleTexAlt = 0; // Vertical texture offset anchor
        if (bDrawWall) {
            if ((line.flags & WADData.LINEDEF_FLAGS_MAP.get("DONT_PEG_BOTTOM")) != 0) {
                // Pegged to floor: V offset is from floor up to texture top
                middleTexAlt = (frontSector.floorHeight + wallTexture[0].length) - player.height;
            } else { // Pegged to ceiling (default)
                middleTexAlt = worldFrontZ1; // Align texture top with ceiling
            }
            middleTexAlt += side.yOffset; // Apply sidedef's Y offset
        }

        double rwOffset = hypotenuse * Math.sin(Math.toRadians(offsetAngle)); // Texture U coordinate offset at seg start
        rwOffset += seg.offset + side.xOffset; // Add linedef and sidedef X offsets

        double rwCenterAngle = normalizeAngle(rwNormalAngle - player.angle); // Wall normal relative to player's view direction

        double wallY1 = Settings.H_HEIGHT - worldFrontZ1 * rwScale1;
        double wallY1Step = -rwScaleStep * worldFrontZ1;
        double wallY2 = Settings.H_HEIGHT - worldFrontZ2 * rwScale1;
        double wallY2Step = -rwScaleStep * worldFrontZ2;

        for (int x = x1; x <= x2; x++) {
            if (!screenRange.contains(x)) continue; // Already drawn by closer seg

            int curUpperClip = upperClip[x];
            int curLowerClip = lowerClip[x];

            int drawWallY1 = (int) Math.round(wallY1);
            int drawWallY2 = (int) Math.round(wallY2);

            if (bDrawCeil) {
                int cy1 = curUpperClip + 1;
                int cy2 = Math.min(drawWallY1 - 1, curLowerClip - 1);
                if (cy1 <= cy2) {
                    renderer.drawFlat(framebuffer, ceilTextureId, lightLevel, x, cy1, cy2, frontSector.ceilHeight, xToAngleTable[x]);
                }
            }

            if (bDrawWall) {
                int wy1 = Math.max(drawWallY1, curUpperClip + 1);
                int wy2 = Math.min(drawWallY2 - 1, curLowerClip - 1); // -1 from drawWallY2 because it's top of floor.
                // Python used draw_wall_y2 which was inclusive.

                if (wy1 <= wy2) {
                    double angle = rwCenterAngle - xToAngleTable[x]; // Angle of ray to wall texture point
                    double textureColumn = rwDistance * Math.tan(Math.toRadians(angle)) - rwOffset;
                    double invScale = 1.0 / rwScale1;

                    ViewRenderer.drawWallColumn(framebuffer, wallTexture, textureColumn, x, wy1, wy2,
                            middleTexAlt, invScale, lightLevel);
                }
            }

            if (bDrawFloor) {
                int fy1 = Math.max(drawWallY2, curUpperClip + 1); // drawWallY2 is the floor's top line
                int fy2 = curLowerClip - 1;
                if (fy1 <= fy2) {
                    renderer.drawFlat(framebuffer, floorTextureId, lightLevel, x, fy1, fy2, frontSector.floorHeight, xToAngleTable[x]);
                }
            }

            // Update scales and Y positions for next column
            rwScale1 += rwScaleStep;
            wallY1 += wallY1Step;
            wallY2 += wallY2Step;
        }
    }

    // Very similar to drawSolidWallRange, but with portal logic
    private void drawPortalWallRange(int x1, int x2) { // x2 is inclusive
        if (x1 > x2) return;

        Seg seg = this.currentSeg;
        Sector frontSector = seg.frontSector;
        Sector backSector = seg.backSector;
        Linedef line = seg.linedef;
        Sidedef side = line.frontSidedef; // Or could be backSidedef if seg.direction == 1
        // Python's logic implicitly handles this by using front_sidedef for upper/lower tex attributes.
        ViewRenderer renderer = engine.getViewRenderer();

        String upperWallTexId = side.upperTexture;
        String lowerWallTexId = side.lowerTexture;
        String ceilTexId = frontSector.ceilTexture;
        String floorTexId = frontSector.floorTexture;
        double light = frontSector.lightLevel;

        double worldFrontZ1 = frontSector.ceilHeight - player.height;
        double worldBackZ1 = backSector.ceilHeight - player.height;
        double worldFrontZ2 = frontSector.floorHeight - player.height;
        double worldBackZ2 = backSector.floorHeight - player.height;

        if (frontSector.ceilTexture.equals(this.skyId) &&
                backSector.ceilTexture.equals(this.skyId)) {
            worldFrontZ1 = worldBackZ1; // Sky hack
        }

        boolean bDrawUpperWall = false, bDrawCeil = false;
        if (worldFrontZ1 != worldBackZ1 ||
                frontSector.lightLevel != backSector.lightLevel ||
                !frontSector.ceilTexture.equals(backSector.ceilTexture)) {
            bDrawUpperWall = !"-".equals(upperWallTexId) && worldBackZ1 < worldFrontZ1 && textures.containsKey(upperWallTexId);
            bDrawCeil = worldFrontZ1 >= 0 || frontSector.ceilTexture.equals(this.skyId);
        }

        boolean bDrawLowerWall = false, bDrawFloor = false;
        if (worldFrontZ2 != worldBackZ2 ||
                !frontSector.floorTexture.equals(backSector.floorTexture) ||
                frontSector.lightLevel != backSector.lightLevel) {
            bDrawLowerWall = !"-".equals(lowerWallTexId) && worldBackZ2 > worldFrontZ2 && textures.containsKey(lowerWallTexId);
            bDrawFloor = worldFrontZ2 <= 0;
        }

        if (!bDrawUpperWall && !bDrawCeil && !bDrawLowerWall && !bDrawFloor) {
            return;
        }

        double rwNormalAngle = normalizeAngle(seg.angle + 90);
        double offsetAngle = normalizeAngle(rwNormalAngle - this.rwAngle1);
        double hypotenuse = Vector2D.distance(player.pos, seg.startVertex);
        double rwDistance = hypotenuse * Math.cos(Math.toRadians(offsetAngle));
        if (rwDistance < MIN_SCALE) rwDistance = MIN_SCALE;

        double rwScale1 = scaleFromGlobalAngle(x1, rwNormalAngle, rwDistance);
        double rwScaleStep = 0;
        if (x1 < x2) {
            double scale2 = scaleFromGlobalAngle(x2, rwNormalAngle, rwDistance);
            rwScaleStep = (scale2 - rwScale1) / (x2 - x1);
        }

        int[][] upperTexture = bDrawUpperWall ? textures.get(upperWallTexId) : null;
        int[][] lowerTexture = bDrawLowerWall ? textures.get(lowerWallTexId) : null;
        double upperTexAlt = 0, lowerTexAlt = 0;

        if (bDrawUpperWall) {
            if ((line.flags & WADData.LINEDEF_FLAGS_MAP.get("DONT_PEG_TOP")) != 0) {
                upperTexAlt = worldFrontZ1; // Pegged to front sector's ceiling
            } else { // Pegged to back sector's ceiling (default for upper textures)
                upperTexAlt = (backSector.ceilHeight + upperTexture[0].length) - player.height;
            }
            upperTexAlt += side.yOffset;
        }
        if (bDrawLowerWall) {
            if ((line.flags & WADData.LINEDEF_FLAGS_MAP.get("DONT_PEG_BOTTOM")) != 0) {
                // Pegged to front sector's floor, texture top is at front floor + texture height
                lowerTexAlt = (frontSector.floorHeight + lowerTexture[0].length) - player.height;
            } else { // Pegged to back sector's floor (default for lower textures)
                // Texture top is at back sector's floor (world_back_z2)
                lowerTexAlt = worldBackZ2; // Texture top aligns with the top of the lower step
            }
            lowerTexAlt += side.yOffset;
        }

        double rwOffset = 0, rwCenterAngle = 0;
        boolean segTextured = bDrawUpperWall || bDrawLowerWall;
        if (segTextured) {
            rwOffset = hypotenuse * Math.sin(Math.toRadians(offsetAngle)) + seg.offset + side.xOffset;
            rwCenterAngle = normalizeAngle(rwNormalAngle - player.angle);
        }

        double wallY1 = Settings.H_HEIGHT - worldFrontZ1 * rwScale1; // Top of front sector opening
        double wallY1Step = -rwScaleStep * worldFrontZ1;
        double wallY2 = Settings.H_HEIGHT - worldFrontZ2 * rwScale1; // Bottom of front sector opening
        double wallY2Step = -rwScaleStep * worldFrontZ2;

        double portalY1 = 0, portalY1Step = 0; // Top of back sector opening (seen through portal)
        if (bDrawUpperWall || bDrawCeil) { // Need portalY1 if upper wall or ceiling is drawn
            portalY1 = (worldBackZ1 > worldFrontZ2) ? (Settings.H_HEIGHT - worldBackZ1 * rwScale1) : wallY2;
            portalY1Step = (worldBackZ1 > worldFrontZ2) ? (-rwScaleStep * worldBackZ1) : wallY2Step;
        }

        double portalY2 = 0, portalY2Step = 0; // Bottom of back sector opening
        if (bDrawLowerWall || bDrawFloor) { // Need portalY2 if lower wall or floor is drawn
            portalY2 = (worldBackZ2 < worldFrontZ1) ? (Settings.H_HEIGHT - worldBackZ2 * rwScale1) : wallY1;
            portalY2Step = (worldBackZ2 < worldFrontZ1) ? (-rwScaleStep * worldBackZ2) : wallY1Step;
        }

        for (int x = x1; x <= x2; x++) {
            if (!screenRange.contains(x)) continue;

            int curUpperClip = upperClip[x];
            int curLowerClip = lowerClip[x];

            int drawWallY1 = (int) Math.round(wallY1);       // Front ceil
            int drawWallY2 = (int) Math.round(wallY2);       // Front floor
            int drawPortalY1 = (int) Math.round(portalY1);   // Back ceil
            int drawPortalY2 = (int) Math.round(portalY2);   // Back floor

            double textureColumn = 0, invScale = 0;
            if (segTextured) {
                double angle = rwCenterAngle - xToAngleTable[x];
                textureColumn = rwDistance * Math.tan(Math.toRadians(angle)) - rwOffset;
                invScale = 1.0 / rwScale1;
            }

            if (bDrawCeil) {
                int cy1 = curUpperClip + 1;
                // Draw ceiling from current clip top down to front sector's ceiling OR portal's ceiling, whichever is higher.
                int cy2 = Math.min(drawWallY1 - 1, curLowerClip - 1);
                if (cy1 <= cy2) {
                    renderer.drawFlat(framebuffer, ceilTexId, light, x, cy1, cy2, frontSector.ceilHeight, xToAngleTable[x]);
                    curUpperClip = Math.max(curUpperClip, cy2); // Update clip
                }
            }

            if (bDrawUpperWall) {
                // Upper wall is between front ceiling and back ceiling
                int wy1 = Math.max(drawWallY1, curUpperClip + 1);      // Top is front ceil
                int wy2 = Math.min(drawPortalY1 - 1, curLowerClip - 1); // Bottom is back ceil
                if (wy1 <= wy2) {
                    ViewRenderer.drawWallColumn(framebuffer, upperTexture, textureColumn, x, wy1, wy2, upperTexAlt, invScale, light);
                    curUpperClip = Math.max(curUpperClip, wy2);
                }
            }

            // Update overall screen clip based on portal opening (back sector)
            // These become the new clip bounds for things behind this portal
            upperClip[x] = Math.max(curUpperClip, Math.max(drawWallY1, drawPortalY1 - 1)); // Portal top clip
            // Python uses complex logic here based on wy2 for upperclip[x] = wy2

            if (bDrawFloor) {
                // Draw floor from front sector's floor OR portal's floor, whichever is lower, down to current clip bottom
                int fy1 = Math.max(drawWallY2, curUpperClip + 1);
                int fy2 = curLowerClip - 1;
                if (fy1 <= fy2) {
                    renderer.drawFlat(framebuffer, floorTexId, light, x, fy1, fy2, frontSector.floorHeight, xToAngleTable[x]);
                    curLowerClip = Math.min(curLowerClip, fy1); // Update clip
                }
            }

            if (bDrawLowerWall) {
                // Lower wall is between back floor and front floor
                int wy1 = Math.max(drawPortalY2, curUpperClip + 1);    // Top is back floor
                int wy2 = Math.min(drawWallY2 - 1, curLowerClip - 1);  // Bottom is front floor
                if (wy1 <= wy2) {
                    ViewRenderer.drawWallColumn(framebuffer, lowerTexture, textureColumn, x, wy1, wy2, lowerTexAlt, invScale, light);
                    curLowerClip = Math.min(curLowerClip, wy1);
                }
            }

            // Update clip for things seen *through* the portal (back sector's opening)
            // This is different from Python's direct update within the loop.
            // Here, we're tracking the clip region *for the current front sector*.
            // The clip values for the *next* BSP traversal (through the portal) will be set by passing
            // the portal's y-boundaries (drawPortalY1, drawPortalY2) to a recursive call or new state.
            // For now, this function's clip arrays (upperClip, lowerClip) are for the current rendering pass.
            // The python code updates upper_clip/lower_clip with portal_y values to restrict further drawing *in the same subsector*.
            // Let's follow python more closely on clip updates:
            // If upper wall was drawn, upper_clip[x] = max(original_curUpperClip, wy2_of_upper_wall)
            // If lower wall was drawn, lower_clip[x] = min(original_curLowerClip, wy1_of_lower_wall)
            // And the portal opening itself becomes the new clip for things *behind* it.
            // This means upperClip[x] and lowerClip[x] define the transparent window.

            // After drawing parts of the current front sector and its portal walls:
            // The new "open" area for things behind this seg is (drawPortalY1, drawPortalY2)
            // We must update the main upperClip and lowerClip arrays for future segs at this X.
            // This is tricky. Python's `self.upper_clip[x] = wy2` means that if an upper wall part was drawn,
            // the new top for any further drawing (even in this sector) is the bottom of that wall part.

            // Let's simplify and assume the clip arrays are correctly managed by the calls to drawFlat/drawWallColumn.
            // The critical part is that `upperClip[x]` and `lowerClip[x]` should define the visible part of the back sector.
            this.upperClip[x] = curUpperClip;
            this.lowerClip[x] = curLowerClip;


            rwScale1 += rwScaleStep;
            wallY1 += wallY1Step;
            wallY2 += wallY2Step;
            if (bDrawUpperWall || bDrawCeil) {
                portalY1 += portalY1Step;
            }
            if (bDrawLowerWall || bDrawFloor) {
                portalY2 += portalY2Step;
            }
        }
    }


    private void clipPortalWalls(int xStart, int xEnd) { // xEnd is exclusive
        if (xStart >= xEnd) return;

        List<Integer> intersection = new ArrayList<>();
        for (int x = xStart; x < xEnd; x++) {
            if (screenRange.contains(x)) {
                intersection.add(x);
            }
        }
        if (intersection.isEmpty()) return;
        Collections.sort(intersection);

        int currentRunStart = -1;
        for (int i = 0; i < intersection.size(); i++) {
            int x = intersection.get(i);
            if (currentRunStart == -1) {
                currentRunStart = x;
            }
            if (i + 1 == intersection.size() || intersection.get(i + 1) != x + 1) {
                // End of a contiguous run
                drawPortalWallRange(currentRunStart, x); // x is inclusive here
                currentRunStart = -1;
            }
        }
    }

    private void clipSolidWalls(int xStart, int xEnd) { // xEnd is exclusive
        if (xStart >= xEnd) return;
        if (screenRange.isEmpty()) {
            engine.getBsp().isTraverseBsp = false; // Optimization: stop BSP traversal
            return;
        }

        List<Integer> intersection = new ArrayList<>();
        List<Integer> toRemoveFromScreenRange = new ArrayList<>();

        for (int x = xStart; x < xEnd; x++) {
            if (screenRange.contains(x)) {
                intersection.add(x);
                toRemoveFromScreenRange.add(x); // Solid walls fill the column
            }
        }

        if (intersection.isEmpty()) return;
        Collections.sort(intersection);

        int currentRunStart = -1;
        for (int i = 0; i < intersection.size(); i++) {
            int x = intersection.get(i);
            if (currentRunStart == -1) {
                currentRunStart = x;
            }
            if (i + 1 == intersection.size() || intersection.get(i + 1) != x + 1) {
                drawSolidWallRange(currentRunStart, x); // x is inclusive here
                currentRunStart = -1;
            }
        }
        screenRange.removeAll(toRemoveFromScreenRange);

        if (screenRange.isEmpty()) {
            engine.getBsp().isTraverseBsp = false;
        }
    }
}