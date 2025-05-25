package com.doomviewer.geometry;

/**
 * Utility class for 3D to 2D projection operations used in Doom rendering.
 * Handles perspective projection, screen space conversions, and related calculations.
 */
public class Projection {
    private final double screenDistance;
    private final int screenWidth;
    private final int screenHeight;
    private final int halfWidth;
    private final int halfHeight;

    public Projection(double screenDistance, int screenWidth, int screenHeight) {
        this.screenDistance = screenDistance;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.halfWidth = screenWidth / 2;
        this.halfHeight = screenHeight / 2;
    }

    /**
     * Projects a 3D point to screen coordinates.
     * Assumes the camera is at origin looking down the positive Y axis.
     */
    public Point2D projectToScreen(Point3D worldPoint) {
        if (Math.abs(worldPoint.y) < 1e-10) {
            // Point is at camera position, return center of screen
            return new Point2D(halfWidth, halfHeight);
        }

        double scale = screenDistance / worldPoint.y;
        double screenX = halfWidth + worldPoint.x * scale;
        double screenY = halfHeight - worldPoint.z * scale; // Negative because screen Y increases downward

        return new Point2D(screenX, screenY);
    }

    /**
     * Projects a 3D point relative to a camera position and orientation.
     */
    public Point2D projectToScreen(Point3D worldPoint, Point3D cameraPos, Angle cameraAngle) {
        // Transform world point to camera space
        Point3D relativePoint = worldPointToCameraSpace(worldPoint, cameraPos, cameraAngle);
        return projectToScreen(relativePoint);
    }

    /**
     * Transforms a world point to camera space coordinates.
     */
    public Point3D worldPointToCameraSpace(Point3D worldPoint, Point3D cameraPos, Angle cameraAngle) {
        // Translate to camera origin
        Vector3D translated = cameraPos.vectorTo(worldPoint);
        
        // Rotate by negative camera angle (to align camera with positive Y axis)
        double cos = cameraAngle.negate().cos();
        double sin = cameraAngle.negate().sin();
        
        return new Point3D(
            translated.x * cos - translated.y * sin,
            translated.x * sin + translated.y * cos,
            translated.z
        );
    }

    /**
     * Calculates the scale factor for a point at the given distance.
     */
    public double scaleAtDistance(double distance) {
        if (Math.abs(distance) < 1e-10) {
            return Double.POSITIVE_INFINITY;
        }
        return screenDistance / distance;
    }

    /**
     * Calculates the distance from scale factor.
     */
    public double distanceFromScale(double scale) {
        if (Math.abs(scale) < 1e-10) {
            return Double.POSITIVE_INFINITY;
        }
        return screenDistance / scale;
    }

    /**
     * Converts a world height to screen height at the given distance.
     */
    public double worldHeightToScreenHeight(double worldHeight, double distance) {
        return worldHeight * scaleAtDistance(distance);
    }

    /**
     * Converts a screen height to world height at the given distance.
     */
    public double screenHeightToWorldHeight(double screenHeight, double distance) {
        return screenHeight / scaleAtDistance(distance);
    }

    /**
     * Calculates the angle from the screen center to a screen column.
     */
    public Angle angleToScreenColumn(int screenX) {
        double offset = screenX - halfWidth;
        return Angle.radians(Math.atan2(offset, screenDistance));
    }

    /**
     * Calculates the screen column for a given angle from center.
     */
    public int screenColumnFromAngle(Angle angle) {
        double offset = screenDistance * angle.tan();
        return (int) Math.round(halfWidth + offset);
    }

    /**
     * Checks if a screen coordinate is within the screen bounds.
     */
    public boolean isOnScreen(Point2D screenPoint) {
        return screenPoint.x >= 0 && screenPoint.x < screenWidth &&
               screenPoint.y >= 0 && screenPoint.y < screenHeight;
    }

    /**
     * Clips a screen X coordinate to screen bounds.
     */
    public int clipScreenX(double screenX) {
        return Math.max(0, Math.min(screenWidth - 1, (int) Math.round(screenX)));
    }

    /**
     * Clips a screen Y coordinate to screen bounds.
     */
    public int clipScreenY(double screenY) {
        return Math.max(0, Math.min(screenHeight - 1, (int) Math.round(screenY)));
    }

    /**
     * Projects a line segment to screen space and returns the screen X coordinates.
     * Returns null if the segment is behind the camera or not visible.
     */
    public ScreenSegment projectSegmentToScreen(LineSegment2D worldSegment, double floorHeight, double ceilHeight, 
                                               Point2D cameraPos, Angle cameraAngle) {
        // Transform segment endpoints to camera space
        Point3D start3D = new Point3D(worldSegment.start.x, worldSegment.start.y, 0);
        Point3D end3D = new Point3D(worldSegment.end.x, worldSegment.end.y, 0);
        
        Point3D cameraPos3D = new Point3D(cameraPos.x, cameraPos.y, 0);
        Point3D startCam = worldPointToCameraSpace(start3D, cameraPos3D, cameraAngle);
        Point3D endCam = worldPointToCameraSpace(end3D, cameraPos3D, cameraAngle);
        
        // Check if segment is behind camera
        if (startCam.y <= 0 && endCam.y <= 0) {
            return null; // Entirely behind camera
        }
        
        // Clip segment to near plane if needed
        if (startCam.y <= 0 || endCam.y <= 0) {
            // One point is behind camera, need to clip
            double nearPlane = 0.1; // Small positive value
            if (startCam.y <= 0) {
                double t = (nearPlane - startCam.y) / (endCam.y - startCam.y);
                startCam = new Point3D(
                    startCam.x + t * (endCam.x - startCam.x),
                    nearPlane,
                    startCam.z + t * (endCam.z - startCam.z)
                );
            }
            if (endCam.y <= 0) {
                double t = (nearPlane - endCam.y) / (startCam.y - endCam.y);
                endCam = new Point3D(
                    endCam.x + t * (startCam.x - endCam.x),
                    nearPlane,
                    endCam.z + t * (startCam.z - endCam.z)
                );
            }
        }
        
        // Project to screen
        Point2D startScreen = projectToScreen(startCam);
        Point2D endScreen = projectToScreen(endCam);
        
        return new ScreenSegment(
            (int) Math.round(startScreen.x),
            (int) Math.round(endScreen.x),
            startCam.y,
            endCam.y
        );
    }

    /**
     * Represents a projected segment on screen with depth information.
     */
    public static class ScreenSegment {
        public final int startX;
        public final int endX;
        public final double startDistance;
        public final double endDistance;

        public ScreenSegment(int startX, int endX, double startDistance, double endDistance) {
            this.startX = startX;
            this.endX = endX;
            this.startDistance = startDistance;
            this.endDistance = endDistance;
        }

        public boolean isVisible() {
            return startX != endX && startDistance > 0 && endDistance > 0;
        }

        @Override
        public String toString() {
            return String.format("ScreenSegment(x: %d->%d, dist: %.3f->%.3f)", 
                    startX, endX, startDistance, endDistance);
        }
    }

    // Getters
    public double getScreenDistance() { return screenDistance; }
    public int getScreenWidth() { return screenWidth; }
    public int getScreenHeight() { return screenHeight; }
    public int getHalfWidth() { return halfWidth; }
    public int getHalfHeight() { return halfHeight; }

    @Override
    public String toString() {
        return String.format("Projection(dist=%.1f, size=%dx%d)", 
                screenDistance, screenWidth, screenHeight);
    }
}