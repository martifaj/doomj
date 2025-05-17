package com.doomviewer.wad;

import com.doomviewer.core.math.Vector2D;
import com.doomviewer.wad.assets.AssetData;
import com.doomviewer.wad.datatypes.*;
import com.doomviewer.wad.WADReader.LumpInfo;


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WADData {

    public static final Map<String, Integer> LUMP_INDICES_MAP = new HashMap<>() {{
        put("THINGS", 1); put("LINEDEFS", 2); put("SIDEDEFS", 3);
        put("VERTEXES", 4); put("SEGS", 5); put("SSECTORS", 6);
        put("NODES", 7); put("SECTORS", 8); /* REJECT: 9, BLOCKMAP: 10 */
    }};

    public static final Map<String, Integer> LINEDEF_FLAGS_MAP = new HashMap<>() {{
        put("BLOCKING", 1); put("BLOCK_MONSTERS", 2); put("TWO_SIDED", 4);
        put("DONT_PEG_TOP", 8); put("DONT_PEG_BOTTOM", 16); put("SECRET", 32);
        put("SOUND_BLOCK", 64); put("DONT_DRAW", 128); put("MAPPED", 256);
    }};


    private WADReader reader;
    private int mapLumpIndex;
    private String mapName;

    public List<Vector2D> vertexes;
    public List<Linedef> linedefs;
    public List<Node> nodes;
    public List<SubSector> subSectors;
    public List<Seg> segments;
    public List<Thing> things;
    public List<Sidedef> sidedefs;
    public List<Sector> sectors;

    public AssetData assetData;


    public WADData(String wadPath, String mapName) throws IOException {
        this.reader = new WADReader(wadPath);
        this.mapName = mapName.toUpperCase();
        this.mapLumpIndex = getLumpIndexByName(this.mapName);
        if (this.mapLumpIndex == -1) {
            throw new IOException("Map " + mapName + " not found in WAD.");
        }

        // Load map specific lumps
        this.vertexes = getLumpData(
                offset -> reader.readVertex(offset),
                this.mapLumpIndex + LUMP_INDICES_MAP.get("VERTEXES"),
                4); // num bytes per vertex

        this.linedefs = getLumpData(
                offset -> reader.readLinedef(offset),
                this.mapLumpIndex + LUMP_INDICES_MAP.get("LINEDEFS"),
                14);

        this.nodes = getLumpData(
                offset -> reader.readNode(offset),
                this.mapLumpIndex + LUMP_INDICES_MAP.get("NODES"),
                28);

        this.subSectors = getLumpData(
                offset -> reader.readSubSector(offset),
                this.mapLumpIndex + LUMP_INDICES_MAP.get("SSECTORS"),
                4);

        this.segments = getLumpData(
                offset -> reader.readSegment(offset),
                this.mapLumpIndex + LUMP_INDICES_MAP.get("SEGS"),
                12);

        this.things = getLumpData(
                offset -> reader.readThing(offset),
                this.mapLumpIndex + LUMP_INDICES_MAP.get("THINGS"),
                10);

        this.sidedefs = getLumpData(
                offset -> reader.readSidedef(offset),
                this.mapLumpIndex + LUMP_INDICES_MAP.get("SIDEDEFS"),
                30);

        this.sectors = getLumpData(
                offset -> reader.readSector(offset),
                this.mapLumpIndex + LUMP_INDICES_MAP.get("SECTORS"),
                26);

        updateDataRelationships();

        // Load assets (textures, sprites, palettes)
        // Pass the reader and its directory to AssetData
        this.assetData = new AssetData(this.reader, this.reader.getDirectory());

        // It's important AssetData is created *before* closing the reader if it needs it.
        // The Python code closes reader after AssetData.
        this.reader.close(); // Close WAD file after all data is read
    }

    private void updateDataRelationships() {
        updateSidedefs();
        updateLinedefs();
        updateSegs();
    }

    private void updateSidedefs() {
        for (Sidedef sidedef : this.sidedefs) {
            if (sidedef.sectorId >= 0 && sidedef.sectorId < this.sectors.size()) {
                sidedef.sector = this.sectors.get(sidedef.sectorId);
            } else {
                System.err.println("Warning: Sidedef has invalid sector_id: " + sidedef.sectorId);
                // Potentially assign a default/dummy sector or handle error
            }
        }
    }

    private void updateLinedefs() {
        for (Linedef linedef : this.linedefs) {
            if (linedef.frontSidedefId >= 0 && linedef.frontSidedefId < this.sidedefs.size()) {
                linedef.frontSidedef = this.sidedefs.get(linedef.frontSidedefId);
            } else {
                System.err.println("Warning: Linedef has invalid front_sidedef_id: " + linedef.frontSidedefId);
            }

            if (linedef.backSidedefId == 0xFFFF || linedef.backSidedefId == -1) { // 0xFFFF as unsigned short
                linedef.backSidedef = null;
            } else {
                if (linedef.backSidedefId >= 0 && linedef.backSidedefId < this.sidedefs.size()) {
                    linedef.backSidedef = this.sidedefs.get(linedef.backSidedefId);
                } else {
                    System.err.println("Warning: Linedef has invalid back_sidedef_id: " + linedef.backSidedefId);
                    linedef.backSidedef = null; // Treat as one-sided
                }
            }
        }
    }

    private void updateSegs() {
        for (Seg seg : this.segments) {
            if (seg.startVertexId >= 0 && seg.startVertexId < this.vertexes.size()) {
                seg.startVertex = this.vertexes.get(seg.startVertexId);
            }
            if (seg.endVertexId >= 0 && seg.endVertexId < this.vertexes.size()) {
                seg.endVertex = this.vertexes.get(seg.endVertexId);
            }
            if (seg.linedefId >= 0 && seg.linedefId < this.linedefs.size()) {
                seg.linedef = this.linedefs.get(seg.linedefId);
            }

            if (seg.linedef == null) {
                System.err.println("Warning: Seg has null linedef (id: " + seg.linedefId + "). Skipping seg update.");
                continue;
            }

            Sidedef frontSidedef, backSidedef;
            if (seg.direction == 1) { // 1 means seg is on back side of linedef
                frontSidedef = seg.linedef.backSidedef;
                backSidedef = seg.linedef.frontSidedef;
            } else { // 0 means seg is on front side of linedef
                frontSidedef = seg.linedef.frontSidedef;
                backSidedef = seg.linedef.backSidedef;
            }

            if (frontSidedef != null) {
                seg.frontSector = frontSidedef.sector;
            } else {
                System.err.println("Warning: Seg's effective front sidedef is null.");
            }

            if ((seg.linedef.flags & LINEDEF_FLAGS_MAP.get("TWO_SIDED")) != 0) {
                if (backSidedef != null) {
                    seg.backSector = backSidedef.sector;
                } else {
                    System.err.println("Warning: Seg's linedef is TWO_SIDED but effective back sidedef is null.");
                    seg.backSector = null; // Treat as error or one-sided for this seg
                }
            } else {
                seg.backSector = null;
            }

            // Convert BAMS to degrees for seg.angle
            // Original angle is short. Python: (seg.angle << 16) * 8.38190317e-8
            // This is because BAMS are stored in the upper 16 bits of a 32-bit value in some contexts,
            // but here seg.angle is just a short. The constant is 360 / (2^16 * 2^16 / 2^16) = 360 / 2^16
            // No, Doom's Binary Angle Measurement System (BAMS) uses unsigned 16-bit integers.
            // 0x0000 is East, 0x4000 is North, 0x8000 is West, 0xC000 is South.
            // The short value read from WAD is this.
            // To convert to degrees: angle_degrees = (bams_value / 65536.0) * 360.0
            // The python code seems to use a more direct conversion if `seg.angle` was already scaled.
            // `seg.angle` is `short` from WAD.
            // `(seg.angle << 16)` makes it a 32-bit value, then `* 8.38190317e-8`.
            // `8.38190317e-8` is `360 / (2^32)`. This is for fine angles.
            // Let's check common Doom angle conversion:
            // Typically, map angles (like seg.angle) are `(unsigned short / 65535.0) * 360.0`.
            // Or, if it's for `Math.atan2` results, `angle_degrees = bams_angle * (360.0 / 65536.0)`.
            // The python expression `(seg.angle << 16) * 8.38190317e-8` is equivalent to `seg.angle * (2^16) * (360 / 2^32)`
            // which simplifies to `seg.angle * (360 / 2^16)`.
            // This is the standard conversion for a 16-bit BAMS value.


            // Python's normalization `seg.angle = seg.angle + 360 if seg.angle < 0 else seg.angle`
            // This implies the result could be negative. Standard BAMS to degrees is 0-360.
            // The `(short)seg.angle` could be negative if MSB is set.
            // If `seg.angle` is signed short from WAD:
            // Let's trust the Python code's math:
            // `(seg.angle << 16)`: if seg.angle is -1 (0xFFFF), this becomes 0xFFFF0000.
            // If `seg.angle` is `short`:
            long extendedAngle = ((long)seg.angle & 0xFFFF) << 16; // Treat as unsigned short then shift
            // Or simply: double originalAngle = seg.angle; (promotes to double)
            // originalAngle = originalAngle * (360.0 / 65536.0);
            double calculatedAngle = ( (double)seg.angle * Math.pow(2,16) ) * 8.38190317e-8;
            // The original python code is `seg.angle = (seg.angle << 16) * 8.38190317e-8`
            // If `seg.angle` is a short, `<< 16` effectively multiplies by 2^16, BUT it operates on the int representation.
            // A Java short cast to int, then shifted:
            // int shifted = ((int)seg.angle) << 16; // This is correct if seg.angle means the high word.
            // However, seg.angle from WAD is a direct 16-bit BAMS.
            // The most direct conversion for a 16-bit BAMS (stored as short) to degrees:
            // `degrees = ( ( (int)seg.angle & 0xFFFF ) / 65536.0) * 360.0;`
            // The python code is a bit idiosyncratic here. Let's try to match its effect.
            // `(seg.angle << 16)` effectively puts the 16-bit BAMS value into the high 16 bits of a 32-bit integer.
            // Example: BAMS 0x4000 (North, 90deg). seg.angle = 0x4000 = 16384.
            // (16384 << 16) = 0x40000000 = 1073741824.
            // 1073741824 * 8.38190317e-8 = 90.0. This works.

            int shiftedIntAngle = ((int)seg.rawBamsAngle) << 16; // Use the raw short value
            seg.angle = shiftedIntAngle * 8.38190317e-8;        // Assign to the double field

            if (seg.angle < 0) {
                seg.angle += 360.0;
            }
            seg.angle %= 360.0;

            // Texture special case from Python
            if (seg.frontSector != null && seg.backSector != null && frontSidedef != null && backSidedef != null) {
                if ("-".equals(frontSidedef.upperTexture)) {
                    seg.linedef.frontSidedef.upperTexture = backSidedef.upperTexture;
                }
                if ("-".equals(frontSidedef.lowerTexture)) {
                    seg.linedef.frontSidedef.lowerTexture = backSidedef.lowerTexture;
                }
            }
        }
    }

    // Functional interface for reading data items
    @FunctionalInterface
    private interface ItemReader<T> {
        T read(long offset) throws IOException;
    }

    private <T> List<T> getLumpData(ItemReader<T> readerFunc, int lumpIndexInDir, int numBytesPerItem) throws IOException {
        if (lumpIndexInDir < 0 || lumpIndexInDir >= reader.getDirectory().size()) {
            System.err.println("Warning: Invalid lump index: " + lumpIndexInDir);
            return new ArrayList<>(); // Return empty list or throw
        }
        LumpInfo lumpInfo = reader.getDirectory().get(lumpIndexInDir);
        int count = lumpInfo.lumpSize / numBytesPerItem;
        List<T> data = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long offset = lumpInfo.lumpOffset + (long)i * numBytesPerItem;
            data.add(readerFunc.read(offset));
        }
        return data;
    }

    private int getLumpIndexByName(String lumpName) {
        List<LumpInfo> directory = reader.getDirectory();
        for (int i = 0; i < directory.size(); i++) {
            if (lumpName.equalsIgnoreCase(directory.get(i).lumpName)) {
                return i;
            }
        }
        return -1; // Not found
    }

    // Python's get_lump_index was part of AssetData, but it used WADData's reader.
    // Here, WADData owns the reader initially.
    // This method is not directly used by WADData itself after initialization, but AssetData might need it if it didn't get the directory.
    // AssetData now gets the directory, so it can implement its own getLumpIndex.
}