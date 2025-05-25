package com.doomviewer.geometry;

/**
 * Utility class for angle operations and conversions.
 * Handles angle normalization, conversion between degrees and radians,
 * and common angle calculations used in Doom rendering.
 */
public class Angle {
    private final double radians;

    private Angle(double radians) {
        this.radians = radians;
    }

    public static Angle radians(double radians) {
        return new Angle(radians);
    }

    public static Angle degrees(double degrees) {
        return new Angle(Math.toRadians(degrees));
    }

    public double radians() {
        return radians;
    }

    public double degrees() {
        return Math.toDegrees(radians);
    }

    public Angle normalize() {
        return new Angle(normalizeRadians(radians));
    }

    public Angle normalizeTo360() {
        double deg = degrees();
        deg = ((deg % 360.0) + 360.0) % 360.0;
        return degrees(deg);
    }

    public Angle add(Angle other) {
        return new Angle(radians + other.radians);
    }

    public Angle subtract(Angle other) {
        return new Angle(radians - other.radians);
    }

    public Angle multiply(double scalar) {
        return new Angle(radians * scalar);
    }

    public Angle negate() {
        return new Angle(-radians);
    }

    public double sin() {
        return Math.sin(radians);
    }

    public double cos() {
        return Math.cos(radians);
    }

    public double tan() {
        return Math.tan(radians);
    }

    public Angle abs() {
        return new Angle(Math.abs(radians));
    }

    /**
     * Returns the shortest angular distance between this angle and another.
     * Result is always in range [-π, π].
     */
    public Angle shortestDistanceTo(Angle other) {
        double diff = other.radians - this.radians;
        return new Angle(normalizeRadians(diff));
    }

    /**
     * Linearly interpolates between this angle and another.
     * Uses the shortest path between angles.
     */
    public Angle lerp(Angle other, double t) {
        Angle diff = shortestDistanceTo(other);
        return new Angle(radians + t * diff.radians);
    }

    /**
     * Checks if this angle is approximately equal to another within tolerance.
     */
    public boolean isApproximatelyEqual(Angle other, double toleranceRadians) {
        return Math.abs(shortestDistanceTo(other).radians) <= toleranceRadians;
    }

    /**
     * Normalizes an angle in radians to the range [-π, π].
     */
    public static double normalizeRadians(double radians) {
        while (radians > Math.PI) {
            radians -= 2 * Math.PI;
        }
        while (radians <= -Math.PI) {
            radians += 2 * Math.PI;
        }
        return radians;
    }

    /**
     * Normalizes an angle in degrees to the range [-180, 180].
     */
    public static double normalizeDegrees(double degrees) {
        while (degrees > 180.0) {
            degrees -= 360.0;
        }
        while (degrees <= -180.0) {
            degrees += 360.0;
        }
        return degrees;
    }

    /**
     * Normalizes an angle in degrees to the range [0, 360).
     */
    public static double normalizeDegreesPositive(double degrees) {
        return ((degrees % 360.0) + 360.0) % 360.0;
    }

    /**
     * Converts from Doom's angle format (if needed) to standard mathematical angles.
     */
    public static Angle fromDoomAngle(int doomAngle) {
        // Doom uses a different angle system, this can be adjusted based on the actual format
        return degrees(doomAngle * 360.0 / 65536.0);
    }

    /**
     * Converts to Doom's angle format (if needed).
     */
    public int toDoomAngle() {
        return (int) (normalizeTo360().degrees() * 65536.0 / 360.0);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Angle angle = (Angle) obj;
        return Double.compare(angle.radians, radians) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(radians);
    }

    @Override
    public String toString() {
        return String.format("Angle(%.3f°)", degrees());
    }
}