package com.doomviewer.game;

import com.doomviewer.misc.Constants;
import com.doomviewer.misc.math.Vector2D;
import com.doomviewer.rendering.SectorRenderInfo;
import com.doomviewer.wad.datatypes.Node;
import com.doomviewer.wad.datatypes.Seg;
import com.doomviewer.wad.datatypes.SubSector;

import java.util.List;
import java.util.logging.Logger;

public class BSP {
    private static final Logger LOGGER = Logger.getLogger(BSP.class.getName());
    public static final int SUB_SECTOR_IDENTIFIER = 0x8000; // 2^15 = 32768

    private DoomEngine engine;
    private Player player;
    private List<Node> nodes;
    private List<SubSector> subSectors;
    private List<Seg> segs;
    private int rootNodeId;
    public boolean isTraverseBsp; // Public for SegHandler to modify

    public BSP(DoomEngine engine) {
        this.engine = engine;
        this.player = engine.getPlayer();
        this.nodes = engine.getWadData().nodes;
        this.subSectors = engine.getWadData().subSectors;
        this.segs = engine.getWadData().segments;
        this.rootNodeId = this.nodes.size() - 1;
        this.isTraverseBsp = true;
    }

    public void update() {
        this.isTraverseBsp = true; // Reset before each traversal
        renderBspNode(this.rootNodeId);
    }

    public SectorRenderInfo getSectorRenderInfoAt(double x, double y) {
        int currentNodeId = this.rootNodeId;

        while ((currentNodeId & SUB_SECTOR_IDENTIFIER) == 0) { // while it's a node ID (MSB is not set)
            if (currentNodeId < 0 || currentNodeId >= nodes.size()) {
                LOGGER.warning("Invalid node ID in getSectorRenderInfoAt: " + currentNodeId);
                return new SectorRenderInfo(0, 200, 128); // Default fallback
            }
            Node node = this.nodes.get(currentNodeId);
            boolean isOnBack = isOnBackSide(node, x, y);
            currentNodeId = isOnBack ? node.backChildId : node.frontChildId;
        }
        // It's a sub-sector ID
        int subSectorIdx = currentNodeId & (~SUB_SECTOR_IDENTIFIER); // Clear MSB to get index
        if (subSectorIdx < 0 || subSectorIdx >= subSectors.size()) {
            LOGGER.warning("Invalid subsector ID in getSectorRenderInfoAt: " + subSectorIdx);
            return new SectorRenderInfo(0, 200, 128); // Default fallback
        }
        SubSector subSector = this.subSectors.get(subSectorIdx);

        if (subSector.firstSegId < 0 || subSector.firstSegId >= segs.size()) {
            LOGGER.warning("Invalid firstSegId in subSector for getSectorRenderInfoAt: " + subSector.firstSegId);
            return new SectorRenderInfo(0, 200, 128); // Default fallback
        }
        Seg firstSeg = this.segs.get(subSector.firstSegId);

        if (firstSeg.frontSector == null) {
            LOGGER.warning("Seg's front sector is null in getSectorRenderInfoAt.");
            return new SectorRenderInfo(0, 200, 128); // Default fallback
        }
        // Assuming frontSector is always valid for a subsector's segs
        return new SectorRenderInfo(
                (double)firstSeg.frontSector.floorHeight,
                (double)firstSeg.frontSector.ceilHeight,
                (int)firstSeg.frontSector.lightLevel
        );
    }
    
    /**
     * Gets the floor height of the subsector at the specified coordinates.
     * 
     * @param x The x-coordinate in the world
     * @param y The y-coordinate in the world
     * @return The floor height at the specified position
     */
    public SubSector getSubSectorAt(double x, double y) {
        int currentNodeId = this.rootNodeId;

        while ((currentNodeId & SUB_SECTOR_IDENTIFIER) == 0) {
            if (currentNodeId < 0 || currentNodeId >= nodes.size()) {
                LOGGER.warning("Invalid node ID in getSubSectorAt: " + currentNodeId);
                return null;
            }
            Node node = this.nodes.get(currentNodeId);
            boolean isOnBack = isOnBackSide(node, x, y);
            currentNodeId = isOnBack ? node.backChildId : node.frontChildId;
        }
        
        int subSectorIdx = currentNodeId & (~SUB_SECTOR_IDENTIFIER);
        if (subSectorIdx < 0 || subSectorIdx >= subSectors.size()) {
            LOGGER.warning("Invalid subsector ID in getSubSectorAt: " + subSectorIdx);
            return null;
        }
        
        return this.subSectors.get(subSectorIdx);
    }

