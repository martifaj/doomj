package com.doomviewer.wad.datatypes;

import java.util.List;

public class TextureHeader {
    public int textureCount;
    public int textureOffset; // Unused in Python after reading, kept for completeness
    public List<Integer> textureDataOffset;
}