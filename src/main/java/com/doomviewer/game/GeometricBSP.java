package com.doomviewer.game;

import com.doomviewer.misc.Constants;
import com.doomviewer.geometry.*;
import com.doomviewer.rendering.SectorRenderInfo;
import com.doomviewer.services.CollisionService;
import com.doomviewer.wad.datatypes.Linedef;
import com.doomviewer.wad.datatypes.Node;
import com.doomviewer.wad.datatypes.Seg;
import com.doomviewer.wad.datatypes.SubSector;

import java.util.List;
import java.util.logging.Logger;

/**
 * Enhanced BSP implementation using the new geometry classes.
 * Demonstrates cleaner and more maintainable geometric calculations.
 * This class implements CollisionService and can replace the original BSP.
 */
public class GeometricBSP implements CollisionService {
    private static final Logger LOGGER = Logger.getLogger(GeometricBSP.class.getName());
    public static final int SUB_SECTOR_IDENTIFIER = 0x8000;

    private DoomEngine engine;
    private Player player;
    private List<Node> nodes;
    private List<SubSector> subSectors;
    private List<Seg> segs;
    private int rootNodeId;
    private Projection projection;
    private Angle fieldOfView;
    public boolean isTraverseBsp;

    public GeometricBSP(DoomEngine engine) {
        this.engine = engine;
        this.player = null;
        this.nodes = engine.getWadData().nodes;
        this.subSectors = engine.getWadData().subSectors;
        this.segs = engine.getWadData().segments;
        this.rootNodeId = this.nodes.size() - 1;
        this.isTraverseBsp = true;
        
        // Initialize projection and field of view
        this.projection = new Projection(Constants.SCREEN_DIST, Constants.WIDTH, Constants.HEIGHT);
        this.fieldOfView = DoomGeometryUtils.calculateFOV(Constants.WIDTH, Constants.SCREEN_DIST);
    }

    public void update() {
        if (this.player == null) {
            this.player = engine.getPlayer();
        }
        if (this.player == null) {
            return;
        }
        
        this.isTraverseBsp = true;
        renderBspNode(this.rootNodeId);
    }

    /**
     * Enhanced BSP traversal using geometry classes for cleaner side determination.
     */
    private void renderBspNode(int nodeId) {
        if (!this.isTraverseBsp) return;

        if ((nodeId & SUB_SECTOR_IDENTIFIER) != 0) {
            // This is a subsector
            int subSectorId = nodeId & ~SUB_SECTOR_IDENTIFIER;
            if (subSectorId < subSectors.size()) {
                renderSubSector(subSectors.get(subSectorId));
            }
            return;
        }

        if (nodeId >= nodes.size()) return;

        Node node = nodes.get(nodeId);
        Point2D playerPos = new Point2D(player.pos.x, player.pos.y);
        
        // Use geometry classes for cleaner side determination
        boolean isOnFrontSide = DoomGeometryUtils.isPointOnFrontSide(playerPos, node);

        if (isOnFrontSide) {
            // Render back side first (farther from player)
            renderBspNode(node.backChildId);
            renderBspNode(node.frontChildId);
        } else {
            // Render front side first (farther from player)
            renderBspNode(node.frontChildId);
            renderBspNode(node.backChildId);
        }
    }

    /**
     * Enhanced subsector rendering with geometry-based culling.
     */
    private void renderSubSector(SubSector subSector) {
        if (!this.isTraverseBsp) return;

        Point2D playerPos = new Point2D(player.pos.x, player.pos.y);
        Angle playerAngle = Angle.degrees(player.angle);

        for (int i = 0; i < subSector.segCount; i++) {
            int segIndex = subSector.firstSegId + i;
            if (segIndex >= segs.size()) continue;

            Seg seg = segs.get(segIndex);
            
            // Use geometry classes for visibility culling
            if (!isSegmentVisible(seg, playerPos, playerAngle)) {
                continue;
            }

            // Project segment to screen space
            Projection.ScreenSegment screenSeg = projectSegmentToScreen(seg, playerPos, playerAngle);
            if (screenSeg == null || !screenSeg.isVisible()) {
                continue;
            }

            // Calculate the angle to the start of the segment
            Point2D segStart = new Point2D(seg.startVertex.x, seg.startVertex.y);
            Vector2D toSegStart = playerPos.vectorTo(segStart);
            Angle rwAngle1 = toSegStart.angle();

            // Pass to segment handler for rendering
            if (engine.getSegHandler() != null) {
                engine.getSegHandler().classifySegment(seg, screenSeg.startX, screenSeg.endX, rwAngle1);
            }
        }
    }

