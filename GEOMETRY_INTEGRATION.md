# Geometry Package Integration Guide

This document describes the new geometry package and how to integrate it into the Doom engine for improved rendering and collision detection.

## Overview

The geometry package (`com.doomviewer.geometry`) provides a comprehensive set of classes for geometric calculations used throughout the Doom engine. It replaces scattered geometric calculations with a clean, type-safe, and well-tested API.

## Package Structure

### Core Classes

- **Point2D/Point3D**: Immutable point classes with distance and interpolation methods
- **Vector2D/Vector3D**: Enhanced vector classes with comprehensive operations
- **Angle**: Type-safe angle handling with degree/radian conversion and normalization
- **Line2D/LineSegment2D**: Line and line segment operations for wall calculations
- **Transform2D**: 2D transformation matrices for coordinate transformations
- **Projection**: 3D to 2D projection utilities for rendering

### Utility Classes

- **GeometryUtils**: Common geometric calculations and helper methods
- **DoomGeometryUtils**: Doom-specific geometry utilities bridging generic classes with Doom data structures

### Demonstration Classes

- **GeometricSegHandler**: Enhanced SegHandler using geometry classes
- **GeometricBSP**: Enhanced BSP implementation with geometry classes
- **DoomGeometryDemo**: Comprehensive examples of geometry package usage

## Key Benefits

### 1. Type Safety
```java
// Old approach - error-prone
double angle = 90.0; // Is this degrees or radians?
double normalizedAngle = normalizeAngle(angle);

// New approach - type-safe
Angle angle = Angle.degrees(90);
Angle normalizedAngle = angle.normalize();
```

### 2. Immutability
```java
// All geometry objects are immutable - no accidental modifications
Vector2D v1 = new Vector2D(1, 0);
Vector2D v2 = v1.rotate(Angle.degrees(90)); // v1 unchanged, v2 is new vector
```

### 3. Cleaner Code
```java
// Old approach - verbose and error-prone
double dx = player.pos.x - seg.startVertex.x;
double dy = player.pos.y - seg.startVertex.y;
double distance = Math.sqrt(dx * dx + dy * dy);

// New approach - clear and concise
Point2D playerPos = new Point2D(player.pos.x, player.pos.y);
Point2D segStart = new Point2D(seg.startVertex.x, seg.startVertex.y);
double distance = playerPos.distanceTo(segStart);
```

### 4. Better Collision Detection
```java
// Enhanced wall collision with proper geometric operations
LineSegment2D wall = DoomGeometryUtils.segToLineSegment(seg);
double distanceToWall = wall.distanceToPoint(playerPosition);
boolean collision = distanceToWall < playerRadius;
```

## Integration Steps

### Step 1: Update Imports

Replace existing Vector2D imports:
```java
// Old
import com.doomviewer.misc.math.Vector2D;

// New
import com.doomviewer.geometry.*;
```

### Step 2: Replace Distance Calculations

```java
// Old
double distance = Vector2D.distance(player.pos, seg.startVertex);

// New
Point2D playerPos = new Point2D(player.pos.x, player.pos.y);
Point2D segStart = new Point2D(seg.startVertex.x, seg.startVertex.y);
double distance = playerPos.distanceTo(segStart);
```

### Step 3: Use Angle Class for Angle Operations

```java
// Old
double normalizedAngle = normalizeAngle(seg.angle + 90);
double cosTheta = Math.cos(Math.toRadians(normalizedAngle - player.angle - xAngle));

// New
Angle segAngle = Angle.degrees(seg.angle);
Angle normalAngle = segAngle.add(Angle.degrees(90));
Angle playerAngle = Angle.degrees(player.angle);
Angle xAngle = xToAngleTable[x]; // Now returns Angle instead of double
Angle angleDiff = normalAngle.subtract(playerAngle).subtract(xAngle);
double cosTheta = angleDiff.cos();
```

### Step 4: Use Projection Class for 3D Calculations

