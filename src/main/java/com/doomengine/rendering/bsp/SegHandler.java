package com.doomengine.rendering.bsp;

import com.doomengine.misc.Constants;
import com.doomengine.geometry.*;
import com.doomengine.game.Player;
import com.doomengine.game.DoomEngine;
import com.doomengine.rendering.DrawSeg;
import com.doomengine.rendering.ViewRenderer;
import com.doomengine.wad.WADDataService;
import com.doomengine.wad.datatypes.Linedef;
import com.doomengine.wad.datatypes.Sector;
import com.doomengine.wad.datatypes.Seg;
import com.doomengine.wad.datatypes.Sidedef;

import java.util.*;

/**
 * Enhanced SegHandler that uses the new geometry classes for cleaner and more maintainable code.
 * This demonstrates how the geometry package can be used to simplify complex rendering calculations.
 */
public class SegHandler {
    public static final double MAX_SCALE = 64.0;
    public static final double MIN_SCALE = 0.00390625; // 1/256

    private final DoomEngine engine;
    private final WADDataService wadDataService;
    private final Player player;
    private final int[] framebuffer;
    private final Map<String, int[][]> textures;
    private final String skyId;

    private Seg currentSeg;
    private Angle rwAngle1; // Raw world angle to the start of the segment
    private final Set<Integer> screenRange;
    private final Angle[] xToAngleTable; // Using Angle class instead of double array

    // Clipping arrays for portal rendering
    private final int[] upperClip;
    private final int[] lowerClip;
    
    // DrawSeg tracking for sprite occlusion
    private final List<DrawSeg> drawSegs;

    public SegHandler(DoomEngine engine) {
        this.engine = engine;
        this.wadDataService = engine.getWadData();
        this.player = engine.getPlayer();
        this.framebuffer = engine.getFramebuffer();
        this.textures = this.wadDataService.assetData.textures;
        this.skyId = this.wadDataService.assetData.skyId;

        this.xToAngleTable = createXToAngleTable();
        this.upperClip = new int[Constants.WIDTH];
        this.lowerClip = new int[Constants.WIDTH];
        this.screenRange = new HashSet<>();
        this.drawSegs = new ArrayList<>();
    }

    public void update() {
        initFloorCeilClipHeight();
        initScreenRange();
        drawSegs.clear();
    }
    
    // Compatibility method for ViewRenderer which expects double[]
    public double[] getXToAngleTable() {
        double[] doubleArray = new double[xToAngleTable.length];
        for (int i = 0; i < xToAngleTable.length; i++) {
            doubleArray[i] = xToAngleTable[i].degrees();
        }
        return doubleArray;
    }
    
    // Direct access to the Angle array for geometry-aware code
    public Angle[] getXToAngleTableAsAngles() {
        return xToAngleTable;
    }

    private void initFloorCeilClipHeight() {
        Arrays.fill(upperClip, -1);
        Arrays.fill(lowerClip, Constants.HEIGHT);
    }

    /**
     * Creates the x-to-angle table using the new Angle class for better type safety.
     */
    private static Angle[] createXToAngleTable() {
        Angle[] table = new Angle[Constants.WIDTH + 1];
        for (int i = 0; i <= Constants.WIDTH; i++) {
            // Angle of screen column i relative to view center
            double angleRad = Math.atan2((double) Constants.H_WIDTH - i, Constants.SCREEN_DIST);
            table[i] = Angle.radians(angleRad);
        }
        return table;
    }

    /**
     * Enhanced scale calculation using geometry classes for cleaner code.
     */
    private double scaleFromGlobalAngle(int x, Angle rwNormalAngle, double rwDistance) {
        Angle xAngle = this.xToAngleTable[x];
        Angle playerAngle = Angle.degrees(player.angle);
        
        // Calculate the angle between wall normal and ray to screen column x
        Angle angleDiff = rwNormalAngle.subtract(playerAngle).subtract(xAngle);
        double cosTheta = angleDiff.cos();

        double numerator = Constants.SCREEN_DIST * cosTheta;
        double denominator = rwDistance * xAngle.cos();

        if (Math.abs(denominator) < 1e-6) return MAX_SCALE;

        double scale = numerator / denominator;
        return GeometryUtils.clamp(scale, MIN_SCALE, MAX_SCALE);
    }

    private void initScreenRange() {
        this.screenRange.clear();
        for (int i = 0; i < Constants.WIDTH; i++) {
            this.screenRange.add(i);
        }
    }

