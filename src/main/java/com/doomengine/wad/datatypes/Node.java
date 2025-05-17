package com.doomengine.wad.datatypes;

import java.util.HashMap;
import java.util.Map;

public class Node {
    public static class BBox {
        public short top;
        public short bottom;
        public short left;
        public short right;
    }

    public short xPartition;
    public short yPartition;
    public short dxPartition;
    public short dyPartition;
    public final Map<String, BBox> bbox;
    public int frontChildId; // H (unsigned short) - Side 0
    public int backChildId;  // H (unsigned short) - Side 1

    public Node() {
        bbox = new HashMap<>();
        bbox.put("front", new BBox());
        bbox.put("back", new BBox());
    }

    /**
     * Gets the child ID based on the side.
     * @param side 0 for front, 1 for back.
     * @return The corresponding child ID.
     */
    public int getChildId(int side) {
        return (side == 0) ? frontChildId : backChildId;
    }
}