package com.doomviewer.geometry;

import com.doomviewer.misc.Constants;

/**
 * Demonstration class showing how to use the geometry package
 * for common Doom engine calculations.
 */
public class DoomGeometryDemo {

    /**
     * Demonstrates basic vector operations useful in Doom.
     */
    public static void demonstrateVectorOperations() {
        System.out.println("=== Vector Operations Demo ===");
        
        // Player movement vector
        Vector2D forward = Vector2D.fromAngle(Angle.degrees(45)); // 45 degrees
        Vector2D movement = forward.multiply(10); // Move 10 units forward
        
        System.out.println("Forward direction: " + forward);
        System.out.println("Movement vector: " + movement);
        
        // Calculate perpendicular for strafing
        Vector2D strafe = forward.perpendicular().multiply(5);
        System.out.println("Strafe vector: " + strafe);
        
        // Combine movement and strafing
        Vector2D totalMovement = movement.add(strafe);
        System.out.println("Total movement: " + totalMovement);
        System.out.println("Movement length: " + totalMovement.length());
        System.out.println();
    }

    /**
     * Demonstrates angle calculations for player rotation and aiming.
     */
    public static void demonstrateAngleOperations() {
        System.out.println("=== Angle Operations Demo ===");
        
        Angle playerAngle = Angle.degrees(90); // Facing north
        Angle turnAmount = Angle.degrees(45);  // Turn 45 degrees
        
        Angle newAngle = playerAngle.add(turnAmount);
        System.out.println("Player angle: " + playerAngle);
        System.out.println("After turning: " + newAngle);
        
        // Normalize angle to [0, 360) range
        Angle normalized = newAngle.normalizeTo360();
        System.out.println("Normalized: " + normalized);
        
        // Calculate shortest rotation to target
        Angle targetAngle = Angle.degrees(270);
        Angle shortestRotation = playerAngle.shortestDistanceTo(targetAngle);
        System.out.println("Shortest rotation to target: " + shortestRotation);
        System.out.println();
    }

    /**
     * Demonstrates wall collision detection.
     */
    public static void demonstrateWallCollision() {
        System.out.println("=== Wall Collision Demo ===");
        
        // Define a wall segment
        Point2D wallStart = new Point2D(0, 0);
        Point2D wallEnd = new Point2D(100, 0);
        LineSegment2D wall = new LineSegment2D(wallStart, wallEnd);
        
        // Player position and movement
        Point2D playerPos = new Point2D(50, 10);
        Vector2D movement = new Vector2D(0, -15); // Moving toward wall
        Point2D newPos = playerPos.add(movement);
        
        System.out.println("Wall: " + wall);
        System.out.println("Player position: " + playerPos);
        System.out.println("Intended new position: " + newPos);
        
        // Check collision
        double playerRadius = 16; // Doom player radius
        double distanceToWall = wall.distanceToPoint(newPos);
        boolean collision = distanceToWall < playerRadius;
        
        System.out.println("Distance to wall: " + distanceToWall);
        System.out.println("Collision detected: " + collision);
        
        if (collision) {
            // Find closest point on wall for sliding
            Point2D closestPoint = wall.closestPointTo(newPos);
            Vector2D wallNormal = wall.normal();
            System.out.println("Closest point on wall: " + closestPoint);
            System.out.println("Wall normal: " + wallNormal);
        }
        System.out.println();
    }

    /**
     * Demonstrates 3D to 2D projection for rendering.
     */
    public static void demonstrateProjection() {
        System.out.println("=== Projection Demo ===");
        
        Projection projection = new Projection(Constants.SCREEN_DIST, Constants.WIDTH, Constants.HEIGHT);
        
        // 3D points in world space
        Point3D wallTop = new Point3D(50, 100, 64);    // Wall top at distance 100
        Point3D wallBottom = new Point3D(50, 100, 0);   // Wall bottom at same distance
        
        // Project to screen
        Point2D screenTop = projection.projectToScreen(wallTop);
        Point2D screenBottom = projection.projectToScreen(wallBottom);
        
        System.out.println("3D wall top: " + wallTop);
        System.out.println("3D wall bottom: " + wallBottom);
        System.out.println("Screen top: " + screenTop);
        System.out.println("Screen bottom: " + screenBottom);
        
        // Calculate wall height on screen
        double screenHeight = Math.abs(screenTop.y - screenBottom.y);
        System.out.println("Wall height on screen: " + screenHeight + " pixels");
        
        // Calculate scale factor
        double scale = projection.scaleAtDistance(100);
        System.out.println("Scale factor at distance 100: " + scale);
        System.out.println();
    }