    /**
     * Enhanced segment classification using geometry classes.
     */
    public void classifySegment(Seg seg, double screenX1, double screenX2, Angle rwAngle1) {
        this.currentSeg = seg;
        this.rwAngle1 = rwAngle1;

        int x1 = (int) Math.round(screenX1);
        int x2 = (int) Math.round(screenX2);

        // Ensure x1 < x2 for looping logic
        if (x1 > x2) {
            int temp = x1;
            x1 = x2;
            x2 = temp;
        }

        // Clip to screen bounds using geometry utilities
        x1 = GeometryUtils.clamp(x1, 0, Constants.WIDTH - 1);
        x2 = GeometryUtils.clamp(x2, 0, Constants.WIDTH - 1);

        if (x1 >= x2) return;

        Sector frontSector = seg.frontSector;
        Sector backSector = seg.backSector;

        if (backSector == null) {
            clipSolidWalls(x1, x2);
        } else {
            clipPortalWalls(x1, x2);
        }
    }

    /**
     * Enhanced wall rendering using geometry classes for distance and angle calculations.
     */
    private void drawSolidWallRange(int x1, int x2) {
        if (x1 > x2) return;

        Seg seg = this.currentSeg;
        Sector frontSector = seg.frontSector;
        Linedef line = seg.linedef;
        Sidedef side = line.frontSidedef;
        ViewRenderer renderer = engine.getViewRenderer();

        String wallTextureId = side.middleTexture;
        String ceilTextureId = frontSector.ceilTexture;
        String floorTextureId = frontSector.floorTexture;
        double lightLevel = frontSector.lightLevel;

        double playerEyeLevel = player.getEyeLevelViewZ();
        double worldFrontZ1 = frontSector.ceilHeight - playerEyeLevel;
        double worldFrontZ2 = frontSector.floorHeight - playerEyeLevel;

        boolean bDrawWall = !"-".equals(wallTextureId) && textures.containsKey(wallTextureId);
        boolean bDrawCeil = worldFrontZ1 > 0 || ceilTextureId.equals(this.skyId);
        boolean bDrawFloor = worldFrontZ2 < 0;

        // Use geometry classes for cleaner angle and distance calculations
        Angle segAngle = Angle.degrees(seg.angle);
        Angle rwNormalAngle = segAngle.add(Angle.degrees(90)); // Wall normal
        Angle offsetAngle = rwNormalAngle.subtract(this.rwAngle1);

        // Create points for distance calculation
        Point2D playerPos = new Point2D(player.pos.x(), player.pos.y());
        Point2D segStart = new Point2D(seg.startVertex.x(), seg.startVertex.y());
        
        double hypotenuse = playerPos.distanceTo(segStart);
        double rwDistance = hypotenuse * offsetAngle.cos(); // Perpendicular distance to wall
        if (rwDistance < MIN_SCALE) rwDistance = MIN_SCALE;

        double rwScale1 = scaleFromGlobalAngle(x1, rwNormalAngle, rwDistance);

        // Apply the stretched line bug fix using geometry utilities
        double positiveOffsetAngle = offsetAngle.normalizeTo360().degrees();
        boolean applyStretchFix = GeometryUtils.approximately(positiveOffsetAngle, 90.0, 1.0);
        if (applyStretchFix) {
        }

        // Note: We'll calculate scale per-column instead of interpolating linearly
        // This fixes the wall height inaccuracy bug when moving back from walls

        int[][] wallTexture = bDrawWall ? textures.get(wallTextureId) : null;
        double middleTexAlt = 0;
        if (bDrawWall) {
            if ((line.flags & WADDataService.LINEDEF_FLAGS_MAP.get("DONT_PEG_BOTTOM")) != 0) {
                middleTexAlt = (frontSector.floorHeight + wallTexture[0].length) - playerEyeLevel;
            } else {
                middleTexAlt = worldFrontZ1;
            }
            middleTexAlt += side.yOffset;
        }

        double rwOffset = hypotenuse * offsetAngle.sin();
        rwOffset += seg.offset + side.xOffset;

        Angle playerAngle = Angle.degrees(player.angle);
        Angle rwCenterAngle = rwNormalAngle.subtract(playerAngle);

        for (int x = x1; x <= x2; x++) {
            if (!screenRange.contains(x)) continue;

            int curUpperClip = upperClip[x];
            int curLowerClip = lowerClip[x];

            // Calculate accurate scale for this specific column
            double currentScale = scaleFromGlobalAngle(x, rwNormalAngle, rwDistance);
            
            // Apply stretch fix if needed
            if (applyStretchFix) {
                currentScale *= 0.01;
            }

            // Calculate wall Y positions using the accurate scale for this column
            double wallY1 = Constants.H_HEIGHT - worldFrontZ1 * currentScale;
            double wallY2 = Constants.H_HEIGHT - worldFrontZ2 * currentScale;

            int drawWallY1 = (int) Math.round(wallY1);
            int drawWallY2 = (int) Math.round(wallY2);

            if (bDrawCeil) {
                int cy1 = curUpperClip + 1;
                int cy2 = Math.min(drawWallY1 - 1, curLowerClip - 1);
                if (cy1 <= cy2) {
                    renderer.drawFlat(framebuffer, ceilTextureId, lightLevel, x, cy1, cy2, 
                                    frontSector.ceilHeight, xToAngleTable[x].degrees());
                }
            }

            if (bDrawWall) {
                int wy1 = Math.max(drawWallY1, curUpperClip + 1);
                int wy2 = Math.min(drawWallY2 - 1, curLowerClip - 1);

                if (wy1 <= wy2) {
                    Angle angle = rwCenterAngle.subtract(xToAngleTable[x]);
                    double textureColumn = rwDistance * angle.tan() - rwOffset;
                    double invScale = 1.0 / currentScale;
                    double columnDepth = Constants.SCREEN_DIST / currentScale;
                    
                    ViewRenderer.drawWallColumn(framebuffer, engine.getDepthBuffer(), wallTexture, 
                                              textureColumn, x, wy1, wy2, middleTexAlt, invScale, 
                                              lightLevel, columnDepth);
                }
            }

            if (bDrawFloor) {
                int fy1 = Math.max(drawWallY2, curUpperClip + 1);
                int fy2 = curLowerClip - 1;
                if (fy1 <= fy2) {
                    renderer.drawFlat(framebuffer, floorTextureId, lightLevel, x, fy1, fy2, 
                                    frontSector.floorHeight, xToAngleTable[x].degrees());
                }
            }
            
            // Update clipping arrays for solid walls
            upperClip[x] = Constants.HEIGHT - 1;
            lowerClip[x] = 0;
        }
        
        // Create DrawSeg for this solid wall segment
        double avgScale = (scaleFromGlobalAngle(x1, rwNormalAngle, rwDistance) + scaleFromGlobalAngle(x2, rwNormalAngle, rwDistance)) / 2.0;
        createDrawSeg(x1, x2, avgScale, false, null);
    }

