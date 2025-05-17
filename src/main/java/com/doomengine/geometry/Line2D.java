package com.doomengine.geometry;

/**
 * Represents an infinite line in 2D space defined by two points.
 */
public class Line2D {
    public final Point2D point1;
    public final Point2D point2;

    public Line2D(Point2D point1, Point2D point2) {
        if (point1.equals(point2)) {
            throw new IllegalArgumentException("Line cannot be defined by two identical points");
        }
        this.point1 = point1;
        this.point2 = point2;
    }

    public Line2D(double x1, double y1, double x2, double y2) {
        this(new Point2D(x1, y1), new Point2D(x2, y2));
    }

    public Vector2D direction() {
        return point1.vectorTo(point2).normalize();
    }

    public Vector2D normal() {
        return direction().perpendicular();
    }

    public double slope() {
        double dx = point2.x() - point1.x();
        if (Math.abs(dx) < 1e-10) {
            return Double.POSITIVE_INFINITY; // Vertical line
        }
        return (point2.y() - point1.y()) / dx;
    }

    public double yIntercept() {
        double slope = slope();
        if (Double.isInfinite(slope)) {
            return Double.NaN; // Vertical line has no y-intercept
        }
        return point1.y() - slope * point1.x();
    }

    /**
     * Returns the perpendicular distance from a point to this line.
     */
    public double distanceToPoint(Point2D point) {
        Vector2D lineVec = point1.vectorTo(point2);
        Vector2D pointVec = point1.vectorTo(point);
        return Math.abs(lineVec.cross(pointVec)) / lineVec.length();
    }

    /**
     * Returns the closest point on this line to the given point.
     */
    public Point2D closestPointTo(Point2D point) {
        Vector2D lineVec = point1.vectorTo(point2);
        Vector2D pointVec = point1.vectorTo(point);
        double t = pointVec.dot(lineVec) / lineVec.lengthSquared();
        return point1.add(lineVec.multiply(t));
    }

    /**
     * Determines which side of the line a point is on.
     * Returns positive if point is on the left side (when looking from point1 to point2),
     * negative if on the right side, and zero if on the line.
     */
    public double sideOfPoint(Point2D point) {
        Vector2D lineVec = point1.vectorTo(point2);
        Vector2D pointVec = point1.vectorTo(point);
        return lineVec.cross(pointVec);
    }

    /**
     * Checks if a point is on the left side of the line.
     */
    public boolean isPointOnLeftSide(Point2D point) {
        return sideOfPoint(point) > 0;
    }

    /**
     * Checks if a point is on the right side of the line.
     */
    public boolean isPointOnRightSide(Point2D point) {
        return sideOfPoint(point) < 0;
    }

    /**
     * Checks if a point lies on the line within a tolerance.
     */
    public boolean containsPoint(Point2D point, double tolerance) {
        return Math.abs(sideOfPoint(point)) <= tolerance * point1.vectorTo(point2).length();
    }

    /**
     * Finds the intersection point with another line.
     * Returns null if lines are parallel.
     */
    public Point2D intersectionWith(Line2D other) {
        Vector2D d1 = this.point1.vectorTo(this.point2);
        Vector2D d2 = other.point1.vectorTo(other.point2);
        
        double cross = d1.cross(d2);
        if (Math.abs(cross) < 1e-10) {
            return null; // Lines are parallel
        }
        
        Vector2D diff = this.point1.vectorTo(other.point1);
        double t = diff.cross(d2) / cross;
        
        return this.point1.add(d1.multiply(t));
    }

    /**
     * Checks if this line is parallel to another line.
     */
    public boolean isParallelTo(Line2D other) {
        Vector2D d1 = this.direction();
        Vector2D d2 = other.direction();
        return Math.abs(d1.cross(d2)) < 1e-10;
    }

    /**
     * Checks if this line is perpendicular to another line.
     */
    public boolean isPerpendicularTo(Line2D other) {
        Vector2D d1 = this.direction();
        Vector2D d2 = other.direction();
        return Math.abs(d1.dot(d2)) < 1e-10;
    }

    /**
     * Returns the angle of this line relative to the positive X-axis.
     */
    public Angle angle() {
        return direction().angle();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Line2D line2D = (Line2D) obj;
        return (point1.equals(line2D.point1) && point2.equals(line2D.point2)) ||
               (point1.equals(line2D.point2) && point2.equals(line2D.point1));
    }

    @Override
    public int hashCode() {
        // Order-independent hash
        return point1.hashCode() + point2.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Line2D(%s -> %s)", point1, point2);
    }
}