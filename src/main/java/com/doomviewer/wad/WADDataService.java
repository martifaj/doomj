package com.doomviewer.wad;

import com.doomviewer.audio.SoundEngine;
import com.doomviewer.misc.math.Vector2D;
import com.doomviewer.wad.assets.AssetData;
import com.doomviewer.wad.datatypes.*;
import com.doomviewer.wad.WADReader.LumpInfo;
import java.util.Map;
import java.util.Collections;
import java.util.logging.Logger;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WADDataService {
    private static final Logger LOGGER = Logger.getLogger(WADDataService.class.getName());

    public static final Map<String, Integer> LUMP_INDICES_MAP = Collections.unmodifiableMap(
        Map.of(
            "THINGS",    1,
            "LINEDEFS",  2,
            "SIDEDEFS",  3,
            "VERTEXES",  4,
            "SEGS",      5,
            "SSECTORS",  6,
            "NODES",     7,
            "SECTORS",   8
        )
    );

    public static final Map<String, Integer> LINEDEF_FLAGS_MAP = Collections.unmodifiableMap(
        Map.of(
            "BLOCKING",       1,
            "BLOCK_MONSTERS", 2,
            "TWO_SIDED",      4,
            "DONT_PEG_TOP",   8,
            "DONT_PEG_BOTTOM",16,
            "SECRET",        32,
            "SOUND_BLOCK",   64,
            "DONT_DRAW",    128,
            "MAPPED",       256
        )
    );


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


    public WADDataService(String wadPath, String mapName) throws IOException {
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
        
        // Load sounds
        loadSounds();

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
                LOGGER.warning("Invalid sidedef sector_id: " + sidedef.sectorId);
                // Potentially assign a default/dummy sector or handle error
            }
        }
    }

    private void updateLinedefs() {
        for (Linedef linedef : this.linedefs) {
            if (linedef.frontSidedefId >= 0 && linedef.frontSidedefId < this.sidedefs.size()) {
                linedef.frontSidedef = this.sidedefs.get(linedef.frontSidedefId);
            } else {
                LOGGER.warning("Invalid linedef front_sidedef_id: " + linedef.frontSidedefId);
            }

            if (linedef.backSidedefId == 0xFFFF || linedef.backSidedefId == -1) { // 0xFFFF as unsigned short
                linedef.backSidedef = null;
            } else {
                if (linedef.backSidedefId >= 0 && linedef.backSidedefId < this.sidedefs.size()) {
                    linedef.backSidedef = this.sidedefs.get(linedef.backSidedefId);
                } else {
                    LOGGER.warning("Invalid linedef back_sidedef_id: " + linedef.backSidedefId);
                    linedef.backSidedef = null;
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
                LOGGER.warning("Seg has null linedef (id: " + seg.linedefId + "), skipping update.");
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
                LOGGER.warning("Seg's effective front sidedef is null.");
            }

            if ((seg.linedef.flags & LINEDEF_FLAGS_MAP.get("TWO_SIDED")) != 0) {
                if (backSidedef != null) {
                    seg.backSector = backSidedef.sector;
                } else {
                    LOGGER.warning("Two-sided linedef but back sidedef is null for seg id: " + seg.linedefId);
                    seg.backSector = null;
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
            // Convert 16-bit BAMS angle to degrees
            seg.angle = bamsToDegrees(seg.rawBamsAngle);

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

    /**
     * Convert a 16-bit Binary Angle Measurement System (BAMS) value to degrees in [0,360).
     * @param bams 16-bit BAMS angle
     * @return angle in degrees
     */
    private static double bamsToDegrees(short bams) {
        int unsigned = bams & 0xFFFF;
        return unsigned * (360.0 / 65536.0);
    }
    
    // Functional interface for reading data items
    @FunctionalInterface
    private interface ItemReader<T> {
        T read(long offset) throws IOException;
    }

    private <T> List<T> getLumpData(ItemReader<T> readerFunc, int lumpIndexInDir, int numBytesPerItem) throws IOException {
        if (lumpIndexInDir < 0 || lumpIndexInDir >= reader.getDirectory().size()) {
            LOGGER.warning("Invalid lump index: " + lumpIndexInDir);
            return new ArrayList<>();
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
    
    private void loadSounds() {
        SoundEngine soundEngine = SoundEngine.getInstance();
        int soundsLoaded = 0;
        
        LOGGER.info("Loading sounds from WAD...");
        
        // Load all sound lumps (they start with DS)
        for (LumpInfo lumpInfo : reader.getDirectory()) {
            if (lumpInfo.lumpName.startsWith("DS") && lumpInfo.lumpSize > 0) {
                try {
                    byte[] soundData = reader.readBytesFromFile(lumpInfo.lumpOffset, lumpInfo.lumpSize);
                    soundEngine.loadSound(lumpInfo.lumpName, soundData);
                    soundsLoaded++;
                } catch (IOException e) {
                    LOGGER.warning("Failed to load sound: " + lumpInfo.lumpName + " - " + e.getMessage());
                }
            }
        }
        
        LOGGER.info("Loaded " + soundsLoaded + " sounds from WAD");
    }
}