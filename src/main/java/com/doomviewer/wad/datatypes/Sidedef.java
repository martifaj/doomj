package com.doomviewer.wad.datatypes;

public class Sidedef {
    public short xOffset;
    public short yOffset;
    public String upperTexture;
    public String lowerTexture;
    public String middleTexture;
    public int sectorId;  // H (unsigned short)
    public Sector sector; // Reference to actual Sector object
}