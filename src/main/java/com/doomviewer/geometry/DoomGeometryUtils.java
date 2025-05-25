package com.doomviewer.geometry;

import com.doomviewer.wad.datatypes.Seg;
import com.doomviewer.wad.datatypes.Linedef;
import com.doomviewer.wad.datatypes.Node;

/**
 * Doom-specific geometry utilities that bridge the gap between
 * the generic geometry classes and Doom's data structures.
 */
public class DoomGeometryUtils {

    /**
     * Creates a LineSegment2D from a Doom Seg.
     */
    public static LineSegment2D segToLineSegment(Seg seg) {
        Point2D start = new Point2D(seg.startVertex.x, seg.startVertex.y);
        Point2D end = new Point2D(seg.endVertex.x, seg.endVertex.y);
        return new LineSegment2D(start, end);
    }

    /**
     * Creates a LineSegment2D from a Doom Linedef.
     */
    public static LineSegment2D linedefToLineSegment(Linedef linedef) {
        Point2D start = new Point2D(linedef.startVertex.x, linedef.startVertex.y);
        Point2D end = new Point2D(linedef.endVertex.x, linedef.endVertex.y);
        return new LineSegment2D(start, end);
    }

    /**
     * Creates a Line2D from a BSP node's partition line.
     */
    public static Line2D nodeToPartitionLine(Node node) {
        Point2D start = new Point2D(node.partitionLineStartX, node.partitionLineStartY);
        Vector2D direction = new Vector2D(node.changeInXAlongPartition, node.changeInYAlongPartition);
        Point2D end = start.add(direction);
        return new Line2D(start, end);
    }

    /**
     * Determines which side of a BSP partition line a point is on.
     * Returns positive if on front side, negative if on back side.
     */
    public static double pointSideOfPartition(Point2D point, Node node) {
        Line2D partitionLine = nodeToPartitionLine(node);
        return partitionLine.sideOfPoint(point);
    }

    /**
     * Checks if a point is on the front side of a BSP partition.
     */
    public static boolean isPointOnFrontSide(Point2D point, Node node) {
        return pointSideOfPartition(point, node) >= 0;
    }

    /**
     * Calculates the perpendicular distance from a point to a wall segment.
     */
    public static double distanceToWall(Point2D point, Seg seg) {
        LineSegment2D segment = segToLineSegment(seg);
        return segment.distanceToPoint(point);
    }

    /**
     * Checks if a point is on the front side of a wall segment.
     * In Doom, the front side is typically the side with the front sector.
     */
    public static boolean isPointOnFrontSideOfWall(Point2D point, Seg seg) {
        LineSegment2D segment = segToLineSegment(seg);
        // In Doom's coordinate system, front side is typically the left side
        return segment.isPointOnLeftSide(point);
    }

    /**
     * Calculates the angle of a wall segment.
     */
    public static Angle wallAngle(Seg seg) {
        LineSegment2D segment = segToLineSegment(seg);
        return segment.angle();
    }

    /**
     * Calculates the normal angle of a wall (perpendicular to the wall).
     */
    public static Angle wallNormalAngle(Seg seg) {
        return wallAngle(seg).add(Angle.degrees(90));
    }

    /**
     * Projects a 3D world point to screen coordinates using Doom's projection system.
     */
    public static Point2D projectWorldToScreen(Point3D worldPoint, Point2D playerPos, 
                                              Angle playerAngle, Projection projection) {
        // Transform to camera space
        Point3D cameraPos3D = new Point3D(playerPos.x, playerPos.y, 0);
        Point3D cameraSpace = projection.worldPointToCameraSpace(worldPoint, cameraPos3D, playerAngle);
        
        // Project to screen
        return projection.projectToScreen(cameraSpace);
    }

    /**
     * Calculates the field of view angle for a given screen width and distance.
     */
    public static Angle calculateFOV(double screenWidth, double screenDistance) {
        double halfFOVRad = Math.atan2(screenWidth / 2.0, screenDistance);
        return Angle.radians(2 * halfFOVRad);
    }

