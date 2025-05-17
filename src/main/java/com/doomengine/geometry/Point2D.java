package com.doomengine.geometry;

/**
 * Represents a point in 2D space with double precision coordinates.
 */
public record Point2D(double x, double y) {

    public Point2D add(Vector2D vector) {
        return new Point2D(x + vector.x(), y + vector.y());
    }

    public Point2D subtract(Vector2D vector) {
        return new Point2D(x - vector.x(), y - vector.y());
    }

    public Vector2D vectorTo(Point2D other) {
        return new Vector2D(other.x() - x, other.y() - y);
    }

    public double distanceTo(Point2D other) {
        double dx = other.x() - x;
        double dy = other.y() - y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public double distanceSquaredTo(Point2D other) {
        double dx = other.x() - x;
        double dy = other.y() - y;
        return dx * dx + dy * dy;
    }

    public Point2D lerp(Point2D other, double t) {
        return new Point2D(
                x + t * (other.x() - x),
                y + t * (other.y() - y)
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Point2D point2D = (Point2D) obj;
        return Double.compare(point2D.x(), x) == 0 && Double.compare(point2D.y(), y) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(x) * 31 + Double.hashCode(y);
    }

    @Override
    public String toString() {
        return String.format("Point2D(%.3f, %.3f)", x, y);
    }
}