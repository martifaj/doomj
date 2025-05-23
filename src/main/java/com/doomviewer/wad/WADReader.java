package com.doomviewer.wad;

import com.doomviewer.misc.math.Vector2D;
import com.doomviewer.wad.datatypes.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class WADReader implements AutoCloseable {
    private RandomAccessFile wadFile;
    private WADHeaderInfo header;
    private List<LumpInfo> directory;

    public static class WADHeaderInfo {
        public String wadType;
        public int lumpCount;
        public int initOffset;
    }

    public static class LumpInfo {
        public int lumpOffset;
        public int lumpSize;
        public String lumpName;
    }

    public WADReader(String wadPath) throws IOException {
        this.wadFile = new RandomAccessFile(new File(wadPath), "r");
        this.header = readHeader();
        this.directory = readDirectory();
    }

    public byte[] readBytesFromFile(long offset, int numBytes) throws IOException {
        wadFile.seek(offset);
        byte[] buffer = new byte[numBytes];
        wadFile.readFully(buffer);
        return buffer;
    }

    private ByteBuffer getByteBuffer(long offset, int numBytes) throws IOException {
        byte[] bytes = readBytesFromFile(offset, numBytes);
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        bb.order(ByteOrder.LITTLE_ENDIAN); // DOOM WADs are little-endian
        return bb;
    }

    // Read methods for different data types
    public int read1Byte(long offset) throws IOException { // Unsigned byte
        return Byte.toUnsignedInt(getByteBuffer(offset, 1).get());
    }

    public short readSignedShort(long offset) throws IOException { // 'h'
        return getByteBuffer(offset, 2).getShort();
    }

    public int readUnsignedShort(long offset) throws IOException { // 'H'
        return Short.toUnsignedInt(getByteBuffer(offset, 2).getShort());
    }

    public int readSignedInt(long offset) throws IOException { // 'i'
        return getByteBuffer(offset, 4).getInt();
    }

    public long readUnsignedInt(long offset) throws IOException { // 'I' (Java doesn't have unsigned int directly)
        return Integer.toUnsignedLong(getByteBuffer(offset, 4).getInt());
    }

    public String readString(long offset, int numBytes) throws IOException {
        byte[] bytes = readBytesFromFile(offset, numBytes);
        int len = 0;
        while (len < numBytes && bytes[len] != 0) {
            len++;
        }
        return new String(bytes, 0, len, StandardCharsets.US_ASCII).toUpperCase();
    }


    private WADHeaderInfo readHeader() throws IOException {
        WADHeaderInfo headerInfo = new WADHeaderInfo();
        headerInfo.wadType = readString(0, 4);
        headerInfo.lumpCount = readSignedInt(4);
        headerInfo.initOffset = readSignedInt(8);
        return headerInfo;
    }

    private List<LumpInfo> readDirectory() throws IOException {
        List<LumpInfo> dir = new ArrayList<>();
        for (int i = 0; i < this.header.lumpCount; i++) {
            long entryOffset = this.header.initOffset + (long)i * 16;
            LumpInfo lumpInfo = new LumpInfo();
            lumpInfo.lumpOffset = readSignedInt(entryOffset);
            lumpInfo.lumpSize = readSignedInt(entryOffset + 4);
            lumpInfo.lumpName = readString(entryOffset + 8, 8);
            dir.add(lumpInfo);
        }
        return dir;
    }

    public List<LumpInfo> getDirectory() {
        return directory;
    }

    public LumpInfo getLumpInfo(String lumpName) {
        for (LumpInfo info : directory) {
            if (lumpName.equalsIgnoreCase(info.lumpName)) {
                return info;
            }
        }
        return null;
    }


    // Methods to read specific WAD structures
    public TextureMap readTextureMap(long offset) throws IOException {
        TextureMap texMap = new TextureMap();
        texMap.name = readString(offset, 8);
        texMap.flags = (int) readUnsignedInt(offset + 8); // Assuming 'I' maps to unsigned int
        texMap.width = readUnsignedShort(offset + 12);
        texMap.height = readUnsignedShort(offset + 14);
        // texMap.columnDir = readUnsignedInt(offset + 16); // unused
        texMap.patchCount = readUnsignedShort(offset + 20);

        texMap.patchMaps = new ArrayList<>();
        for (int i = 0; i < texMap.patchCount; i++) {
            texMap.patchMaps.add(readPatchMap(offset + 22 + (long)i * 10));
        }
        return texMap;
    }

    public PatchMap readPatchMap(long offset) throws IOException {
        PatchMap patchMap = new PatchMap();
        patchMap.xOffset = readSignedShort(offset);
        patchMap.yOffset = readSignedShort(offset + 2);
        patchMap.pNameIndex = readUnsignedShort(offset + 4);
        // patchMap.stepDir = readUnsignedShort(offset + 6); // unused
        // patchMap.colorMap = readUnsignedShort(offset + 8); // unused
        return patchMap;
    }

    public TextureHeader readTextureHeader(long offset) throws IOException {
        TextureHeader texHeader = new TextureHeader();
        texHeader.textureCount = (int) readUnsignedInt(offset); // Assuming 'I'
        // texHeader.textureOffset = readUnsignedInt(offset + 4); // Unused in Python logic

        texHeader.textureDataOffset = new ArrayList<>();
        for (int i = 0; i < texHeader.textureCount; i++) {
            // The python code reads texture_offset[i] from offset + 4 + i*4
            // This seems to be the list of offsets for each texture definition
            texHeader.textureDataOffset.add((int)readUnsignedInt(offset + 4 + (long)i * 4));
        }
        return texHeader;
    }

    // Returns PatchColumn and the new offset to read the next column from
    public Pair<PatchColumn, Long> readPatchColumn(long offset) throws IOException {
        PatchColumn patchColumn = new PatchColumn();
        patchColumn.topDelta = read1Byte(offset);

        if (patchColumn.topDelta != 0xFF) {
            patchColumn.length = read1Byte(offset + 1);
            // patchColumn.paddingPre = read1Byte(offset + 2); // unused

            patchColumn.data = new ArrayList<>();
            for (int i = 0; i < patchColumn.length; i++) {
                patchColumn.data.add(read1Byte(offset + 3 + i));
            }
            // patchColumn.paddingPost = read1Byte(offset + 3 + patchColumn.length); // unused
            return new Pair<>(patchColumn, offset + 4 + patchColumn.length);
        }
        return new Pair<>(patchColumn, offset + 1);
    }

    // Helper Pair class
    public static class Pair<K, V> {
        public K key; public V value;
        public Pair(K key, V value) { this.key = key; this.value = value; }
    }


    public PatchHeader readPatchHeader(long offset) throws IOException {
        PatchHeader patchHeader = new PatchHeader();
        patchHeader.width = readUnsignedShort(offset);
        patchHeader.height = readUnsignedShort(offset + 2);
        patchHeader.leftOffset = readSignedShort(offset + 4);
        patchHeader.topOffset = readSignedShort(offset + 6);

        patchHeader.columnOffset = new ArrayList<>();
        for (int i = 0; i < patchHeader.width; i++) {
            patchHeader.columnOffset.add((int)readUnsignedInt(offset + 8 + (long)i * 4));
        }
        return patchHeader;
    }

    public List<int[]> readPalette(long offset) throws IOException { // List of RGB int arrays
        List<int[]> palette = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            int r = read1Byte(offset + (long)i * 3);
            int g = read1Byte(offset + (long)i * 3 + 1);
            int b = read1Byte(offset + (long)i * 3 + 2);
            palette.add(new int[]{r, g, b});
        }
        return palette;
    }

    public Sector readSector(long offset) throws IOException {
        Sector sector = new Sector();
        sector.floorHeight = readSignedShort(offset);
        sector.ceilHeight = readSignedShort(offset + 2);
        sector.floorTexture = readString(offset + 4, 8);
        sector.ceilTexture = readString(offset + 12, 8);
        sector.lightLevel = readUnsignedShort(offset + 20) / 255.0;
        sector.type = readUnsignedShort(offset + 22);
        sector.tag = readUnsignedShort(offset + 24);
        return sector;
    }

    public Sidedef readSidedef(long offset) throws IOException {
        Sidedef sidedef = new Sidedef();
        sidedef.xOffset = readSignedShort(offset);
        sidedef.yOffset = readSignedShort(offset + 2);
        sidedef.upperTexture = readString(offset + 4, 8);
        sidedef.lowerTexture = readString(offset + 12, 8);
        sidedef.middleTexture = readString(offset + 20, 8);
        sidedef.sectorId = readUnsignedShort(offset + 28);
        return sidedef;
    }

    public Thing readThing(long offset) throws IOException {
        Thing thing = new Thing();
        short x = readSignedShort(offset);
        short y = readSignedShort(offset + 2);
        thing.pos = new Vector2D(x, y);
        thing.angle = readUnsignedShort(offset + 4);
        thing.type = readUnsignedShort(offset + 6);
        thing.flags = readUnsignedShort(offset + 8);
        return thing;
    }

    // In WADReader.java
    public Seg readSegment(long offset) throws IOException {
        Seg seg = new Seg();
        seg.startVertexId = readSignedShort(offset);
        seg.endVertexId = readSignedShort(offset + 2);
        // seg.angle = readSignedShort(offset + 4); // Seg.angle IS DOUBLE. This line is the direct cause if Seg.angle is double.
        // You cannot assign a short to a double field named 'angle' and then
        // later assign a double to the same field without problems IF it was short.

        // Let's assume Seg.angle is indeed double.
        // We need to read the short BAMS value first.
        short bamsAngleRaw = readSignedShort(offset + 4); // Read the raw short

        seg.linedefId = readSignedShort(offset + 6);
        seg.direction = readSignedShort(offset + 8);
        seg.offset = readSignedShort(offset + 10);

        // Now, in WADData.updateSegs, we will use bamsAngleRaw to calculate the final double seg.angle.
        // So, how does WADData get bamsAngleRaw?
        // We can add a temporary field to Seg to pass this:
        // In Seg.java: public short rawBamsAngle; (and initialize Seg.angle to 0.0 initially)
        // Then in WADReader: seg.rawBamsAngle = readSignedShort(offset+4);

        // OR, WADReader.readSegment could return a more complex object or a Pair,
        // but that complicates getLumpData.

        // Simplest for now: Add rawBamsAngle to Seg.java
        // In Seg.java:
        // public double angle;
        // public transient short rawBamsAngle; // transient if you ever serialize, not relevant here

        seg.rawBamsAngle = bamsAngleRaw; // Store it temporarily

        return seg;
    }

    public SubSector readSubSector(long offset) throws IOException {
        SubSector subSector = new SubSector();
        subSector.segCount = readSignedShort(offset);
        subSector.firstSegId = readSignedShort(offset + 2);
        return subSector;
    }

    public Node readNode(long offset) throws IOException {
        Node node = new Node();
        node.xPartition = readSignedShort(offset);
        node.yPartition = readSignedShort(offset + 2);
        node.dxPartition = readSignedShort(offset + 4);
        node.dyPartition = readSignedShort(offset + 6);

        node.bbox.get("front").top = readSignedShort(offset + 8);
        node.bbox.get("front").bottom = readSignedShort(offset + 10);
        node.bbox.get("front").left = readSignedShort(offset + 12);
        node.bbox.get("front").right = readSignedShort(offset + 14);

        node.bbox.get("back").top = readSignedShort(offset + 16);
        node.bbox.get("back").bottom = readSignedShort(offset + 18);
        node.bbox.get("back").left = readSignedShort(offset + 20);
        node.bbox.get("back").right = readSignedShort(offset + 22);

        node.frontChildId = readUnsignedShort(offset + 24);
        node.backChildId = readUnsignedShort(offset + 26);
        return node;
    }

    public Linedef readLinedef(long offset) throws IOException {
        Linedef linedef = new Linedef();
        linedef.startVertexId = readUnsignedShort(offset);
        linedef.endVertexId = readUnsignedShort(offset + 2);
        linedef.flags = readUnsignedShort(offset + 4);
        linedef.lineType = readUnsignedShort(offset + 6);
        linedef.sectorTag = readUnsignedShort(offset + 8);
        linedef.frontSidedefId = readUnsignedShort(offset + 10);
        linedef.backSidedefId = readUnsignedShort(offset + 12); // 0xFFFF is -1 as short, check unsigned
        return linedef;
    }

    public Vector2D readVertex(long offset) throws IOException {
        short x = readSignedShort(offset);
        short y = readSignedShort(offset + 2);
        return new Vector2D(x, y);
    }

    @Override
    public void close() throws IOException {
        if (wadFile != null) {
            wadFile.close();
        }
    }
}