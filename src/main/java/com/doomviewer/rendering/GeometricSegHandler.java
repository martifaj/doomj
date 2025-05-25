package com.doomviewer.rendering;

import com.doomviewer.misc.Constants;
import com.doomviewer.geometry.*;
import com.doomviewer.game.Player;
import com.doomviewer.game.DoomEngine;
import com.doomviewer.wad.WADDataService;
import com.doomviewer.wad.datatypes.Linedef;
import com.doomviewer.wad.datatypes.Sector;
import com.doomviewer.wad.datatypes.Seg;
import com.doomviewer.wad.datatypes.Sidedef;

import java.util.*;
import java.util.List;
import java.util.ArrayList;

/**
 * Enhanced SegHandler that uses the new geometry classes for cleaner and more maintainable code.
 * This demonstrates how the geometry package can be used to simplify complex rendering calculations.
 */
public class GeometricSegHandler {
    public static final double MAX_SCALE = 64.0;
    public static final double MIN_SCALE = 0.00390625; // 1/256

    private DoomEngine engine;
    private WADDataService wadDataService;
    private Player player;
    private int[] framebuffer;
    private Map<String, int[][]> textures;
    private String skyId;
    private Projection projection;

    private Seg currentSeg;
    private Angle rwAngle1; // Raw world angle to the start of the segment
    private Set<Integer> screenRange;
    private final Angle[] xToAngleTable; // Using Angle class instead of double array

    // Clipping arrays for portal rendering
    private int[] upperClip;
    private int[] lowerClip;
    
    // DrawSeg tracking for sprite occlusion
    private List<DrawSeg> drawSegs;

    public GeometricSegHandler(DoomEngine engine) {
        this.engine = engine;
        this.wadDataService = engine.getWadData();
        this.player = engine.getPlayer();
        this.framebuffer = engine.getFramebuffer();
        this.textures = this.wadDataService.assetData.textures;
        this.skyId = this.wadDataService.assetData.skyId;

        // Create projection helper
        this.projection = new Projection(Constants.SCREEN_DIST, Constants.WIDTH, Constants.HEIGHT);
        
        this.xToAngleTable = createXToAngleTable();
        this.upperClip = new int[Constants.WIDTH];
        this.lowerClip = new int[Constants.WIDTH];
        this.screenRange = new HashSet<>();
        this.drawSegs = new ArrayList<>();
    }

    public int[] getUpperClip() {
        return upperClip;
    }

    public int[] getLowerClip() {
        return lowerClip;
    }

    public void update() {
        initFloorCeilClipHeight();
        initScreenRange();
        drawSegs.clear();
    }
    
    public List<DrawSeg> getDrawSegs() {
        return drawSegs;
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
        Point2D playerPos = new Point2D(player.pos.x, player.pos.y);
        Point2D segStart = new Point2D(seg.startVertex.x, seg.startVertex.y);
        
        double hypotenuse = playerPos.distanceTo(segStart);
        double rwDistance = hypotenuse * offsetAngle.cos(); // Perpendicular distance to wall
        if (rwDistance < MIN_SCALE) rwDistance = MIN_SCALE;

        double rwScale1 = scaleFromGlobalAngle(x1, rwNormalAngle, rwDistance);

        // Apply the stretched line bug fix using geometry utilities
        double positiveOffsetAngle = offsetAngle.normalizeTo360().degrees();
        if (GeometryUtils.approximately(positiveOffsetAngle, 90.0, 1.0)) {
            rwScale1 *= 0.01;
        }

        double rwScaleStep = 0;
        if (x1 < x2) {
            double scale2 = scaleFromGlobalAngle(x2, rwNormalAngle, rwDistance);
            rwScaleStep = (scale2 - rwScale1) / (x2 - x1);
        }

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

        double wallY1 = Constants.H_HEIGHT - worldFrontZ1 * rwScale1;
        double wallY1Step = -rwScaleStep * worldFrontZ1;
        double wallY2 = Constants.H_HEIGHT - worldFrontZ2 * rwScale1;
        double wallY2Step = -rwScaleStep * worldFrontZ2;

        for (int x = x1; x <= x2; x++) {
            if (!screenRange.contains(x)) continue;

            int curUpperClip = upperClip[x];
            int curLowerClip = lowerClip[x];

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
                    double invScale = 1.0 / rwScale1;
                    double columnDepth = Constants.SCREEN_DIST / rwScale1;
                    
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

            // Update scales and Y positions for next column
            rwScale1 += rwScaleStep;
            wallY1 += wallY1Step;
            wallY2 += wallY2Step;
        }
        
        // Create DrawSeg for this solid wall segment
        double avgScale = (rwScale1 + scaleFromGlobalAngle(x2, rwNormalAngle, rwDistance)) / 2.0;
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

    private void clipPortalWalls(int x1, int x2) {
        // Portal wall implementation would go here
        // This would use similar geometry calculations but handle upper/lower walls separately
    }

    /**
     * Utility method to create a line segment from a Doom seg.
     */
    private LineSegment2D createLineSegmentFromSeg(Seg seg) {
        Point2D start = new Point2D(seg.startVertex.x, seg.startVertex.y);
        Point2D end = new Point2D(seg.endVertex.x, seg.endVertex.y);
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