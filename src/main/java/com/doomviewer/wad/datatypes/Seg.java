package com.doomviewer.wad.datatypes;
import com.doomviewer.core.math.Vector2D;

public class Seg {
    public short startVertexId;
    public short endVertexId;
    public double angle; // Final converted angle in degrees
    public transient short rawBamsAngle; // Temporary storage for raw BAMS value from WAD
    public short linedefId;
    public short direction;
    public short offset;

    public Vector2D startVertex;
    public Vector2D endVertex;
    public Linedef linedef;
    public Sector frontSector;
    public Sector backSector;

    public Seg() {
        this.angle = 0.0; // Initialize
    }
}