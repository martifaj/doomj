package com.doomviewer.rendering;

public class SectorRenderInfo {
    public double floorHeight;
    public double ceilingHeight;
    public double lightLevel; // Normalized to 0.0 - 1.0

    public SectorRenderInfo(double floorHeight, double ceilingHeight, int rawLightLevel) {
        this.floorHeight = floorHeight;
        this.ceilingHeight = ceilingHeight;
        this.lightLevel = rawLightLevel / 255.0; // Normalize from 0-255 to 0.0-1.0
    }
}

