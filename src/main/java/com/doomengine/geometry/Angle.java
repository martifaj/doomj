package com.doomengine.geometry;

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

    public Angle shortestDistanceTo(Angle other) {
        double diff = other.radians - this.radians;
        return new Angle(normalizeRadians(diff));
    }

    public Angle lerp(Angle other, double t) {
        Angle diff = shortestDistanceTo(other);
        return new Angle(radians + t * diff.radians);
    }

    public boolean isApproximatelyEqual(Angle other, double toleranceRadians) {
        return Math.abs(shortestDistanceTo(other).radians) <= toleranceRadians;
    }

    public static double normalizeRadians(double radians) {
        while (radians > Math.PI) {
            radians -= 2 * Math.PI;
        }
        while (radians <= -Math.PI) {
            radians += 2 * Math.PI;
        }
        return radians;
    }

    public static double normalizeDegrees(double degrees) {
        while (degrees > 180.0) {
            degrees -= 360.0;
        }
        while (degrees <= -180.0) {
            degrees += 360.0;
        }
        return degrees;
    }

    public static double normalizeDegreesPositive(double degrees) {
        return ((degrees % 360.0) + 360.0) % 360.0;
    }

    public static Angle fromDoomAngle(int doomAngle) {
        return degrees(doomAngle * 360.0 / 65536.0);
    }

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