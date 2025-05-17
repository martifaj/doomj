package com.doomengine.wad.datatypes;

public class Linedef {
    public int startVertexId;   // H (unsigned short)
    public int endVertexId;     // H (unsigned short)
    public int flags;           // H (unsigned short)
    public int lineType;        // H (unsigned short)
    public int sectorTag;       // H (unsigned short)
    public int frontSidedefId;  // H (unsigned short)
    public int backSidedefId;   // H (unsigned short), 0xFFFF for none

    // Populated after loading
    public Sidedef frontSidedef;
    public Sidedef backSidedef; // Can be null
}