    public double getSubSectorHeightAt(double x, double y) {
        SectorRenderInfo info = getSectorRenderInfoAt(x, y);
        return info.floorHeight;
    }

    private static int angleToX(double angle) { // angle is relative to player's FOV center
        double x;
        if (angle > 0) { // Right of center view -> Left on screen (smaller x values in typical graphics)
            x = Constants.SCREEN_DIST - Math.tan(Math.toRadians(angle)) * Constants.H_WIDTH;
        } else { // Left of center view -> Right on screen
            x = -Math.tan(Math.toRadians(angle)) * Constants.H_WIDTH + Constants.SCREEN_DIST;
        }
        return (int) (Constants.H_WIDTH - Math.tan(Math.toRadians(angle)) * Constants.SCREEN_DIST);
    }

    // Returns: {x1, x2, raw_world_angle1} or null
    public double[] addSegmentToFov(Vector2D vertex1, Vector2D vertex2) {
        double angle1 = pointToAngle(vertex1); // World angle to vertex1
        double angle2 = pointToAngle(vertex2); // World angle to vertex2

        double span = normalizeAngle(angle1 - angle2);

        // Backface culling
        if (span >= 180.0) {
            return null; // Segment is facing away or edge-on
        }

        double rwAngle1 = angle1; // Store raw world angle for later use by SegHandler

        // Clip to FOV
        double pAngle = player.angle;
        double tAngle1 = normalizeAngle(angle1 - pAngle); // Angle of v1 relative to player's view direction
        double tAngle2 = normalizeAngle(angle2 - pAngle); // Angle of v2 relative to player's view direction

        double r1 = normalizeAngle180(tAngle1);
        double r2 = normalizeAngle180(tAngle2);

        if (r1 > Constants.H_FOV && r2 > Constants.H_FOV && r1 > r2) return null; // Both right, r1 further right
        if (r1 < -Constants.H_FOV && r2 < -Constants.H_FOV && r1 < r2) return null; // Both left, r1 further left

        double cAngle1 = Math.max(-Constants.H_FOV, Math.min(Constants.H_FOV, r1));
        double cAngle2 = Math.max(-Constants.H_FOV, Math.min(Constants.H_FOV, r2));

        if (cAngle1 == cAngle2) return null; // Too small or outside

        double tempAngle1 = tAngle1; // tempAngle1 is relative to player's view, 0 is forward. Can be > 180.
        double tempAngle2 = tAngle2;

        double s1 = normalizeAngle(tempAngle1 + Constants.H_FOV); // Angle from left FOV edge (positive means inside or to the right)
        if (s1 > Constants.FOV) { // tempAngle1 is to the left of the left FOV edge
            if (s1 >= span + Constants.FOV) return null; // Segment entirely to the left of FOV
            tempAngle1 = Constants.H_FOV; // Clip to left FOV edge
        }

        double s2 = normalizeAngle(Constants.H_FOV - tempAngle2); // Angle from right FOV edge (positive means inside or to the left)
        if (s2 > Constants.FOV) { // tempAngle2 is to the right of the right FOV edge
            if (s2 >= span + Constants.FOV) return null; // Segment entirely to the right of FOV
            tempAngle2 = -Constants.H_FOV; // Clip to right FOV edge
        }

        int x1 = angleToX(tempAngle1);
        int x2 = angleToX(tempAngle2);

        if (x1 == x2) return null; // Degenerate segment on screen

        return new double[]{x1, x2, rwAngle1}; // Use double for x1, x2 before passing to SegHandler
    }

