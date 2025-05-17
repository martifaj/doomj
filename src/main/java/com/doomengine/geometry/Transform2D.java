package com.doomengine.geometry;

/**
 * Represents a 2D transformation matrix for translation, rotation, and scaling.
 * Uses a 3x3 matrix representation for 2D homogeneous coordinates.
 */
public class Transform2D {
    // Matrix elements: [m00 m01 m02]
    //                  [m10 m11 m12]
    //                  [ 0   0   1 ]
    private final double m00, m01, m02;
    private final double m10, m11, m12;

    public static final Transform2D IDENTITY = new Transform2D(1, 0, 0, 0, 1, 0);

    private Transform2D(double m00, double m01, double m02, double m10, double m11, double m12) {
        this.m00 = m00;
        this.m01 = m01;
        this.m02 = m02;
        this.m10 = m10;
        this.m11 = m11;
        this.m12 = m12;
    }

    public static Transform2D identity() {
        return IDENTITY;
    }

    public static Transform2D translation(double tx, double ty) {
        return new Transform2D(1, 0, tx, 0, 1, ty);
    }

    public static Transform2D translation(Vector2D translation) {
        return translation(translation.x(), translation.y());
    }

    public static Transform2D rotation(Angle angle) {
        double cos = angle.cos();
        double sin = angle.sin();
        return new Transform2D(cos, -sin, 0, sin, cos, 0);
    }

    public static Transform2D rotation(double angleDegrees) {
        return rotation(Angle.degrees(angleDegrees));
    }

    public static Transform2D scale(double sx, double sy) {
        return new Transform2D(sx, 0, 0, 0, sy, 0);
    }

    /**
     * Creates a custom transformation matrix.
     * Matrix format: [m00 m01 m02]
     *                [m10 m11 m12]
     *                [ 0   0   1 ]
     */
    public static Transform2D matrix(double m00, double m01, double m02, double m10, double m11, double m12) {
        return new Transform2D(m00, m01, m02, m10, m11, m12);
    }

    public static Transform2D scale(double s) {
        return scale(s, s);
    }

    /**
     * Creates a transformation that rotates around a specific point.
     */
    public static Transform2D rotationAround(Point2D center, Angle angle) {
        return translation(-center.x(), -center.y())
                .then(rotation(angle))
                .then(translation(center.x(), center.y()));
    }

    /**
     * Creates a transformation that scales around a specific point.
     */
    public static Transform2D scaleAround(Point2D center, double sx, double sy) {
        return translation(-center.x(), -center.y())
                .then(scale(sx, sy))
                .then(translation(center.x(), center.y()));
    }

    /**
     * Combines this transformation with another (this * other).
     */
    public Transform2D then(Transform2D other) {
        return new Transform2D(
            m00 * other.m00 + m01 * other.m10,
            m00 * other.m01 + m01 * other.m11,
            m00 * other.m02 + m01 * other.m12 + m02,
            m10 * other.m00 + m11 * other.m10,
            m10 * other.m01 + m11 * other.m11,
            m10 * other.m02 + m11 * other.m12 + m12
        );
    }

    /**
     * Transforms a point.
     */
    public Point2D transform(Point2D point) {
        return new Point2D(
            m00 * point.x() + m01 * point.y() + m02,
            m10 * point.x() + m11 * point.y() + m12
        );
    }

    /**
     * Transforms a vector (ignores translation).
     */
    public Vector2D transform(Vector2D vector) {
        return new Vector2D(
            m00 * vector.x() + m01 * vector.y(),
            m10 * vector.x() + m11 * vector.y()
        );
    }

    /**
     * Transforms a line segment.
     */
    public LineSegment2D transform(LineSegment2D segment) {
        return new LineSegment2D(
            transform(segment.start),
            transform(segment.end)
        );
    }

    /**
     * Returns the inverse transformation.
     * Throws IllegalStateException if the matrix is not invertible.
     */
    public Transform2D inverse() {
        double det = m00 * m11 - m01 * m10;
        if (Math.abs(det) < 1e-10) {
            throw new IllegalStateException("Transform is not invertible (determinant is zero)");
        }

        double invDet = 1.0 / det;
        return new Transform2D(
            m11 * invDet,
            -m01 * invDet,
            (m01 * m12 - m02 * m11) * invDet,
            -m10 * invDet,
            m00 * invDet,
            (m02 * m10 - m00 * m12) * invDet
        );
    }

    /**
     * Returns the determinant of the transformation matrix.
     */
    public double determinant() {
        return m00 * m11 - m01 * m10;
    }

    /**
     * Checks if this transformation preserves orientation (determinant > 0).
     */
    public boolean preservesOrientation() {
        return determinant() > 0;
    }

    /**
     * Extracts the translation component.
     */
    public Vector2D getTranslation() {
        return new Vector2D(m02, m12);
    }

    /**
     * Extracts the rotation angle (assuming uniform scaling).
     */
    public Angle getRotation() {
        return Angle.radians(Math.atan2(m10, m00));
    }

    /**
     * Extracts the scale factors.
     */
    public Vector2D getScale() {
        double sx = Math.sqrt(m00 * m00 + m10 * m10);
        double sy = Math.sqrt(m01 * m01 + m11 * m11);
        return new Vector2D(sx, sy);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Transform2D that = (Transform2D) obj;
        return Double.compare(that.m00, m00) == 0 &&
               Double.compare(that.m01, m01) == 0 &&
               Double.compare(that.m02, m02) == 0 &&
               Double.compare(that.m10, m10) == 0 &&
               Double.compare(that.m11, m11) == 0 &&
               Double.compare(that.m12, m12) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(m00);
        result = 31 * result + Double.hashCode(m01);
        result = 31 * result + Double.hashCode(m02);
        result = 31 * result + Double.hashCode(m10);
        result = 31 * result + Double.hashCode(m11);
        result = 31 * result + Double.hashCode(m12);
        return result;
    }

    @Override
    public String toString() {
        return String.format("Transform2D[%.3f %.3f %.3f; %.3f %.3f %.3f]",
                m00, m01, m02, m10, m11, m12);
    }
}