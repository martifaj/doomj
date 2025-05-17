package com.doomengine.geometry;

import com.doomengine.wad.datatypes.Seg;
import com.doomengine.wad.datatypes.Linedef;
import com.doomengine.wad.datatypes.Node;

public class DoomGeometryUtils {

    public static LineSegment2D segToLineSegment(Seg seg) {
        Point2D start = new Point2D(seg.startVertex.x(), seg.startVertex.y());
        Point2D end = new Point2D(seg.endVertex.x(), seg.endVertex.y());
        return new LineSegment2D(start, end);
    }

    public static LineSegment2D linedefToLineSegment(Linedef linedef) {
        throw new UnsupportedOperationException("linedefToLineSegment requires vertex lookup - use segToLineSegment instead");
    }

    public static Line2D nodeToPartitionLine(Node node) {
        Point2D start = new Point2D(node.xPartition, node.yPartition);
        Vector2D direction = new Vector2D(node.dxPartition, node.dyPartition);
        Point2D end = start.add(direction);
        return new Line2D(start, end);
    }

    public static double pointSideOfPartition(Point2D point, Node node) {
        Line2D partitionLine = nodeToPartitionLine(node);
        return partitionLine.sideOfPoint(point);
    }

    public static boolean isPointOnFrontSide(Point2D point, Node node) {
        return pointSideOfPartition(point, node) >= 0;
    }

    public static double distanceToWall(Point2D point, Seg seg) {
        LineSegment2D segment = segToLineSegment(seg);
        return segment.distanceToPoint(point);
    }

    public static boolean isPointOnFrontSideOfWall(Point2D point, Seg seg) {
        LineSegment2D segment = segToLineSegment(seg);
        return segment.isPointOnLeftSide(point);
    }

    public static Angle wallAngle(Seg seg) {
        LineSegment2D segment = segToLineSegment(seg);
        return segment.angle();
    }

    public static Angle wallNormalAngle(Seg seg) {
        return wallAngle(seg).add(Angle.degrees(90));
    }

    public static Point2D projectWorldToScreen(Point3D worldPoint, Point2D playerPos, 
                                              Angle playerAngle, Projection projection) {
        Point3D cameraPos3D = new Point3D(playerPos.x(), playerPos.y(), 0);
        Point3D cameraSpace = projection.worldPointToCameraSpace(worldPoint, cameraPos3D, playerAngle);
        
        return projection.projectToScreen(cameraSpace);
    }

    public static Angle calculateFOV(double screenWidth, double screenDistance) {
        double halfFOVRad = Math.atan2(screenWidth / 2.0, screenDistance);
        return Angle.radians(2 * halfFOVRad);
    }

    public static boolean isWallPotentiallyVisible(Seg seg, Point2D viewPoint, Angle viewAngle, Angle fovAngle) {
        LineSegment2D segment = segToLineSegment(seg);
        
        if (!segment.isPointOnLeftSide(viewPoint)) {
            return false;
        }
        
        Vector2D toStart = viewPoint.vectorTo(segment.start);
        Vector2D toEnd = viewPoint.vectorTo(segment.end);
        
        Angle angleToStart = toStart.angle();
        Angle angleToEnd = toEnd.angle();
        
        Angle halfFOV = fovAngle.multiply(0.5);
        Angle minViewAngle = viewAngle.subtract(halfFOV);
        Angle maxViewAngle = viewAngle.add(halfFOV);
        
        return isAngleInRange(angleToStart, minViewAngle, maxViewAngle) ||
               isAngleInRange(angleToEnd, minViewAngle, maxViewAngle);
    }

    private static boolean isAngleInRange(Angle angle, Angle minAngle, Angle maxAngle) {
        double a = angle.normalizeTo360().degrees();
        double min = minAngle.normalizeTo360().degrees();
        double max = maxAngle.normalizeTo360().degrees();
        
        if (min <= max) {
            return a >= min && a <= max;
        } else {
            return a >= min || a <= max;
        }
    }

    public static Point2D wallIntersection(Seg seg1, Seg seg2) {
        LineSegment2D segment1 = segToLineSegment(seg1);
        LineSegment2D segment2 = segToLineSegment(seg2);
        return segment1.intersectionWith(segment2);
    }

    public static boolean wallsIntersect(Seg seg1, Seg seg2) {
        return wallIntersection(seg1, seg2) != null;
    }

    public static Point2D closestPointOnWall(Point2D point, Seg seg) {
        LineSegment2D segment = segToLineSegment(seg);
        return segment.closestPointTo(point);
    }

    public static GeometryUtils.BoundingBox wallsBoundingBox(Seg... segs) {
        if (segs.length == 0) {
            throw new IllegalArgumentException("Cannot create bounding box for empty wall set");
        }
        
        Point2D[] points = new Point2D[segs.length * 2];
        for (int i = 0; i < segs.length; i++) {
            points[i * 2] = new Point2D(segs[i].startVertex.x(), segs[i].startVertex.y());
            points[i * 2 + 1] = new Point2D(segs[i].endVertex.x(), segs[i].endVertex.y());
        }
        
        return GeometryUtils.boundingBox(points);
    }

    public static Point2D worldToPlayerSpace(Point2D worldPoint, Point2D playerPos, Angle playerAngle) {
        Transform2D transform = Transform2D.translation(-playerPos.x(), -playerPos.y())
                                          .then(Transform2D.rotation(playerAngle.negate()));
        return transform.transform(worldPoint);
    }

    public static Point2D playerToWorldSpace(Point2D playerPoint, Point2D playerPos, Angle playerAngle) {
        Transform2D transform = Transform2D.rotation(playerAngle)
                                          .then(Transform2D.translation(playerPos.x(), playerPos.y()));
        return transform.transform(playerPoint);
    }

    public static double calculateTextureU(Point2D point, Seg seg, double textureWidth) {
        LineSegment2D segment = segToLineSegment(seg);
        Point2D closestPoint = segment.closestPointTo(point);
        double distanceAlongWall = segment.start.distanceTo(closestPoint);
        return (distanceAlongWall + seg.offset) % textureWidth;
    }

    public static double calculateWallLightingFactor(Seg seg, Angle lightDirection) {
        Angle wallNormal = wallNormalAngle(seg);
        Angle angleDiff = wallNormal.shortestDistanceTo(lightDirection);
        return Math.max(0.3, Math.cos(angleDiff.radians()));
    }

    public static Vector2D worldToCameraSpace(Vector2D worldVector, Angle playerAngle) {
        double dx = worldVector.x();
        double dy = worldVector.y();
        double camSpaceX = dx * (-playerAngle.sin()) + dy * playerAngle.cos();
        double camSpaceY = dx * playerAngle.cos() + dy * playerAngle.sin();
        return new Vector2D(camSpaceX, camSpaceY);
    }

    public static Vector2D worldPositionToCameraSpace(Point2D worldPos, Point2D playerPos, Angle playerAngle) {
        Vector2D worldVector = playerPos.vectorTo(worldPos);
        return worldToCameraSpace(worldVector, playerAngle);
    }

    public static Transform2D createCameraTransform(Angle playerAngle) {
        double cos = playerAngle.cos();
        double sin = playerAngle.sin();
        return Transform2D.matrix(-sin, cos, 0, cos, sin, 0);
    }
}