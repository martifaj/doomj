package com.doomengine.rendering.bsp;

import com.doomengine.game.DoomEngine;
import com.doomengine.game.Player;
import com.doomengine.misc.Constants;
import com.doomengine.geometry.*;
import com.doomengine.services.CollisionService;
import com.doomengine.services.DoorService;
import com.doomengine.wad.datatypes.Node;
import com.doomengine.wad.datatypes.Sector;
import com.doomengine.wad.datatypes.Seg;
import com.doomengine.wad.datatypes.SubSector;

import java.util.List;
import java.util.logging.Logger;

/**
 * Enhanced BSP implementation using the new geometry classes.
 * Demonstrates cleaner and more maintainable geometric calculations.
 * This class implements CollisionService and can replace the original BSP.
 */
public class BSP implements CollisionService {
    private static final Logger LOGGER = Logger.getLogger(BSP.class.getName());
    public static final int SUB_SECTOR_IDENTIFIER = 0x8000;

    private final DoomEngine engine;
    private Player player;
    private final List<Node> nodes;
    private final List<SubSector> subSectors;
    private final List<Seg> segs;
    private final List<Sector> sectors;
    private final int rootNodeId;
    private final Projection projection;
    private final Angle fieldOfView;
    public boolean isTraverseBsp;
    private DoorService doorService; // To be injected

    public void setDoorService(DoorService doorService) {
        this.doorService = doorService;
    }

