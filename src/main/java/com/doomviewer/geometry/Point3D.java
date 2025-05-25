package com.doomviewer.geometry;

/**
 * Represents a point in 3D space with double precision coordinates.
 */
public class Point3D {
    public final double x;
    public final double y;
    public final double z;

    public Point3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Point3D add(Vector3D vector) {
        return new Point3D(x + vector.x, y + vector.y, z + vector.z);
    }

    public Point3D subtract(Vector3D vector) {
        return new Point3D(x - vector.x, y - vector.y, z - vector.z);
    }

    public Vector3D vectorTo(Point3D other) {
        return new Vector3D(other.x - x, other.y - y, other.z - z);
    }

    public double distanceTo(Point3D other) {
        double dx = other.x - x;
        double dy = other.y - y;
        double dz = other.z - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double distanceSquaredTo(Point3D other) {
        double dx = other.x - x;
        double dy = other.y - y;
        double dz = other.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public Point2D toPoint2D() {
        return new Point2D(x, y);
    }

    public Point3D lerp(Point3D other, double t) {
        return new Point3D(
            x + t * (other.x - x),
            y + t * (other.y - y),
            z + t * (other.z - z)
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Point3D point3D = (Point3D) obj;
        return Double.compare(point3D.x, x) == 0 && 
               Double.compare(point3D.y, y) == 0 && 
               Double.compare(point3D.z, z) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(x) * 31 * 31 + Double.hashCode(y) * 31 + Double.hashCode(z);
    }

    @Override
    public String toString() {
        return String.format("Point3D(%.3f, %.3f, %.3f)", x, y, z);
    }
}