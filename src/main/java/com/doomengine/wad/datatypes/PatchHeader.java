package com.doomengine.wad.datatypes;

import java.util.List;

public class PatchHeader {
    public int width;         // H (unsigned short)
    public int height;        // H (unsigned short)
    public short leftOffset;  // h (short)
    public short topOffset;   // h (short)
    public List<Integer> columnOffset; // width x I (unsigned int)
}