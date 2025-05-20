package com.doomviewer.wad.datatypes;

import com.doomviewer.misc.math.Vector2D;

/**
 * Represents a Thing structure from the WAD file.
 * Things are game entities like the player, monsters, items, etc.
 */
public class Thing {
    /** 2D position (X, Y) of the Thing in the map. */
    public Vector2D pos;
    /** Initial angle (orientation) of the Thing in BAMS (Binary Angle Measurement System). */
    public int angle;
    /** Type identifier for the Thing (DoomEd number). */
    public int type;
    /** Flags describing properties like skill level appearance, ambient sound, etc. */
    public int flags;

    @Override
    public String toString() {
        return "Thing{" +
               "pos=" + pos +
               ", angleBAMS=" + angle +
               ", type=" + type +
               ", flags=" + flags +
               '}';
    }
}