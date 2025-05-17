package com.doomviewer.wad.datatypes;

import java.util.List;

public class PatchColumn {
    public int topDelta;    // B (unsigned byte)
    public int length;      // B (unsigned byte)
    // public int paddingPre;  // B - unused
    public List<Integer> data; // length x B (unsigned bytes)
    // public int paddingPost; // B - unused
}