    private void renderSubSector(int subSectorId) {
        SubSector subSector = this.subSectors.get(subSectorId);
        for (int i = 0; i < subSector.segCount; i++) {
            Seg seg = this.segs.get(subSector.firstSegId + i);
            if (seg.startVertex == null || seg.endVertex == null) {
                LOGGER.warning("Seg has null vertex. ID: " + (subSector.firstSegId + i));
                continue;
            }

            double[] result = addSegmentToFov(seg.startVertex, seg.endVertex);
            if (result != null) {
                engine.getSegHandler().classifySegment(seg, result[0], result[1], result[2]);
            }
        }
    }

    public static double normalizeAngle(double angle) {
        angle %= 360.0;
        if (angle < 0) angle += 360.0;
        return angle;
    }

    private double normalizeAngle180(double angle) {
        angle = normalizeAngle(angle);
        if (angle > 180.0) {
            angle -= 360.0;
        }
        return angle;
    }

    private boolean checkBox(Node.BBox bbox) {
        double px = player.pos.x;
        double py = player.pos.y;

        if (px >= bbox.left && px <= bbox.right && py >= bbox.bottom && py <= bbox.top) {
            return true; // Player is inside the bbox
        }

        Vector2D vBL = new Vector2D(bbox.left, bbox.bottom);
        Vector2D vTL = new Vector2D(bbox.left, bbox.top);
        Vector2D vTR = new Vector2D(bbox.right, bbox.top);
        Vector2D vBR = new Vector2D(bbox.right, bbox.bottom);

        double aBL = normalizeAngle180(pointToAngle(vBL) - player.angle);
        double aTL = normalizeAngle180(pointToAngle(vTL) - player.angle);
        double aTR = normalizeAngle180(pointToAngle(vTR) - player.angle);
        double aBR = normalizeAngle180(pointToAngle(vBR) - player.angle);

        if (Math.abs(aBL) <= Constants.H_FOV || Math.abs(aTL) <= Constants.H_FOV ||
                Math.abs(aTR) <= Constants.H_FOV || Math.abs(aBR) <= Constants.H_FOV) {
            return true;
        }

        double minAngle = Math.min(Math.min(aBL, aTL), Math.min(aTR, aBR));
        double maxAngle = Math.max(Math.max(aBL, aTL), Math.max(aTR, aBR));

        if (maxAngle >= -Constants.H_FOV && minAngle <= Constants.H_FOV) {
            return true;
        }

        return false; // Default to not visible if simple checks fail
    }

    private double pointToAngle(Vector2D vertex) {
        double dx = vertex.x - player.pos.x;
        double dy = vertex.y - player.pos.y;
        double angleRad = Math.atan2(dy, dx);
        double angleDeg = Math.toDegrees(angleRad);
        return normalizeAngle(angleDeg); // Ensure 0-360 range
    }

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

        boolean isOnBack = isOnBackSide(node, player.pos.x, player.pos.y); // Use current player position for rendering logic

        renderBspNode(isOnBack ? node.backChildId : node.frontChildId);