    /**
     * Creates a DrawSeg with proper occlusion information.
     */
    private void createDrawSeg(int x1, int x2, double scale, boolean isMasked, String maskedTextureName) {
        DrawSeg drawSeg = new DrawSeg(x1, x2, scale, isMasked, maskedTextureName, Constants.HEIGHT);
        
        // Fill occlusion arrays based on current clipping state
        for (int x = x1; x <= x2; x++) {
            int index = x - x1;
            if (index >= 0 && index < drawSeg.sprtopclip.length) {
                drawSeg.sprtopclip[index] = (short) upperClip[x];
                drawSeg.sprbottomclip[index] = (short) lowerClip[x];
            }
        }
        
        drawSegs.add(drawSeg);
    }

    // Placeholder methods for portal walls and clipping - these would be implemented similarly
    private void clipSolidWalls(int x1, int x2) {
        drawSolidWallRange(x1, x2);
        // Remove drawn columns from screen range
        for (int x = x1; x <= x2; x++) {
            screenRange.remove(x);
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

    /**
     * Portal wall rendering using geometry classes for enhanced calculations.
     */
    private void drawPortalWallRange(int x1, int x2) { // x2 is inclusive
        if (x1 > x2) return;

        Seg seg = this.currentSeg;
        Sector frontSector = seg.frontSector;
        Sector backSector = seg.backSector;
        Linedef line = seg.linedef;
        Sidedef side = line.frontSidedef;
        ViewRenderer renderer = engine.getViewRenderer();

        String upperWallTexId = side.upperTexture;
        String lowerWallTexId = side.lowerTexture;
        String ceilTexId = frontSector.ceilTexture;
        String floorTexId = frontSector.floorTexture;
        double light = frontSector.lightLevel;

        // Use player's current eye level for Z calculations
        double playerEyeLevel = player.getEyeLevelViewZ();
        double worldFrontZ1 = frontSector.ceilHeight - playerEyeLevel;
        double worldBackZ1 = backSector.ceilHeight - playerEyeLevel;
        double worldFrontZ2 = frontSector.floorHeight - playerEyeLevel;
        double worldBackZ2 = backSector.floorHeight - playerEyeLevel;

        if (frontSector.ceilTexture.equals(this.skyId) &&
                backSector.ceilTexture.equals(this.skyId)) {
            worldFrontZ1 = worldBackZ1; // Sky hack
        }

        boolean bDrawUpperWall = false, bDrawCeil = false;
        if (worldFrontZ1 != worldBackZ1 ||
                !frontSector.ceilTexture.equals(backSector.ceilTexture) ||
                frontSector.lightLevel != backSector.lightLevel) {
            bDrawCeil = worldFrontZ1 > 0 || ceilTexId.equals(this.skyId);
            bDrawUpperWall = !"-".equals(upperWallTexId) && textures.containsKey(upperWallTexId);
        }

        boolean bDrawLowerWall = false, bDrawFloor = false;
        if (worldFrontZ2 != worldBackZ2 ||
                !frontSector.floorTexture.equals(backSector.floorTexture) ||
                frontSector.lightLevel != backSector.lightLevel) {
            bDrawFloor = worldFrontZ2 < 0;
            bDrawLowerWall = !"-".equals(lowerWallTexId) && textures.containsKey(lowerWallTexId);
        }

        // Enhanced geometry calculations using geometry classes
        Angle segAngle = Angle.degrees(seg.angle);
        Angle rwNormalAngle = segAngle.add(Angle.degrees(90)); // Wall normal
        Angle offsetAngle = rwNormalAngle.subtract(this.rwAngle1);

        Point2D playerPos = new Point2D(player.pos.x(), player.pos.y());
        Point2D segStart = new Point2D(seg.startVertex.x(), seg.startVertex.y());
        
        double hypotenuse = playerPos.distanceTo(segStart);
        double rwDistance = hypotenuse * offsetAngle.cos();
        if (rwDistance < MIN_SCALE) rwDistance = MIN_SCALE;

        // Note: We'll calculate scale per-column instead of interpolating linearly
        // This fixes the wall height inaccuracy bug when moving back from walls

        // Texture setup for upper and lower walls
        int[][] upperTexture = bDrawUpperWall ? textures.get(upperWallTexId) : null;
        int[][] lowerTexture = bDrawLowerWall ? textures.get(lowerWallTexId) : null;
        
        double upperTexAlt = 0, lowerTexAlt = 0;
        if (bDrawUpperWall) {
            if ((line.flags & WADDataService.LINEDEF_FLAGS_MAP.get("DONT_PEG_TOP")) != 0) {
                upperTexAlt = worldFrontZ1;
            } else {
                upperTexAlt = worldBackZ1 + upperTexture[0].length;
            }
            upperTexAlt += side.yOffset;
        }
        
        if (bDrawLowerWall) {
            if ((line.flags & WADDataService.LINEDEF_FLAGS_MAP.get("DONT_PEG_BOTTOM")) != 0) {
                lowerTexAlt = worldFrontZ1;
            } else {
                lowerTexAlt = worldBackZ2;
            }
            lowerTexAlt += side.yOffset;
        }

        double rwOffset = hypotenuse * offsetAngle.sin();
        rwOffset += seg.offset + side.xOffset;

        Angle playerAngle = Angle.degrees(player.angle);
        Angle rwCenterAngle = rwNormalAngle.subtract(playerAngle);

        for (int x = x1; x <= x2; x++) {
            if (!screenRange.contains(x)) continue;

            int curUpperClip = upperClip[x];
            int curLowerClip = lowerClip[x];

            // Calculate accurate scale for this specific column
            double currentScale = scaleFromGlobalAngle(x, rwNormalAngle, rwDistance);

            // Calculate wall positions using the accurate scale for this column
            double wallY1 = Constants.H_HEIGHT - worldFrontZ1 * currentScale; // Front ceiling
            double wallY2 = Constants.H_HEIGHT - worldFrontZ2 * currentScale; // Front floor

            double portalY1 = 0; // Back ceiling
            if (bDrawUpperWall || bDrawCeil) {
                portalY1 = (worldBackZ1 > worldFrontZ2) ? (Constants.H_HEIGHT - worldBackZ1 * currentScale) : wallY2;
            }

            double portalY2 = 0; // Back floor
            if (bDrawLowerWall || bDrawFloor) {
                portalY2 = (worldBackZ2 < worldFrontZ1) ? (Constants.H_HEIGHT - worldBackZ2 * currentScale) : wallY1;
            }

            int drawWallY1 = (int) Math.round(wallY1);       // Front ceil
            int drawWallY2 = (int) Math.round(wallY2);       // Front floor
            int drawPortalY1 = (int) Math.round(portalY1);   // Back ceil
            int drawPortalY2 = (int) Math.round(portalY2);   // Back floor

            double textureColumn = 0, invScale = 0;
            if (bDrawUpperWall || bDrawLowerWall) {
                Angle angle = rwCenterAngle.subtract(xToAngleTable[x]);
                textureColumn = rwDistance * angle.tan() - rwOffset;
                invScale = 1.0 / currentScale;
            }

            if (bDrawCeil) {
                int cy1 = curUpperClip + 1;
                int cy2 = Math.min(drawWallY1 - 1, curLowerClip - 1);
                if (cy1 <= cy2) {
                    renderer.drawFlat(framebuffer, ceilTexId, light, x, cy1, cy2, frontSector.ceilHeight, xToAngleTable[x].degrees());
                    curUpperClip = Math.max(curUpperClip, cy2);
                }
            }

            if (bDrawUpperWall) {
                int wy1 = Math.max(drawWallY1, curUpperClip + 1);
                int wy2 = Math.min(drawPortalY1 - 1, curLowerClip - 1);
                if (wy1 <= wy2) {
                    double columnDepth = Constants.SCREEN_DIST / currentScale;
                    ViewRenderer.drawWallColumn(framebuffer, engine.getDepthBuffer(), upperTexture, textureColumn, x, wy1, wy2, upperTexAlt, invScale, light, columnDepth);
                    curUpperClip = Math.max(curUpperClip, wy2);
                }
            }

            // Update clip for portal opening
            upperClip[x] = Math.max(curUpperClip, Math.max(drawWallY1, drawPortalY1 - 1));

            if (bDrawFloor) {
                int fy1 = Math.max(drawWallY2, curUpperClip + 1);
                int fy2 = curLowerClip - 1;
                if (fy1 <= fy2) {
                    renderer.drawFlat(framebuffer, floorTexId, light, x, fy1, fy2, frontSector.floorHeight, xToAngleTable[x].degrees());
                    curLowerClip = Math.min(curLowerClip, fy1);
                }
            }

            if (bDrawLowerWall) {
                int wy1 = Math.max(drawPortalY2, curUpperClip + 1);
                int wy2 = Math.min(drawWallY2 - 1, curLowerClip - 1);
                if (wy1 <= wy2) {
                    double columnDepth = Constants.SCREEN_DIST / currentScale;
                    ViewRenderer.drawWallColumn(framebuffer, engine.getDepthBuffer(), lowerTexture, textureColumn, x, wy1, wy2, lowerTexAlt, invScale, light, columnDepth);
                    curLowerClip = Math.min(curLowerClip, wy1);
                }
            }

            // Update clip for things seen through the portal
            this.upperClip[x] = curUpperClip;
            this.lowerClip[x] = curLowerClip;
        }

        // Create DrawSeg for this portal segment
        double avgScale = (scaleFromGlobalAngle(x1, rwNormalAngle, rwDistance) + scaleFromGlobalAngle(x2, rwNormalAngle, rwDistance)) / 2.0;
        createDrawSeg(x1, x2, avgScale, false, null);
    }

    /**
     * Utility method to create a line segment from a Doom seg.
     */
    private LineSegment2D createLineSegmentFromSeg(Seg seg) {
        Point2D start = new Point2D(seg.startVertex.x(), seg.startVertex.y());
        Point2D end = new Point2D(seg.endVertex.x(), seg.endVertex.y());
        return new LineSegment2D(start, end);
    }

    /**
     * Utility method to check if a point is on the front side of a wall segment.
     */
    public boolean isPointOnFrontSide(Point2D point, Seg seg) {
        LineSegment2D segment = createLineSegmentFromSeg(seg);
        return segment.isPointOnLeftSide(point);
    }

    /**
     * Calculates the perpendicular distance from a point to a wall segment.
     */
    public double distanceToWall(Point2D point, Seg seg) {
        LineSegment2D segment = createLineSegmentFromSeg(seg);
        return segment.distanceToPoint(point);
    }
}