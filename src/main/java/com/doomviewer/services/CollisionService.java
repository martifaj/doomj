package com.doomviewer.services;

import com.doomviewer.misc.math.Vector2D;

public interface CollisionService {
    boolean isMovementBlocked(Vector2D start, Vector2D end, double radius, boolean logCollisions);
    Vector2D getSafeMovementPosition(Vector2D start, Vector2D desired, double radius, boolean logCollisions);
    double getSubSectorHeightAt(double x, double y);
    boolean isPositionValid(Vector2D position, double radius);
    boolean hasLineOfSight(Vector2D start, Vector2D end);
    boolean circleIntersectsLineSegment(Vector2D circleCenter, double radius, Vector2D lineStart, Vector2D lineEnd);
}