    /**
     * Enhanced visibility test using geometry classes.
     */
    private boolean isSegmentVisible(Seg seg, Point2D playerPos, Angle playerAngle) {
        // Quick distance check
        Point2D segStart = new Point2D(seg.startVertex.x, seg.startVertex.y);
        Point2D segEnd = new Point2D(seg.endVertex.x, seg.endVertex.y);
        
        double distanceToStart = playerPos.distanceTo(segStart);
        double distanceToEnd = playerPos.distanceTo(segEnd);
        
        // Skip very distant segments (simple LOD)
        double maxRenderDistance = 2000.0; // Configurable
        if (distanceToStart > maxRenderDistance && distanceToEnd > maxRenderDistance) {
            return false;
        }

        // Use DoomGeometryUtils for comprehensive visibility check
        return DoomGeometryUtils.isWallPotentiallyVisible(seg, playerPos, playerAngle, fieldOfView);
    }

    /**
     * Projects a segment to screen space using the geometry classes.
     */
    private Projection.ScreenSegment projectSegmentToScreen(Seg seg, Point2D playerPos, Angle playerAngle) {
        LineSegment2D segment = DoomGeometryUtils.segToLineSegment(seg);
        
        // For now, assume floor and ceiling heights (these would come from sectors)
        double floorHeight = 0;
        double ceilHeight = 128;
        
        return projection.projectSegmentToScreen(segment, floorHeight, ceilHeight, playerPos, playerAngle);
    }

    /**
     * Enhanced collision detection using geometry classes.
     */
    public boolean checkCollision(double newX, double newY, double radius) {
        Point2D newPos = new Point2D(newX, newY);
        
        // Check collision with all line segments
        for (Seg seg : segs) {
            if (seg.linedef == null) continue;
            
            // Skip two-sided lines (portals) for basic collision
            if (seg.linedef.backSidedef != null) continue;
            
            LineSegment2D wall = DoomGeometryUtils.segToLineSegment(seg);
            double distance = wall.distanceToPoint(newPos);
            
            if (distance < radius) {
                return true; // Collision detected
            }
        }
        
        return false;
    }

    /**
     * Enhanced line-of-sight check using geometry classes.
     */
    public boolean hasLineOfSight(Point2D from, Point2D to) {
        LineSegment2D sightLine = new LineSegment2D(from, to);
        
        // Check intersection with all solid walls
        for (Seg seg : segs) {
            if (seg.linedef == null) continue;
            
            // Skip two-sided lines (portals) for line of sight
            if (seg.linedef.backSidedef != null) continue;
            
            LineSegment2D wall = DoomGeometryUtils.segToLineSegment(seg);
            if (sightLine.intersectsWith(wall)) {
                return false; // Line of sight blocked
            }
        }
        
        return true;
    }

    /**
     * Finds the closest wall to a given point.
     */
    public Seg findClosestWall(Point2D point) {
        Seg closestSeg = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (Seg seg : segs) {
            double distance = DoomGeometryUtils.distanceToWall(point, seg);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestSeg = seg;
            }
        }
        
