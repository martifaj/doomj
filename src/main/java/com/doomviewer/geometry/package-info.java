/**
 * Comprehensive geometry package for the Doom engine.
 * 
 * This package provides a complete set of geometric primitives and operations
 * used throughout the Doom rendering engine, including:
 * 
 * <h3>Core Primitives:</h3>
 * <ul>
 *   <li>{@link com.doomviewer.geometry.Point2D} - 2D points with double precision</li>
 *   <li>{@link com.doomviewer.geometry.Point3D} - 3D points with double precision</li>
 *   <li>{@link com.doomviewer.geometry.Vector2D} - Enhanced 2D vectors with comprehensive operations</li>
 *   <li>{@link com.doomviewer.geometry.Vector3D} - 3D vectors with comprehensive operations</li>
 * </ul>
 * 
 * <h3>Linear Geometry:</h3>
 * <ul>
 *   <li>{@link com.doomviewer.geometry.Line2D} - Infinite lines in 2D space</li>
 *   <li>{@link com.doomviewer.geometry.LineSegment2D} - Finite line segments (useful for walls)</li>
 * </ul>
 * 
 * <h3>Transformations and Projections:</h3>
 * <ul>
 *   <li>{@link com.doomviewer.geometry.Angle} - Angle utilities with degree/radian conversion</li>
 *   <li>{@link com.doomviewer.geometry.Transform2D} - 2D transformation matrices</li>
 *   <li>{@link com.doomviewer.geometry.Projection} - 3D to 2D projection for rendering</li>
 * </ul>
 * 
 * <h3>Utilities:</h3>
 * <ul>
 *   <li>{@link com.doomviewer.geometry.GeometryUtils} - Common geometric calculations</li>
 * </ul>
 * 
 * <h3>Design Principles:</h3>
 * <ul>
 *   <li><strong>Immutability:</strong> Most classes are immutable for thread safety and predictability</li>
 *   <li><strong>Precision:</strong> Uses double precision for accurate calculations</li>
 *   <li><strong>Performance:</strong> Optimized for common Doom rendering operations</li>
 *   <li><strong>Clarity:</strong> Clear method names and comprehensive documentation</li>
 * </ul>
 * 
 * <h3>Usage Examples:</h3>
 * 
 * <pre>{@code
 * // Basic vector operations
 * Vector2D v1 = new Vector2D(1, 0);
 * Vector2D v2 = new Vector2D(0, 1);
 * Vector2D sum = v1.add(v2);
 * double angle = v1.angleTo(v2).degrees(); // 90 degrees
 * 
 * // Line segment operations (useful for walls)
 * Point2D start = new Point2D(0, 0);
 * Point2D end = new Point2D(100, 0);
 * LineSegment2D wall = new LineSegment2D(start, end);
 * Point2D playerPos = new Point2D(50, 10);
 * double distance = wall.distanceToPoint(playerPos); // 10.0
 * boolean onLeft = wall.isPointOnLeftSide(playerPos); // true
 * 
 * // Transformations
 * Transform2D rotation = Transform2D.rotation(45);
 * Transform2D translation = Transform2D.translation(10, 20);
 * Transform2D combined = translation.then(rotation);
 * Point2D transformed = combined.transform(new Point2D(1, 1));
 * 
 * // Projection for rendering
 * Projection proj = new Projection(160, 320, 200); // Doom's screen parameters
 * Point3D worldPoint = new Point3D(50, 100, 30);
 * Point2D screenPoint = proj.projectToScreen(worldPoint);
 * }</pre>
 * 
 * @author OpenHands AI
 * @version 1.0
 * @since 1.0
 */
package com.doomviewer.geometry;