        Node.BBox otherBBox = isOnBack ? node.bbox.get("front") : node.bbox.get("back");
        if (checkBox(otherBBox)) {
            renderBspNode(isOnBack ? node.frontChildId : node.backChildId);
        }
    }

    private boolean isOnBackSide(Node node, double pX, double pY) {
        double dx = pX - node.xPartition;
        double dy = pY - node.yPartition;
        return (dx * node.dyPartition - dy * node.dxPartition) <= 0;
    }
    
    public boolean hasLineOfSight(Vector2D start, Vector2D end) {
        // Simple line of sight check - trace along the line and check for solid walls
        // This is a simplified implementation
        
        double distance = Vector2D.distance(start, end);
        int samples = Math.max(4, (int)(distance / 32.0)); // Sample every 32 units
        
        for (int i = 1; i < samples; i++) {
            double t = (double)i / samples;
            double x = start.x + t * (end.x - start.x);
            double y = start.y + t * (end.y - start.y);
            
            // Get the subsector at this point
            SubSector subSector = getSubSectorAt(x, y);
            if (subSector == null) continue;
            
            // Check all segs in this subsector for blocking walls
            for (int j = 0; j < subSector.segCount; j++) {
                Seg seg = segs.get(subSector.firstSegId + j);
                if (seg.backSector == null) { // Solid wall (no back sector)
                    // Check if line intersects this wall segment
                    if (lineIntersectsSegment(start, end, seg)) {
                        return false; // Line of sight blocked
                    }
                }
            }
        }
        
        return true; // No blocking walls found
    }
    
    private boolean lineIntersectsSegment(Vector2D lineStart, Vector2D lineEnd, Seg wallSeg) {
        // Check if line from lineStart to lineEnd intersects the wall segment
        Vector2D wallStart = wallSeg.startVertex;
        Vector2D wallEnd = wallSeg.endVertex;
        
        // Line intersection algorithm
        double d = (lineEnd.x - lineStart.x) * (wallEnd.y - wallStart.y) - (lineEnd.y - lineStart.y) * (wallEnd.x - wallStart.x);
        if (Math.abs(d) < 1e-10) return false; // Lines are parallel
        
        double t = ((wallStart.x - lineStart.x) * (wallEnd.y - wallStart.y) - (wallStart.y - lineStart.y) * (wallEnd.x - wallStart.x)) / d;
        double u = ((wallStart.x - lineStart.x) * (lineEnd.y - lineStart.y) - (wallStart.y - lineStart.y) * (lineEnd.x - lineStart.x)) / d;
        
        // Check if intersection occurs within both line segments
        return t >= 0 && t <= 1 && u >= 0 && u <= 1;
    }
    
    /**
     * Check if movement from start to end position is blocked by walls
     * @param start Starting position
     * @param end Ending position  
     * @param radius Entity radius for collision detection
     * @return true if movement is blocked, false if clear
     */
    public boolean isMovementBlocked(Vector2D start, Vector2D end, double radius) {
        // Check if the direct line from start to end intersects any solid walls
        if (hasWallCollision(start, end, radius)) {
            return true;
        }
        
        // Check if the destination position is valid (not inside a wall)
        return !isPositionValid(end, radius);
    }
    
    /**
     * Check if a circular entity at the given position collides with any walls
     * @param position Center position of the entity
     * @param radius Radius of the entity
     * @return true if position is valid (no collision), false if blocked
     */
    public boolean isPositionValid(Vector2D position, double radius) {
        // Get the subsector at this position
        SubSector subSector = getSubSectorAt(position.x, position.y);
        if (subSector == null) return false; // Outside valid map area
        
        // Check collision with all solid walls in nearby subsectors
        return !hasNearbyWallCollision(position, radius);
    }
    
    /**
     * Check if a line segment with thickness (entity radius) collides with walls
     * @param start Starting position
     * @param end Ending position
     * @param radius Thickness/radius of the moving entity
     * @return true if collision detected, false if clear
     */
    private boolean hasWallCollision(Vector2D start, Vector2D end, double radius) {
        // For entities with radius, we need to check multiple parallel lines
        Vector2D direction = end.subtract(start);
        double distance = Math.sqrt(direction.x * direction.x + direction.y * direction.y);
        
        if (distance < 1e-6) return false; // No movement
        
        // Normalize direction
        Vector2D normalizedDir = direction.scale(1.0 / distance);
        // Get perpendicular vector for width checks
        Vector2D perpendicular = new Vector2D(-normalizedDir.y, normalizedDir.x);
        
        // Check center line and edges
        Vector2D leftEdge = start.add(perpendicular.scale(radius));
        Vector2D rightEdge = start.subtract(perpendicular.scale(radius));
        Vector2D leftEdgeEnd = end.add(perpendicular.scale(radius));
        Vector2D rightEdgeEnd = end.subtract(perpendicular.scale(radius));
        
        // Check if any of the edge lines intersect with walls
        return checkLineCollision(start, end) || 
               checkLineCollision(leftEdge, leftEdgeEnd) || 
               checkLineCollision(rightEdge, rightEdgeEnd);
    }
    
    /**
     * Check if a line intersects with any solid walls
     * @param start Line start
     * @param end Line end
     * @return true if collision detected
     */
    private boolean checkLineCollision(Vector2D start, Vector2D end) {
        double distance = Vector2D.distance(start, end);
        int samples = Math.max(4, (int)(distance / 16.0)); // Sample every 16 units for better precision
        
        for (int i = 0; i <= samples; i++) {
            double t = (double)i / samples;
            double x = start.x + t * (end.x - start.x);
            double y = start.y + t * (end.y - start.y);
            
            SubSector subSector = getSubSectorAt(x, y);
            if (subSector == null) return true; // Outside valid area = collision
            
            // Check all segs in this subsector
            for (int j = 0; j < subSector.segCount; j++) {
                Seg seg = segs.get(subSector.firstSegId + j);
                if (seg.backSector == null) { // Solid wall
                    if (lineIntersectsSegment(start, end, seg)) {
                        return true; // Collision detected
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if a circular area around a position intersects with any walls
     * @param center Center position
     * @param radius Radius to check
     * @return true if collision detected
     */
    private boolean hasNearbyWallCollision(Vector2D center, double radius) {
        // Get subsector at center
        SubSector centerSubSector = getSubSectorAt(center.x, center.y);
        if (centerSubSector == null) return true;
        
        // Check collision with walls in this subsector
        for (int i = 0; i < centerSubSector.segCount; i++) {
            Seg seg = segs.get(centerSubSector.firstSegId + i);
            if (seg.backSector == null) { // Solid wall
                if (circleIntersectsLineSegment(center, radius, seg.startVertex, seg.endVertex)) {
                    return true;
                }
            }
        }
        
        // TODO: For better collision detection, we should also check neighboring subsectors
        // within the radius. For now, this basic check should work for most cases.
        
        return false;
    }
    
    /**
     * Check if a circle intersects with a line segment
     * @param circleCenter Center of the circle
     * @param radius Radius of the circle
     * @param lineStart Start of line segment
     * @param lineEnd End of line segment
     * @return true if they intersect
     */
    private boolean circleIntersectsLineSegment(Vector2D circleCenter, double radius, Vector2D lineStart, Vector2D lineEnd) {
        // Vector from line start to circle center
        Vector2D startToCenter = circleCenter.subtract(lineStart);
        // Vector along the line
        Vector2D lineVector = lineEnd.subtract(lineStart);
        
        double lineLengthSquared = lineVector.x * lineVector.x + lineVector.y * lineVector.y;
        if (lineLengthSquared < 1e-6) {
            // Line is essentially a point, check distance to that point
            return Vector2D.distance(circleCenter, lineStart) <= radius;
        }
        
        // Project circle center onto the line
        double t = Math.max(0, Math.min(1, 
            (startToCenter.x * lineVector.x + startToCenter.y * lineVector.y) / lineLengthSquared));
        
        // Find closest point on line segment
        Vector2D closestPoint = lineStart.add(lineVector.scale(t));
        
        // Check distance from circle center to closest point
        return Vector2D.distance(circleCenter, closestPoint) <= radius;
    }
    
    /**
     * Get a safe movement position that doesn't collide with walls
     * @param start Starting position
     * @param desired Desired ending position
     * @param radius Entity radius
     * @return Safe position to move to (may be closer to start if desired position is blocked)
     */
    public Vector2D getSafeMovementPosition(Vector2D start, Vector2D desired, double radius) {
        // If desired position is valid, return it
        if (!isMovementBlocked(start, desired, radius)) {
            return desired;
        }
        
        // Try to find the furthest safe position along the movement vector
        Vector2D direction = desired.subtract(start);
        double maxDistance = Math.sqrt(direction.x * direction.x + direction.y * direction.y);
        
        if (maxDistance < 1e-6) return start; // No movement
        
        Vector2D normalizedDir = direction.scale(1.0 / maxDistance);
        
        // Binary search for the furthest safe position
        double minSafe = 0;
        double maxUnsafe = maxDistance;
        
        for (int i = 0; i < 10; i++) { // 10 iterations should be enough precision
            double testDistance = (minSafe + maxUnsafe) / 2.0;
            Vector2D testPos = start.add(normalizedDir.scale(testDistance));
            
            if (isMovementBlocked(start, testPos, radius)) {
                maxUnsafe = testDistance;
            } else {
                minSafe = testDistance;
            }
        }
        
        Vector2D result = start.add(normalizedDir.scale(minSafe));
        
        // Debug output for collision blocking (only log significant blocks)
        if (Vector2D.distance(result, desired) > 8.0) {
            // System.out.println("BSP: Movement blocked, moved " + (int)minSafe + " of " + (int)maxDistance + " units");
        }
        
        return result;
    }
}

