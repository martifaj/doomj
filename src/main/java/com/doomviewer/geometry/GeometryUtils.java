package com.doomviewer.geometry;

/**
 * Utility class containing common geometric calculations and helper methods
 * used throughout the Doom engine.
 */
public class GeometryUtils {
    
    private static final double EPSILON = 1e-10;

    /**
     * Calculates the perpendicular distance from a point to a line defined by two points.
     */
    public static double pointToLineDistance(Point2D point, Point2D lineStart, Point2D lineEnd) {
        return new Line2D(lineStart, lineEnd).distanceToPoint(point);
    }

    /**
     * Calculates the perpendicular distance from a point to a line segment.
     */
    public static double pointToSegmentDistance(Point2D point, Point2D segStart, Point2D segEnd) {
        return new LineSegment2D(segStart, segEnd).distanceToPoint(point);
    }

    /**
     * Determines which side of a line a point is on.
     * Returns positive if left side, negative if right side, zero if on line.
     */
    public static double pointSideOfLine(Point2D point, Point2D lineStart, Point2D lineEnd) {
        Vector2D lineVec = lineStart.vectorTo(lineEnd);
        Vector2D pointVec = lineStart.vectorTo(point);
        return lineVec.cross(pointVec);
    }

    /**
     * Checks if a point is on the left side of a line (when looking from start to end).
     */
    public static boolean isPointOnLeftSide(Point2D point, Point2D lineStart, Point2D lineEnd) {
        return pointSideOfLine(point, lineStart, lineEnd) > 0;
    }

    /**
     * Calculates the intersection point of two lines.
     * Returns null if lines are parallel.
     */
    public static Point2D lineIntersection(Point2D line1Start, Point2D line1End, 
                                          Point2D line2Start, Point2D line2End) {
        return new Line2D(line1Start, line1End).intersectionWith(new Line2D(line2Start, line2End));
    }

    /**
     * Calculates the intersection point of two line segments.
     * Returns null if segments don't intersect.
     */
    public static Point2D segmentIntersection(Point2D seg1Start, Point2D seg1End,
                                             Point2D seg2Start, Point2D seg2End) {
        return new LineSegment2D(seg1Start, seg1End).intersectionWith(new LineSegment2D(seg2Start, seg2End));
    }

    /**
     * Checks if two line segments intersect.
     */
    public static boolean segmentsIntersect(Point2D seg1Start, Point2D seg1End,
                                           Point2D seg2Start, Point2D seg2End) {
        return segmentIntersection(seg1Start, seg1End, seg2Start, seg2End) != null;
    }

    /**
     * Calculates the angle between two vectors.
     */
    public static Angle angleBetweenVectors(Vector2D v1, Vector2D v2) {
        double dot = v1.dot(v2);
        double cross = v1.cross(v2);
        return Angle.radians(Math.atan2(cross, dot));
    }

    /**
     * Calculates the angle from one point to another.
     */
    public static Angle angleBetweenPoints(Point2D from, Point2D to) {
        Vector2D direction = from.vectorTo(to);
        return direction.angle();
    }

    /**
     * Rotates a point around another point by the given angle.
     */
    public static Point2D rotatePointAround(Point2D point, Point2D center, Angle angle) {
        return Transform2D.rotationAround(center, angle).transform(point);
    }

    /**
     * Clamps a value between min and max.
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamps an integer value between min and max.
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Linear interpolation between two values.
     */
    public static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    /**
     * Checks if two double values are approximately equal within epsilon.
     */
    public static boolean approximately(double a, double b) {
        return Math.abs(a - b) < EPSILON;
    }

    /**
     * Checks if two double values are approximately equal within the given tolerance.
     */
    public static boolean approximately(double a, double b, double tolerance) {
        return Math.abs(a - b) < tolerance;
    }

    /**
     * Calculates the area of a triangle defined by three points.
     */
    public static double triangleArea(Point2D p1, Point2D p2, Point2D p3) {
        Vector2D v1 = p1.vectorTo(p2);
        Vector2D v2 = p1.vectorTo(p3);
        return Math.abs(v1.cross(v2)) * 0.5;
    }

    /**
     * Checks if a point is inside a triangle.
     */
    public static boolean isPointInTriangle(Point2D point, Point2D t1, Point2D t2, Point2D t3) {
        // Use barycentric coordinates
        Vector2D v0 = t1.vectorTo(t3);
        Vector2D v1 = t1.vectorTo(t2);
        Vector2D v2 = t1.vectorTo(point);

        double dot00 = v0.dot(v0);
        double dot01 = v0.dot(v1);
        double dot02 = v0.dot(v2);
        double dot11 = v1.dot(v1);
        double dot12 = v1.dot(v2);

        double invDenom = 1 / (dot00 * dot11 - dot01 * dot01);
        double u = (dot11 * dot02 - dot01 * dot12) * invDenom;
        double v = (dot00 * dot12 - dot01 * dot02) * invDenom;

        return (u >= 0) && (v >= 0) && (u + v <= 1);
    }

    /**
     * Calculates the centroid of a polygon defined by points.
     */
    public static Point2D polygonCentroid(Point2D... points) {
        if (points.length == 0) {
            throw new IllegalArgumentException("Cannot calculate centroid of empty polygon");
        }

        double sumX = 0, sumY = 0;
        for (Point2D point : points) {
            sumX += point.x;
            sumY += point.y;
        }

        return new Point2D(sumX / points.length, sumY / points.length);
    }

    /**
     * Calculates the signed area of a polygon (positive if counterclockwise).
     */
    public static double polygonSignedArea(Point2D... points) {
        if (points.length < 3) {
            return 0;
        }

        double area = 0;
        for (int i = 0; i < points.length; i++) {
            int j = (i + 1) % points.length;
            area += points[i].x * points[j].y - points[j].x * points[i].y;
        }

        return area * 0.5;
    }

    /**
     * Checks if a polygon is oriented counterclockwise.
     */
    public static boolean isPolygonCounterClockwise(Point2D... points) {
        return polygonSignedArea(points) > 0;
    }

    /**
     * Calculates the bounding box of a set of points.
     */
    public static BoundingBox boundingBox(Point2D... points) {
        if (points.length == 0) {
            throw new IllegalArgumentException("Cannot calculate bounding box of empty point set");
        }

        double minX = points[0].x, maxX = points[0].x;
        double minY = points[0].y, maxY = points[0].y;

        for (int i = 1; i < points.length; i++) {
            minX = Math.min(minX, points[i].x);
            maxX = Math.max(maxX, points[i].x);
            minY = Math.min(minY, points[i].y);
            maxY = Math.max(maxY, points[i].y);
        }

        return new BoundingBox(minX, minY, maxX, maxY);
    }

    /**
     * Simple bounding box class.
     */
    public static class BoundingBox {
        public final double minX, minY, maxX, maxY;

        public BoundingBox(double minX, double minY, double maxX, double maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        public double width() { return maxX - minX; }
        public double height() { return maxY - minY; }
        public Point2D center() { return new Point2D((minX + maxX) * 0.5, (minY + maxY) * 0.5); }

        public boolean contains(Point2D point) {
            return point.x >= minX && point.x <= maxX && point.y >= minY && point.y <= maxY;
        }

        public boolean intersects(BoundingBox other) {
            return !(other.maxX < minX || other.minX > maxX || other.maxY < minY || other.minY > maxY);
        }

        @Override
        public String toString() {
            return String.format("BoundingBox(%.3f,%.3f -> %.3f,%.3f)", minX, minY, maxX, maxY);
        }
    }
}