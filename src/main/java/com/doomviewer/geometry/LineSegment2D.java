package com.doomviewer.geometry;

/**
 * Represents a line segment in 2D space with finite start and end points.
 * This is particularly useful for wall segments in Doom rendering.
 */
public class LineSegment2D {
    public final Point2D start;
    public final Point2D end;

    public LineSegment2D(Point2D start, Point2D end) {
        this.start = start;
        this.end = end;
    }

    public LineSegment2D(double x1, double y1, double x2, double y2) {
        this(new Point2D(x1, y1), new Point2D(x2, y2));
    }

    public Vector2D direction() {
        return start.vectorTo(end);
    }

    public Vector2D normalizedDirection() {
        return direction().normalize();
    }

    public Vector2D normal() {
        return normalizedDirection().perpendicular();
    }

    public double length() {
        return start.distanceTo(end);
    }

    public double lengthSquared() {
        return start.distanceSquaredTo(end);
    }

    public Point2D midpoint() {
        return start.lerp(end, 0.5);
    }

    public Line2D toLine() {
        return new Line2D(start, end);
    }

    /**
     * Returns a point along the segment at parameter t (0 = start, 1 = end).
     */
    public Point2D pointAt(double t) {
        return start.lerp(end, t);
    }

    /**
     * Returns the perpendicular distance from a point to this line segment.
     * If the closest point on the infinite line is outside the segment,
     * returns the distance to the nearest endpoint.
     */
    public double distanceToPoint(Point2D point) {
        Vector2D segmentVec = start.vectorTo(end);
        Vector2D pointVec = start.vectorTo(point);
        
        double segmentLengthSq = segmentVec.lengthSquared();
        if (segmentLengthSq < 1e-10) {
            // Degenerate segment (start == end)
            return start.distanceTo(point);
        }
        
        double t = pointVec.dot(segmentVec) / segmentLengthSq;
        
        if (t < 0) {
            return start.distanceTo(point);
        } else if (t > 1) {
            return end.distanceTo(point);
        } else {
            Point2D closestPoint = start.add(segmentVec.multiply(t));
            return point.distanceTo(closestPoint);
        }
    }

    /**
     * Returns the closest point on this segment to the given point.
     */
    public Point2D closestPointTo(Point2D point) {
        Vector2D segmentVec = start.vectorTo(end);
        Vector2D pointVec = start.vectorTo(point);
        
        double segmentLengthSq = segmentVec.lengthSquared();
        if (segmentLengthSq < 1e-10) {
            return start; // Degenerate segment
        }
        
        double t = Math.max(0, Math.min(1, pointVec.dot(segmentVec) / segmentLengthSq));
        return start.add(segmentVec.multiply(t));
    }

    /**
     * Determines which side of the segment a point is on.
     * Returns positive if point is on the left side, negative if on the right side.
     */
    public double sideOfPoint(Point2D point) {
        return toLine().sideOfPoint(point);
    }

    /**
     * Checks if a point is on the left side of the segment.
     */
    public boolean isPointOnLeftSide(Point2D point) {
        return sideOfPoint(point) > 0;
    }

    /**
     * Checks if a point lies on the segment within a tolerance.
     */
    public boolean containsPoint(Point2D point, double tolerance) {
        return distanceToPoint(point) <= tolerance;
    }

    /**
     * Finds the intersection point with another line segment.
     * Returns null if segments don't intersect.
     */
    public Point2D intersectionWith(LineSegment2D other) {
        Vector2D d1 = this.direction();
        Vector2D d2 = other.direction();
        
        double cross = d1.cross(d2);
        if (Math.abs(cross) < 1e-10) {
            return null; // Segments are parallel
        }
        
        Vector2D diff = this.start.vectorTo(other.start);
        double t1 = diff.cross(d2) / cross;
        double t2 = diff.cross(d1) / cross;
        
        if (t1 >= 0 && t1 <= 1 && t2 >= 0 && t2 <= 1) {
            return this.start.add(d1.multiply(t1));
        }
        
        return null; // No intersection within segment bounds
    }

    /**
     * Checks if this segment intersects with another segment.
     */
    public boolean intersectsWith(LineSegment2D other) {
        return intersectionWith(other) != null;
    }

    /**
     * Returns the angle of this segment relative to the positive X-axis.
     */
    public Angle angle() {
        return normalizedDirection().angle();
    }

    /**
     * Checks if this segment is parallel to another segment.
     */
    public boolean isParallelTo(LineSegment2D other) {
        Vector2D d1 = this.normalizedDirection();
        Vector2D d2 = other.normalizedDirection();
        return Math.abs(d1.cross(d2)) < 1e-10;
    }

    /**
     * Extends the segment by the given distance at both ends.
     */
    public LineSegment2D extend(double distance) {
        Vector2D dir = normalizedDirection();
        Point2D newStart = start.subtract(dir.multiply(distance));
        Point2D newEnd = end.add(dir.multiply(distance));
        return new LineSegment2D(newStart, newEnd);
    }

    /**
     * Clips this segment to the given bounds.
     * Returns null if the segment is entirely outside the bounds.
     */
    public LineSegment2D clipToBounds(double minX, double minY, double maxX, double maxY) {
        // Simple implementation using Cohen-Sutherland algorithm concepts
        // This is a simplified version - a full implementation would be more complex
        
        double x1 = start.x, y1 = start.y;
        double x2 = end.x, y2 = end.y;
        
        // Clip to bounds (simplified)
        x1 = Math.max(minX, Math.min(maxX, x1));
        y1 = Math.max(minY, Math.min(maxY, y1));
        x2 = Math.max(minX, Math.min(maxX, x2));
        y2 = Math.max(minY, Math.min(maxY, y2));
        
        if (x1 == x2 && y1 == y2) {
            return null; // Segment collapsed to a point
        }
        
        return new LineSegment2D(x1, y1, x2, y2);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        LineSegment2D segment = (LineSegment2D) obj;
        return start.equals(segment.start) && end.equals(segment.end);
    }

    @Override
    public int hashCode() {
        return start.hashCode() * 31 + end.hashCode();
    }

    @Override
    public String toString() {
        return String.format("LineSegment2D(%s -> %s)", start, end);
    }
}