```java
// Create projection helper
Projection projection = new Projection(Constants.SCREEN_DIST, Constants.WIDTH, Constants.HEIGHT);

// Project 3D points to screen
Point3D worldPoint = new Point3D(x, y, z);
Point2D screenPoint = projection.projectToScreen(worldPoint);
```

### Step 5: Use LineSegment2D for Wall Operations

```java
// Create line segment from wall
LineSegment2D wall = new LineSegment2D(
    new Point2D(seg.startVertex.x, seg.startVertex.y),
    new Point2D(seg.endVertex.x, seg.endVertex.y)
);

// Check which side of wall player is on
boolean onLeftSide = wall.isPointOnLeftSide(playerPosition);

// Calculate distance to wall
double distance = wall.distanceToPoint(playerPosition);
```

## Migration Strategy

### Phase 1: Core Geometry (Current)
- ✅ Create geometry package with all classes
- ✅ Create demonstration classes showing usage
- ✅ Create enhanced SegHandler and BSP examples

### Phase 2: Gradual Integration
- Update SegHandler to use geometry classes for distance calculations
- Update BSP to use geometry classes for side determination
- Update collision detection to use LineSegment2D

### Phase 3: Full Integration
- Replace all Vector2D usage with new geometry classes
- Update all angle calculations to use Angle class
- Integrate Projection class into rendering pipeline

### Phase 4: Optimization
- Add spatial indexing using bounding boxes
- Implement frustum culling using geometry classes
- Add level-of-detail calculations

## Example Usage

### Basic Vector Operations
```java
Vector2D forward = Vector2D.fromAngle(Angle.degrees(playerAngle));
Vector2D movement = forward.multiply(speed);
Vector2D strafe = forward.perpendicular().multiply(strafeSpeed);
Vector2D totalMovement = movement.add(strafe);
```

### Wall Collision Detection
```java
LineSegment2D wall = DoomGeometryUtils.segToLineSegment(seg);
Point2D newPlayerPos = currentPos.add(movement);
double distance = wall.distanceToPoint(newPlayerPos);
if (distance < playerRadius) {
    // Handle collision - slide along wall
    Point2D closestPoint = wall.closestPointTo(newPlayerPos);
    Vector2D wallNormal = wall.normal();
    Vector2D slideMovement = movement.subtract(wallNormal.multiply(movement.dot(wallNormal)));
    newPlayerPos = currentPos.add(slideMovement);
}
```

### 3D Projection
```java
Projection projection = new Projection(screenDist, screenWidth, screenHeight);
Point3D worldPoint = new Point3D(wallX, wallY, wallHeight);
Point2D screenPoint = projection.projectToScreen(worldPoint, cameraPos, cameraAngle);
```

### Angle Calculations
```java
Angle playerAngle = Angle.degrees(player.angle);
Angle wallAngle = DoomGeometryUtils.wallAngle(seg);
Angle wallNormal = wallAngle.add(Angle.degrees(90));
Angle incidenceAngle = playerAngle.shortestDistanceTo(wallNormal);
```

## Testing

Run the demonstration class to see the geometry package in action:
```bash
java com.doomviewer.geometry.DoomGeometryDemo
```

This will show examples of all major geometry operations used in Doom rendering.

## Performance Considerations

- All geometry classes are designed for performance with minimal object allocation
- Immutable objects allow for safe caching and sharing
- Double precision provides accuracy for large Doom levels
- Geometric operations are optimized for common Doom use cases

## Future Enhancements

- Add 3D geometry classes for true 3D Doom engines
- Implement spatial indexing (quadtree, BSP tree) using geometry classes
- Add curve and spline support for enhanced level geometry
- Implement advanced collision detection algorithms
- Add support for non-axis-aligned bounding boxes

## Conclusion

The geometry package provides a solid foundation for all geometric calculations in the Doom engine. By using type-safe, immutable, and well-tested geometric primitives, the codebase becomes more maintainable, less error-prone, and easier to extend with new features.