        return closestSeg;
    }

    /**
     * Gets all walls within a certain radius of a point.
     */
    public List<Seg> getWallsInRadius(Point2D center, double radius) {
        return segs.stream()
                   .filter(seg -> DoomGeometryUtils.distanceToWall(center, seg) <= radius)
                   .toList();
    }

    /**
     * Calculates the bounding box of the entire level.
     */
    public GeometryUtils.BoundingBox getLevelBounds() {
        if (segs.isEmpty()) {
            return new GeometryUtils.BoundingBox(0, 0, 0, 0);
        }
        
        return DoomGeometryUtils.wallsBoundingBox(segs.toArray(new Seg[0]));
    }

    /**
     * Normalizes an angle to the range [-180, 180] degrees.
     * This method is kept for compatibility with existing code.
     */
    public static double normalizeAngle(double angleDegrees) {
        return Angle.degrees(angleDegrees).normalize().degrees();
    }

    // Getters for compatibility
    public Projection getProjection() { return projection; }
    public Angle getFieldOfView() { return fieldOfView; }
    
    /**
     * Enhanced movement blocking check (3-parameter version).
     */
    public boolean isMovementBlocked(Point2D start, Point2D end, double radius) {
        // Simple implementation: check if the end position would cause collision
        return checkCollision(end.x, end.y, radius);
    }
    
    /**
     * Enhanced movement blocking check with logging support.
     */
    public boolean isMovementBlocked(Point2D start, Point2D end, double radius, boolean logCollisions) {
        // For now, delegate to the simpler version
        return isMovementBlocked(start, end, radius);
    }
    
    /**
     * Get the height of the subsector at the given coordinates.
     * This method is required by CollisionService interface.
     */
    @Override
    public double getSubSectorHeightAt(double x, double y) {
        // Find the subsector containing this point
        Point2D point = new Point2D(x, y);
        
        // For now, return a default height
        // In a full implementation, this would traverse the BSP tree to find the correct subsector
        return 0.0;
    }
    
    // CollisionService interface implementation with adapter methods
    
    @Override
    public boolean isMovementBlocked(com.doomviewer.misc.math.Vector2D start, com.doomviewer.misc.math.Vector2D end, double radius, boolean logCollisions) {
        Point2D startPoint = new Point2D(start.x, start.y);
        Point2D endPoint = new Point2D(end.x, end.y);
        return isMovementBlocked(startPoint, endPoint, radius, logCollisions);
    }
    
    @Override
    public com.doomviewer.misc.math.Vector2D getSafeMovementPosition(com.doomviewer.misc.math.Vector2D start, com.doomviewer.misc.math.Vector2D desired, double radius, boolean logCollisions) {
        // For now, return the desired position if movement is not blocked, otherwise return start
        if (!isMovementBlocked(start, desired, radius, logCollisions)) {
            return desired;
        }
        return start;
    }
    
    @Override
    public boolean isPositionValid(com.doomviewer.misc.math.Vector2D position, double radius) {
        // Check if the position is within level bounds and not colliding with walls
        Point2D point = new Point2D(position.x, position.y);
        GeometryUtils.BoundingBox bounds = getLevelBounds();
        
        if (point.x < bounds.minX || point.x > bounds.maxX || 
            point.y < bounds.minY || point.y > bounds.maxY) {
            return false;
        }
        
        // Check collision with walls
        return !isMovementBlocked(point, point, radius, false);
    }
    
    @Override
    public boolean hasLineOfSight(com.doomviewer.misc.math.Vector2D start, com.doomviewer.misc.math.Vector2D end) {
        Point2D startPoint = new Point2D(start.x, start.y);
        Point2D endPoint = new Point2D(end.x, end.y);
        return hasLineOfSight(startPoint, endPoint);
    }
    
    @Override
    public boolean circleIntersectsLineSegment(com.doomviewer.misc.math.Vector2D circleCenter, double radius, com.doomviewer.misc.math.Vector2D lineStart, com.doomviewer.misc.math.Vector2D lineEnd) {
        Point2D center = new Point2D(circleCenter.x, circleCenter.y);
        Point2D start = new Point2D(lineStart.x, lineStart.y);
        Point2D end = new Point2D(lineEnd.x, lineEnd.y);
        LineSegment2D line = new LineSegment2D(start, end);
        
        return line.distanceToPoint(center) <= radius;
    }
}