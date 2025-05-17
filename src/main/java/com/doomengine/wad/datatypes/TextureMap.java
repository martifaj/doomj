package com.doomengine.wad.datatypes;

import java.util.List;

public class TextureMap {
    public String name;
    public int flags;
    public int width;
    public int height;
    public int columnDir; // unused
    public int patchCount;
    public List<PatchMap> patchMaps;
}