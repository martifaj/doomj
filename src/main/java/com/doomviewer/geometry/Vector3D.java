package com.doomviewer.geometry;

/**
 * 3D vector class with comprehensive geometric operations.
 */
public class Vector3D {
    public final double x;
    public final double y;
    public final double z;

    public static final Vector3D ZERO = new Vector3D(0, 0, 0);
    public static final Vector3D UNIT_X = new Vector3D(1, 0, 0);
    public static final Vector3D UNIT_Y = new Vector3D(0, 1, 0);
    public static final Vector3D UNIT_Z = new Vector3D(0, 0, 1);

    public Vector3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3D add(Vector3D other) {
        return new Vector3D(x + other.x, y + other.y, z + other.z);
    }

    public Vector3D subtract(Vector3D other) {
        return new Vector3D(x - other.x, y - other.y, z - other.z);
    }

    public Vector3D multiply(double scalar) {
        return new Vector3D(x * scalar, y * scalar, z * scalar);
    }

    public Vector3D divide(double scalar) {
        if (Math.abs(scalar) < 1e-10) {
            throw new IllegalArgumentException("Division by zero or near-zero scalar");
        }
        return new Vector3D(x / scalar, y / scalar, z / scalar);
    }

    public Vector3D negate() {
        return new Vector3D(-x, -y, -z);
    }

    public double dot(Vector3D other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vector3D cross(Vector3D other) {
        return new Vector3D(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        );
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public Vector3D normalize() {
        double len = length();
        if (len < 1e-10) {
            return ZERO;
        }
        return new Vector3D(x / len, y / len, z / len);
    }

    public Vector2D toVector2D() {
        return new Vector2D(x, y);
    }

    public Vector3D project(Vector3D onto) {
        double dotProduct = this.dot(onto);
        double ontoLengthSquared = onto.lengthSquared();
        if (ontoLengthSquared < 1e-10) {
            return ZERO;
        }
        return onto.multiply(dotProduct / ontoLengthSquared);
    }

    public Vector3D reflect(Vector3D normal) {
        Vector3D normalizedNormal = normal.normalize();
        return this.subtract(normalizedNormal.multiply(2 * this.dot(normalizedNormal)));
    }

    public Vector3D lerp(Vector3D other, double t) {
        return new Vector3D(
            x + t * (other.x - x),
            y + t * (other.y - y),
            z + t * (other.z - z)
        );
    }

    public boolean isZero() {
        return Math.abs(x) < 1e-10 && Math.abs(y) < 1e-10 && Math.abs(z) < 1e-10;
    }

    public boolean isUnit() {
        return Math.abs(lengthSquared() - 1.0) < 1e-10;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Vector3D vector3D = (Vector3D) obj;
        return Double.compare(vector3D.x, x) == 0 && 
               Double.compare(vector3D.y, y) == 0 && 
               Double.compare(vector3D.z, z) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(x) * 31 * 31 + Double.hashCode(y) * 31 + Double.hashCode(z);
    }

    @Override
    public String toString() {
        return String.format("Vector3D(%.3f, %.3f, %.3f)", x, y, z);
    }
}