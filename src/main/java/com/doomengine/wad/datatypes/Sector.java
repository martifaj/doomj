package com.doomengine.wad.datatypes;

public class Sector {
    public short floorHeight;
    public short ceilHeight;
    public String floorTexture;
    public String ceilTexture;
    public double lightLevel; // Converted from unsigned short
    public int type;
    public int tag;
}