package com.doomviewer.misc.math;

public class Vector2D {
    public double x;
    public double y;

    public Vector2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Vector2D add(Vector2D other) {
        return new Vector2D(this.x + other.x, this.y + other.y);
    }

    public Vector2D subtract(Vector2D other) {
        return new Vector2D(this.x - other.x, this.y - other.y);
    }

    public Vector2D scale(double scalar) {
        return new Vector2D(this.x * scalar, this.y * scalar);
    }

    public void translate(Vector2D delta) {
        this.x += delta.x;
        this.y += delta.y;
    }

    // In-place rotation (like Pygame's rotate_ip)
    public void rotateIp(double angleDegrees) {
        double angleRad = Math.toRadians(angleDegrees);
        double cosA = Math.cos(angleRad);
        double sinA = Math.sin(angleRad);
        double newX = this.x * cosA - this.y * sinA;
        double newY = this.x * sinA + this.y * cosA;
        this.x = newX;
        this.y = newY;
    }

    public static double distance(Vector2D v1, Vector2D v2) {
        double dx = v1.x - v2.x;
        double dy = v1.y - v2.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public String toString() {
        return "Vector2D(" + x + ", " + y + ")";
    }
}