    /**
     * Checks if a wall segment is potentially visible from a given viewpoint.
     * This is a quick culling test before more expensive rendering calculations.
     */
    public static boolean isWallPotentiallyVisible(Seg seg, Point2D viewPoint, Angle viewAngle, Angle fovAngle) {
        LineSegment2D segment = segToLineSegment(seg);
        
        // Check if the wall is facing away from the viewer
        if (!segment.isPointOnLeftSide(viewPoint)) {
            return false; // Back-facing wall
        }
        
        // Check if the wall is within the field of view
        Vector2D toStart = viewPoint.vectorTo(segment.start);
        Vector2D toEnd = viewPoint.vectorTo(segment.end);
        
        Angle angleToStart = toStart.angle();
        Angle angleToEnd = toEnd.angle();
        
        Angle halfFOV = fovAngle.multiply(0.5);
        Angle minViewAngle = viewAngle.subtract(halfFOV);
        Angle maxViewAngle = viewAngle.add(halfFOV);
        
        // Simple FOV check (this could be more sophisticated)
        return isAngleInRange(angleToStart, minViewAngle, maxViewAngle) ||
               isAngleInRange(angleToEnd, minViewAngle, maxViewAngle);
    }

    /**
     * Checks if an angle is within a given range, handling angle wrapping.
     */
    private static boolean isAngleInRange(Angle angle, Angle minAngle, Angle maxAngle) {
        // Normalize all angles to [0, 360)
        double a = angle.normalizeTo360().degrees();
        double min = minAngle.normalizeTo360().degrees();
        double max = maxAngle.normalizeTo360().degrees();
        
        if (min <= max) {
            return a >= min && a <= max;
        } else {
            // Range wraps around 0/360
            return a >= min || a <= max;
        }
    }

    /**
     * Calculates the intersection point of two wall segments.
     */
    public static Point2D wallIntersection(Seg seg1, Seg seg2) {
        LineSegment2D segment1 = segToLineSegment(seg1);
        LineSegment2D segment2 = segToLineSegment(seg2);
        return segment1.intersectionWith(segment2);
    }

    /**
     * Checks if two wall segments intersect.
     */
    public static boolean wallsIntersect(Seg seg1, Seg seg2) {
        return wallIntersection(seg1, seg2) != null;
    }

    /**
     * Calculates the closest point on a wall to a given position.
     */
    public static Point2D closestPointOnWall(Point2D point, Seg seg) {
        LineSegment2D segment = segToLineSegment(seg);
        return segment.closestPointTo(point);
    }

    /**
     * Creates a bounding box for a set of wall segments.
     */
    public static GeometryUtils.BoundingBox wallsBoundingBox(Seg... segs) {
        if (segs.length == 0) {
            throw new IllegalArgumentException("Cannot create bounding box for empty wall set");
        }
        
        Point2D[] points = new Point2D[segs.length * 2];
        for (int i = 0; i < segs.length; i++) {
            points[i * 2] = new Point2D(segs[i].startVertex.x, segs[i].startVertex.y);
            points[i * 2 + 1] = new Point2D(segs[i].endVertex.x, segs[i].endVertex.y);
        }
        
        return GeometryUtils.boundingBox(points);
    }

    /**
     * Transforms a point from world coordinates to player-relative coordinates.
     */
    public static Point2D worldToPlayerSpace(Point2D worldPoint, Point2D playerPos, Angle playerAngle) {
        Transform2D transform = Transform2D.translation(-playerPos.x, -playerPos.y)
                                          .then(Transform2D.rotation(playerAngle.negate()));
        return transform.transform(worldPoint);
    }

    /**
     * Transforms a point from player-relative coordinates to world coordinates.
     */
    public static Point2D playerToWorldSpace(Point2D playerPoint, Point2D playerPos, Angle playerAngle) {
        Transform2D transform = Transform2D.rotation(playerAngle)
                                          .then(Transform2D.translation(playerPos.x, playerPos.y));
        return transform.transform(playerPoint);
    }

    /**
     * Calculates the texture coordinate along a wall segment.
     */
    public static double calculateTextureU(Point2D point, Seg seg, double textureWidth) {
        LineSegment2D segment = segToLineSegment(seg);
        Point2D closestPoint = segment.closestPointTo(point);
        double distanceAlongWall = segment.start.distanceTo(closestPoint);
        return (distanceAlongWall + seg.offset) % textureWidth;
    }

    /**
     * Calculates the lighting factor based on wall angle (for fake lighting effects).
     */
    public static double calculateWallLightingFactor(Seg seg, Angle lightDirection) {
        Angle wallNormal = wallNormalAngle(seg);
        Angle angleDiff = wallNormal.shortestDistanceTo(lightDirection);
        return Math.max(0.3, Math.cos(angleDiff.radians())); // Minimum 30% lighting
    }
}