    public BSP(DoomEngine engine) {
        this.engine = engine;
        this.player = null;
        this.nodes = engine.getWadData().nodes;
        this.subSectors = engine.getWadData().subSectors;
        this.segs = engine.getWadData().segments;
        this.sectors = engine.getWadData().sectors;
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
     * BSP traversal matching original logic with geometry classes.
     */
    private void renderBspNode(int nodeId) {
        if (!this.isTraverseBsp) {
            return; // Stop traversal if SegHandler determined scene is full
        }

        if ((nodeId & SUB_SECTOR_IDENTIFIER) != 0) { // Check MSB
            int subSectorIdx = nodeId & (~SUB_SECTOR_IDENTIFIER); // Clear MSB to get index
            if (subSectorIdx < 0 || subSectorIdx >= subSectors.size()) {
                LOGGER.warning("Invalid subsector index from node ID: " + subSectorIdx);
                return;
            }
            renderSubSector(subSectorIdx);
            return;
        }

        if (nodeId < 0 || nodeId >= nodes.size()) {
            LOGGER.warning("Invalid node ID in renderBspNode: " + nodeId);
            return;
        }
        Node node = this.nodes.get(nodeId);

        boolean isOnBack = isOnBackSide(node, player.pos.x(), player.pos.y()); // Use current player position for rendering logic

        renderBspNode(isOnBack ? node.backChildId : node.frontChildId);

        Node.BBox otherBBox = isOnBack ? node.bbox.get("front") : node.bbox.get("back");
        if (checkBox(otherBBox)) {
            renderBspNode(isOnBack ? node.frontChildId : node.backChildId);
        }
    }
    
    /**
     * Check if bounding box is potentially visible (matching original logic).
     */
    private boolean checkBox(Node.BBox bbox) {
        double px = player.pos.x();
        double py = player.pos.y();

        if (px >= bbox.left && px <= bbox.right && py >= bbox.bottom && py <= bbox.top) {
            return true; // Player is inside the bbox
        }

        Point2D vBL = new Point2D(bbox.left, bbox.bottom);
        Point2D vTL = new Point2D(bbox.left, bbox.top);
        Point2D vTR = new Point2D(bbox.right, bbox.top);
        Point2D vBR = new Point2D(bbox.right, bbox.bottom);

        Point2D playerPos = new Point2D(px, py);
        double aBL = normalizeAngle180(pointToAngle(vBL, playerPos) - player.angle);
        double aTL = normalizeAngle180(pointToAngle(vTL, playerPos) - player.angle);
        double aTR = normalizeAngle180(pointToAngle(vTR, playerPos) - player.angle);
        double aBR = normalizeAngle180(pointToAngle(vBR, playerPos) - player.angle);

        if (Math.abs(aBL) <= Constants.H_FOV || Math.abs(aTL) <= Constants.H_FOV ||
                Math.abs(aTR) <= Constants.H_FOV || Math.abs(aBR) <= Constants.H_FOV) {
            return true;
        }

        double minAngle = Math.min(Math.min(aBL, aTL), Math.min(aTR, aBR));
        double maxAngle = Math.max(Math.max(aBL, aTL), Math.max(aTR, aBR));

        return maxAngle >= -Constants.H_FOV && minAngle <= Constants.H_FOV;
    }
    
    /**
     * Check which side of BSP partition the point is on (matching original logic).
     * Enhanced to use geometry classes.
     */
    private boolean isOnBackSide(Node node, double pX, double pY) {
        Point2D point = new Point2D(pX, pY);
        Point2D partitionPoint = new Point2D(node.xPartition, node.yPartition);
        Vector2D toPoint = partitionPoint.vectorTo(point);
        Vector2D partitionDirection = new Vector2D(node.dxPartition, node.dyPartition);
        
        // Cross product to determine which side of the partition line the point is on
        double crossProduct = toPoint.cross(partitionDirection);
        return crossProduct <= 0;
    }

    /**
     * Enhanced subsector rendering with geometry-based culling.
     */
    private void renderSubSector(int subSectorId) {
        SubSector subSector = this.subSectors.get(subSectorId);
        if (!this.isTraverseBsp) return;

        Point2D playerPos = new Point2D(player.pos.x(), player.pos.y());
        Angle playerAngle = Angle.degrees(player.angle);

        for (int i = 0; i < subSector.segCount; i++) {
            int segIndex = subSector.firstSegId + i;
            if (segIndex >= segs.size()) continue;

            Seg seg = segs.get(segIndex);
            
            // Use geometry classes for visibility culling
            if (!isSegmentVisible(seg, playerPos, playerAngle)) {
                continue;
            }

            // Use original BSP-style projection logic
            double[] segmentProjection = addSegmentToFov(seg, playerPos, playerAngle);
            if (segmentProjection != null) {
                // segmentProjection contains {x1, x2, rawWorldAngle1}
                double screenX1 = segmentProjection[0];
                double screenX2 = segmentProjection[1];
                Angle rwAngle1 = Angle.degrees(segmentProjection[2]);

                // Pass to segment handler for rendering
                if (engine.getSegHandler() != null) {
                    engine.getSegHandler().classifySegment(seg, screenX1, screenX2, rwAngle1);
                }
            }
        }
    }

    /**
     * Enhanced visibility test using geometry classes, based on original BSP logic.
     */
    private boolean isSegmentVisible(Seg seg, Point2D playerPos, Angle playerAngle) {
        Point2D segStart = new Point2D(seg.startVertex.x(), seg.startVertex.y());
        Point2D segEnd = new Point2D(seg.endVertex.x(), seg.endVertex.y());
        
        // Calculate angles to segment endpoints (similar to original pointToAngle)
        Vector2D toStart = playerPos.vectorTo(segStart);
        Vector2D toEnd = playerPos.vectorTo(segEnd);
        
        Angle angle1 = toStart.angle();
        Angle angle2 = toEnd.angle();
        
        // Calculate span (similar to original logic)
        double span = angle1.subtract(angle2).normalizeTo360().degrees();
        
        // Backface culling - if span >= 180, segment is facing away
        if (span >= 180.0) {
            return false;
        }
        
        // Quick distance check
        double distanceToStart = playerPos.distanceTo(segStart);
        double distanceToEnd = playerPos.distanceTo(segEnd);
        
        // Skip very distant segments (simple LOD)
        double maxRenderDistance = 2000.0; // Configurable
        return !(distanceToStart > maxRenderDistance) || !(distanceToEnd > maxRenderDistance);
    }

    /**
     * Original BSP-style segment projection logic using geometry classes.
     * Returns: {x1, x2, raw_world_angle1} or null
     */
    private double[] addSegmentToFov(Seg seg, Point2D playerPos, Angle playerAngle) {
        Point2D vertex1 = new Point2D(seg.startVertex.x(), seg.startVertex.y());
        Point2D vertex2 = new Point2D(seg.endVertex.x(), seg.endVertex.y());
        
        // Calculate angles to vertices (matching original pointToAngle)
        double angle1 = pointToAngle(vertex1, playerPos); // World angle to vertex1
        double angle2 = pointToAngle(vertex2, playerPos); // World angle to vertex2

        double span = normalizeAngle360(angle1 - angle2);

        // Backface culling
        if (span >= 180.0) {
            return null; // Segment is facing away or edge-on
        }

        // Store raw world angle for later use by SegHandler

        // Clip to FOV
        double pAngle = playerAngle.degrees();
        double tAngle1 = normalizeAngle360(angle1 - pAngle); // Angle of v1 relative to player's view direction
        double tAngle2 = normalizeAngle360(angle2 - pAngle); // Angle of v2 relative to player's view direction

        double r1 = normalizeAngle180(tAngle1);
        double r2 = normalizeAngle180(tAngle2);

        if (r2 > Constants.H_FOV && r1 > r2) return null; // Both right, r1 further right
        if (r2 < -Constants.H_FOV && r1 < r2) return null; // Both left, r1 further left

        double cAngle1 = Math.max(-Constants.H_FOV, Math.min(Constants.H_FOV, r1));
        double cAngle2 = Math.max(-Constants.H_FOV, Math.min(Constants.H_FOV, r2));

        if (cAngle1 == cAngle2) return null; // Too small or outside

        double tempAngle1 = tAngle1; // tempAngle1 is relative to player's view, 0 is forward. Can be > 180.
        double tempAngle2 = tAngle2;

        double s1 = normalizeAngle360(tempAngle1 + Constants.H_FOV); // Angle from left FOV edge (positive means inside or to the right)
        if (s1 > Constants.FOV) { // tempAngle1 is to the left of the left FOV edge
            if (s1 >= span + Constants.FOV) return null; // Segment entirely to the left of FOV
            tempAngle1 = Constants.H_FOV; // Clip to left FOV edge
        }

        double s2 = normalizeAngle360(Constants.H_FOV - tempAngle2); // Angle from right FOV edge (positive means inside or to the left)
        if (s2 > Constants.FOV) { // tempAngle2 is to the right of the right FOV edge
            if (s2 >= span + Constants.FOV) return null; // Segment entirely to the right of FOV
            tempAngle2 = -Constants.H_FOV; // Clip to right FOV edge
        }

        int x1 = angleToX(tempAngle1);
        int x2 = angleToX(tempAngle2);

        if (x1 == x2) return null; // Degenerate segment on screen

        return new double[]{x1, x2, angle1}; // Use double for x1, x2 before passing to SegHandler
    }
    
    /**
     * Calculate angle to a point (matching original pointToAngle).
     * Enhanced to use geometry classes.
     */
    private double pointToAngle(Point2D vertex, Point2D playerPos) {
        Vector2D toVertex = playerPos.vectorTo(vertex);
        Angle angle = toVertex.angle();
        return normalizeAngle360(angle.degrees()); // Ensure 0-360 range
    }
    
    /**
     * Convert angle to screen coordinate (matching original angleToX).
     */
    private int angleToX(double angle) { // angle is relative to player's FOV center
        return (int) (Constants.H_WIDTH - Math.tan(Math.toRadians(angle)) * Constants.SCREEN_DIST);
    }
    
    /**
     * Normalize angle to [0, 360) range.
     */
    private double normalizeAngle360(double angle) {
        angle %= 360.0;
        if (angle < 0) angle += 360.0;
        return angle;
    }
    
    /**
     * Normalize angle to [-180, 180) range.
     */
    private double normalizeAngle180(double angle) {
        angle = normalizeAngle360(angle);
        if (angle > 180.0) {
            angle -= 360.0;
        }
        return angle;
    }

    /**
     * Enhanced collision detection using geometry classes.
     */
    public boolean checkCollision(double newX, double newY, double radius) {
        Point2D newPos = new Point2D(newX, newY);
        
        // Check collision with all line segments
        for (Seg seg : segs) {
            if (seg.linedef == null) continue;
            
            // Only skip two-sided lines if they are actually passable (doors open, etc)
            if (seg.linedef.backSidedef != null && doorService != null && !doorService.isDoorBlocking(seg.linedefId)) {
                continue; // This is an open door or portal, so we can pass through
            }
            
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
            
            // Only skip two-sided lines if they are actually passable (doors open, etc)
            if (seg.linedef.backSidedef != null && doorService != null && !doorService.isDoorBlocking(seg.linedefId)) {
                continue; // This is an open door or portal, so line of sight can pass through
            }
            
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
        return checkCollision(end.x(), end.y(), radius);
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
        // Find the subsector containing this point by traversing the BSP tree
        int subSectorIndex = findSubSectorContainingPoint(x, y);
        
        if (subSectorIndex < 0 || subSectorIndex >= subSectors.size()) {
            return 0.0; // Default height if subsector not found
        }
        
        // Get the subsector and find its sector through one of its segments
        SubSector subSector = subSectors.get(subSectorIndex);
        
        // Get the first segment of this subsector to find the sector
        if (subSector.firstSegId >= 0 && subSector.firstSegId < segs.size()) {
            Seg seg = segs.get(subSector.firstSegId);
            if (seg.frontSector != null) {
                return seg.frontSector.floorHeight;
            }
        }
        
        return 0.0; // Default height if sector not found
    }
    
    /**
     * Traverse the BSP tree to find the subsector containing the given point.
     * @param x X coordinate
     * @param y Y coordinate
     * @return Index of the subsector containing the point, or -1 if not found
     */
    private int findSubSectorContainingPoint(double x, double y) {
        if (nodes.isEmpty()) {
            return -1;
        }
        
        int nodeId = rootNodeId;
        
        // Traverse the BSP tree
        while ((nodeId & SUB_SECTOR_IDENTIFIER) == 0) {
            // This is a node, not a subsector
            if (nodeId < 0 || nodeId >= nodes.size()) {
                return -1; // Invalid node
            }
            
            Node node = nodes.get(nodeId);
            
            // Determine which side of the partition line the point is on
            // The partition line is defined by (xPartition, yPartition) + t * (dxPartition, dyPartition)
            // We use the cross product to determine the side
            double dx = x - node.xPartition;
            double dy = y - node.yPartition;
            double cross = dx * node.dyPartition - dy * node.dxPartition;
            
            // If cross product is positive, point is on the front side (right side)
            // If negative, point is on the back side (left side)
            if (cross >= 0) {
                nodeId = node.frontChildId;
            } else {
                nodeId = node.backChildId;
            }
        }
        
        // We've reached a subsector (MSB is set)
        // Clear MSB to get index
        return nodeId & (~SUB_SECTOR_IDENTIFIER);
    }
    
    // CollisionService interface implementation with adapter methods
    
    @Override
    public boolean isMovementBlocked(com.doomengine.geometry.Vector2D start, com.doomengine.geometry.Vector2D end, double radius, boolean logCollisions) {
        Point2D startPoint = new Point2D(start.x(), start.y());
        Point2D endPoint = new Point2D(end.x(), end.y());
        return isMovementBlocked(startPoint, endPoint, radius, logCollisions);
    }
    
    @Override
    public com.doomengine.geometry.Vector2D getSafeMovementPosition(com.doomengine.geometry.Vector2D start, com.doomengine.geometry.Vector2D desired, double radius, boolean logCollisions) {
        // For now, return the desired position if movement is not blocked, otherwise return start
        if (!isMovementBlocked(start, desired, radius, logCollisions)) {
            return desired;
        }
        return start;
    }
    
    @Override
    public boolean isPositionValid(com.doomengine.geometry.Vector2D position, double radius) {
        // Check if the position is within level bounds and not colliding with walls
        Point2D point = new Point2D(position.x(), position.y());
        GeometryUtils.BoundingBox bounds = getLevelBounds();
        
        if (point.x() < bounds.minX() || point.x() > bounds.maxX() ||
            point.y() < bounds.minY() || point.y() > bounds.maxY()) {
            return false;
        }
        
        // Check collision with walls - position is valid if NO collision
        return !checkCollision(point.x(), point.y(), radius);
    }
    
    @Override
    public boolean hasLineOfSight(com.doomengine.geometry.Vector2D start, com.doomengine.geometry.Vector2D end) {
        Point2D startPoint = new Point2D(start.x(), start.y());
        Point2D endPoint = new Point2D(end.x(), end.y());
        return hasLineOfSight(startPoint, endPoint);
    }
    
    @Override
    public boolean circleIntersectsLineSegment(com.doomengine.geometry.Vector2D circleCenter, double radius, com.doomengine.geometry.Vector2D lineStart, com.doomengine.geometry.Vector2D lineEnd) {
        Point2D center = new Point2D(circleCenter.x(), circleCenter.y());
        Point2D start = new Point2D(lineStart.x(), lineStart.y());
        Point2D end = new Point2D(lineEnd.x(), lineEnd.y());
        LineSegment2D line = new LineSegment2D(start, end);
        
        return line.distanceToPoint(center) <= radius;
    }
}