    /**
     * Demonstrates coordinate transformations.
     */
    public static void demonstrateTransformations() {
        System.out.println("=== Transformation Demo ===");
        
        // Original point
        Point2D point = new Point2D(10, 20);
        System.out.println("Original point: " + point);
        
        // Apply transformations
        Transform2D rotation = Transform2D.rotation(Angle.degrees(90));
        Transform2D translation = Transform2D.translation(5, 10);
        Transform2D scale = Transform2D.scale(2.0);
        
        // Combine transformations: scale, then rotate, then translate
        Transform2D combined = scale.then(rotation).then(translation);
        
        Point2D transformed = combined.transform(point);
        System.out.println("After scale(2) -> rotate(90°) -> translate(5,10): " + transformed);
        
        // Apply inverse transformation
        Transform2D inverse = combined.inverse();
        Point2D restored = inverse.transform(transformed);
        System.out.println("After inverse transformation: " + restored);
        boolean closeToOriginal = GeometryUtils.approximately(point.x, restored.x) && 
                                  GeometryUtils.approximately(point.y, restored.y);
        System.out.println("Close to original: " + closeToOriginal);
        System.out.println();
    }

    /**
     * Demonstrates line intersection for door mechanics.
     */
    public static void demonstrateLineIntersection() {
        System.out.println("=== Line Intersection Demo ===");
        
        // Two walls that might intersect
        LineSegment2D wall1 = new LineSegment2D(0, 0, 100, 100);
        LineSegment2D wall2 = new LineSegment2D(0, 100, 100, 0);
        
        System.out.println("Wall 1: " + wall1);
        System.out.println("Wall 2: " + wall2);
        
        Point2D intersection = wall1.intersectionWith(wall2);
        if (intersection != null) {
            System.out.println("Intersection point: " + intersection);
        } else {
            System.out.println("Walls do not intersect");
        }
        
        // Check if walls are parallel
        boolean parallel = wall1.isParallelTo(wall2);
        System.out.println("Walls are parallel: " + parallel);
        System.out.println();
    }

    /**
     * Demonstrates field of view calculations.
     */
    public static void demonstrateFOVCalculations() {
        System.out.println("=== Field of View Demo ===");
        
        Projection projection = new Projection(Constants.SCREEN_DIST, Constants.WIDTH, Constants.HEIGHT);
        
        // Calculate field of view
        Angle fov = DoomGeometryUtils.calculateFOV(Constants.WIDTH, Constants.SCREEN_DIST);
        System.out.println("Field of view: " + fov);
        
        // Check if a point is within FOV
        Point2D playerPos = new Point2D(0, 0);
        Angle playerAngle = Angle.degrees(0); // Facing east
        Point2D target = new Point2D(100, 50);
        
        Vector2D toTarget = playerPos.vectorTo(target);
        Angle angleToTarget = toTarget.angle();
        Angle angleDiff = playerAngle.shortestDistanceTo(angleToTarget);
        
        boolean inFOV = angleDiff.abs().degrees() <= fov.degrees() / 2;
        
        System.out.println("Player position: " + playerPos);
        System.out.println("Player facing: " + playerAngle);
        System.out.println("Target position: " + target);
        System.out.println("Angle to target: " + angleToTarget);
        System.out.println("Angle difference: " + angleDiff);
        System.out.println("Target in FOV: " + inFOV);
        System.out.println();
    }

    /**
     * Demonstrates bounding box calculations for spatial optimization.
     */
    public static void demonstrateBoundingBox() {
        System.out.println("=== Bounding Box Demo ===");
        
        // Create some points representing a room
        Point2D[] roomCorners = {
            new Point2D(0, 0),
            new Point2D(100, 0),
            new Point2D(100, 80),
            new Point2D(0, 80)
        };
        
        GeometryUtils.BoundingBox roomBounds = GeometryUtils.boundingBox(roomCorners);
        System.out.println("Room bounds: " + roomBounds);
        System.out.println("Room center: " + roomBounds.center());
        System.out.println("Room size: " + roomBounds.width() + " x " + roomBounds.height());
        
        // Check if player is in room
        Point2D playerPos = new Point2D(50, 40);
        boolean playerInRoom = roomBounds.contains(playerPos);
        System.out.println("Player position: " + playerPos);
        System.out.println("Player in room: " + playerInRoom);
        System.out.println();
    }

    /**
     * Main method to run all demonstrations.
     */
    public static void main(String[] args) {
        System.out.println("Doom Geometry Package Demonstration");
        System.out.println("===================================");
        System.out.println();
        
        demonstrateVectorOperations();
        demonstrateAngleOperations();
        demonstrateWallCollision();
        demonstrateProjection();
        demonstrateTransformations();
        demonstrateLineIntersection();
        demonstrateFOVCalculations();
        demonstrateBoundingBox();
        
        System.out.println("Demo completed!");
    }
}