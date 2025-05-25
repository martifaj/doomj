package com.doomviewer.geometry;

/**
 * Enhanced 2D vector class with comprehensive geometric operations.
 * Replaces and extends the original Vector2D from misc.math package.
 */
public class Vector2D {
    public final double x;
    public final double y;

    public static final Vector2D ZERO = new Vector2D(0, 0);
    public static final Vector2D UNIT_X = new Vector2D(1, 0);
    public static final Vector2D UNIT_Y = new Vector2D(0, 1);

    public Vector2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Vector2D add(Vector2D other) {
        return new Vector2D(x + other.x, y + other.y);
    }

    public Vector2D subtract(Vector2D other) {
        return new Vector2D(x - other.x, y - other.y);
    }

    public Vector2D multiply(double scalar) {
        return new Vector2D(x * scalar, y * scalar);
    }

    public Vector2D divide(double scalar) {
        if (Math.abs(scalar) < 1e-10) {
            throw new IllegalArgumentException("Division by zero or near-zero scalar");
        }
        return new Vector2D(x / scalar, y / scalar);
    }

    public Vector2D negate() {
        return new Vector2D(-x, -y);
    }

    public double dot(Vector2D other) {
        return x * other.x + y * other.y;
    }

    public double cross(Vector2D other) {
        return x * other.y - y * other.x;
    }

    public double length() {
        return Math.sqrt(x * x + y * y);
    }

    public double lengthSquared() {
        return x * x + y * y;
    }

    public Vector2D normalize() {
        double len = length();
        if (len < 1e-10) {
            return ZERO;
        }
        return new Vector2D(x / len, y / len);
    }

    public Vector2D rotate(Angle angle) {
        double cos = Math.cos(angle.radians());
        double sin = Math.sin(angle.radians());
        return new Vector2D(x * cos - y * sin, x * sin + y * cos);
    }

    public Vector2D rotate(double angleDegrees) {
        return rotate(Angle.degrees(angleDegrees));
    }

    public Vector2D perpendicular() {
        return new Vector2D(-y, x);
    }

    public Vector2D perpendicularCCW() {
        return new Vector2D(-y, x);
    }

    public Vector2D perpendicularCW() {
        return new Vector2D(y, -x);
    }

    public Angle angle() {
        return Angle.radians(Math.atan2(y, x));
    }

    public Angle angleTo(Vector2D other) {
        double cross = this.cross(other);
        double dot = this.dot(other);
        return Angle.radians(Math.atan2(cross, dot));
    }

    public Vector2D project(Vector2D onto) {
        double dotProduct = this.dot(onto);
        double ontoLengthSquared = onto.lengthSquared();
        if (ontoLengthSquared < 1e-10) {
            return ZERO;
        }
        return onto.multiply(dotProduct / ontoLengthSquared);
    }

    public Vector2D reflect(Vector2D normal) {
        Vector2D normalizedNormal = normal.normalize();
        return this.subtract(normalizedNormal.multiply(2 * this.dot(normalizedNormal)));
    }

    public Vector2D lerp(Vector2D other, double t) {
        return new Vector2D(
            x + t * (other.x - x),
            y + t * (other.y - y)
        );
    }

    public boolean isZero() {
        return Math.abs(x) < 1e-10 && Math.abs(y) < 1e-10;
    }

    public boolean isUnit() {
        return Math.abs(lengthSquared() - 1.0) < 1e-10;
    }

    public static Vector2D fromAngle(Angle angle) {
        return new Vector2D(Math.cos(angle.radians()), Math.sin(angle.radians()));
    }

    public static Vector2D fromAngle(double angleDegrees) {
        return fromAngle(Angle.degrees(angleDegrees));
    }

    public static double distance(Vector2D v1, Vector2D v2) {
        double dx = v1.x - v2.x;
        double dy = v1.y - v2.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Vector2D vector2D = (Vector2D) obj;
        return Double.compare(vector2D.x, x) == 0 && Double.compare(vector2D.y, y) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(x) * 31 + Double.hashCode(y);
    }

    @Override
    public String toString() {
        return String.format("Vector2D(%.3f, %.3f)", x, y);
    }
}