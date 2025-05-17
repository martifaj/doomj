package com.doomviewer.wad.datatypes;

import com.doomviewer.core.math.Vector2D;

public class Thing {
    public Vector2D pos;
    public int angle;  // H (unsigned short)
    public int type;   // H (unsigned short)
    public int flags;  // H (unsigned short)
}