package com.doomviewer.wad.assets;

import com.doomviewer.core.Settings;
import com.doomviewer.utils.ImageUtils;
import com.doomviewer.wad.WADReader;
import com.doomviewer.wad.WADReader.LumpInfo;
import com.doomviewer.wad.datatypes.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class AssetData {
    private WADReader reader;
    private List<LumpInfo> directory; // Keep a reference for getLumpIndex

    public List<List<int[]>> palettes; // List of palettes, each palette is a List of int[3] RGB
    public int paletteIdx;
    public List<int[]> currentPalette; // Current active palette

    public Map<String, BufferedImage> sprites; // Name to BufferedImage
    public List<String> pNames; // Patch names
    public List<Patch> texturePatches; // Actual Patch objects

    // Wall and flat textures are stored as int[width][height] (column-major) ARGB pixel arrays
    public Map<String, int[][]> textures;

    public String skyId;
    public String skyTexName;
    public int[][] skyTex; // Sky texture as int[width][height]


    public AssetData(WADReader wadReader, List<LumpInfo> directory) throws IOException {
        this.reader = wadReader;
        this.directory = directory; // Store for getLumpIndex

        // Palettes
        this.palettes = loadPalettes();
        this.paletteIdx = 0;
        this.currentPalette = this.palettes.get(this.paletteIdx);

        // Sprites
        this.sprites = loadSprites("S_START", "S_END");

        // Texture patch names
        LumpInfo pnamesLump = getLumpInfo("PNAMES");
        this.pNames = reader.getLumpInfo("PNAMES") != null ?
                loadStringList(pnamesLump.lumpOffset, pnamesLump.lumpSize, 8, 4) :
                new ArrayList<>();


        // Texture patches
        this.texturePatches = new ArrayList<>();
        for (String pName : this.pNames) {
            this.texturePatches.add(new Patch(this, pName, false));
        }

        // Wall textures
        this.textures = new HashMap<>();
        List<TextureMap> textureMaps = loadTextureMaps("TEXTURE1");
        if (getLumpInfo("TEXTURE2") != null) {
            textureMaps.addAll(loadTextureMaps("TEXTURE2"));
        }

        for (TextureMap texMap : textureMaps) {
            Texture texture = new Texture(this, texMap);
            this.textures.put(texMap.name, texture.getImage());
        }

        // Flat textures
        this.textures.putAll(loadFlats("F_START", "F_END"));

        // Sky
        this.skyId = "F_SKY1"; // This is a flat name, but used as a texture reference
        this.skyTexName = "SKY1"; // This is a texture name
        if (this.textures.containsKey(this.skyTexName)) {
            this.skyTex = this.textures.get(this.skyTexName);
        } else {
            System.err.println("Warning: Sky texture " + this.skyTexName + " not found.");
            // Fallback: create a dummy small blue texture
            this.skyTex = new int[1][1];
            this.skyTex[0][0] = 0xFF0000FF; // Opaque Blue
        }
    }

    private LumpInfo getLumpInfo(String lumpName) {
        for (LumpInfo info : this.directory) {
            if (lumpName.equalsIgnoreCase(info.lumpName)) {
                return info;
            }
        }
        return null;
    }

    private int getLumpIndex(String lumpName) {
        for (int i = 0; i < this.directory.size(); i++) {
            if (lumpName.equalsIgnoreCase(this.directory.get(i).lumpName)) {
                return i;
            }
        }
        return -1; // Or throw exception
    }


    private List<List<int[]>> loadPalettes() throws IOException {
        LumpInfo playpalLump = getLumpInfo("PLAYPAL");
        if (playpalLump == null) throw new IOException("PLAYPAL lump not found");

        List<List<int[]>> allPalettes = new ArrayList<>();
        int numPalettes = playpalLump.lumpSize / (256 * 3); // Each palette is 256 colors * 3 bytes
        for (int i = 0; i < numPalettes; i++) {
            allPalettes.add(reader.readPalette(playpalLump.lumpOffset + (long) i * 256 * 3));
        }
        return allPalettes;
    }

    private List<String> loadStringList(long lumpOffset, int lumpSize, int stringLength, int headerLength) throws IOException {
        List<String> stringList = new ArrayList<>();
        int count = (lumpSize - headerLength) / stringLength; // Python code implies header is subtracted from total size
        // The python uses num_bytes for string length, and header_length only for offset start.
        // The python: range(count), offset = lump_info['lump_offset'] + i * num_bytes + header_length
        // Count is lump_info['lump_size'] // num_bytes
        // This means header_length seems to be a global offset for the first string, and not affecting count.
        // Let's re-evaluate based on python's get_lump_data logic.
        // lump_info = self.reader.directory[lump_index]
        // count = lump_info['lump_size'] // num_bytes
        // data = []
        // for i in range(count):
        //     offset = lump_info['lump_offset'] + i * num_bytes + header_length <--- This is the actual offset for EACH item.
        // It seems the header_length applies to *each* item's offset calculation, which is unusual or I misread.
        // More likely, header_length is an initial offset into the lump data.
        // PNAMEs lump structure: int num_patches; char name[8] * num_patches;
        // So, header_length = 4 bytes for num_patches.
        // Actual number of names: first 4 bytes of PNAMES lump is an int for the count.

        int numNames = (int) reader.readUnsignedInt(lumpOffset); // First 4 bytes is count for PNAMES
        long currentOffset = lumpOffset + headerLength; // Skip header
        for (int i = 0; i < numNames; i++) {
            stringList.add(reader.readString(currentOffset + (long) i * stringLength, stringLength));
        }
        return stringList;
    }


    private Map<String, BufferedImage> loadSprites(String startMarker, String endMarker) throws IOException {
        Map<String, BufferedImage> loadedSprites = new HashMap<>();
        int idx1 = getLumpIndex(startMarker);
        int idx2 = getLumpIndex(endMarker);

        if (idx1 == -1 || idx2 == -1) {
            System.err.println("Warning: Sprite markers " + startMarker + "/" + endMarker + " not found.");
            return loadedSprites;
        }

        for (int i = idx1 + 1; i < idx2; i++) {
            LumpInfo lumpInfo = this.directory.get(i);
            // Skip empty lumps or other markers
            if (lumpInfo.lumpSize == 0 || lumpInfo.lumpName.startsWith("S_") || lumpInfo.lumpName.startsWith("P_") || lumpInfo.lumpName.contains("$")) {
                // Actual sprites don't usually have these prefixes, those are markers.
                // But the python code just iterates and loads. So we follow that.
                // The python code filters S_START and S_END and processes everything in between.
            }
            Patch patch = new Patch(this, lumpInfo.lumpName, true); // isSprite = true for scaling
            loadedSprites.put(lumpInfo.lumpName, patch.getImage());
        }
        return loadedSprites;
    }

    private List<TextureMap> loadTextureMaps(String textureLumpName) throws IOException {
        LumpInfo texLump = getLumpInfo(textureLumpName);
        if (texLump == null) return new ArrayList<>();

        long baseOffset = texLump.lumpOffset;
        TextureHeader textureHeader = reader.readTextureHeader(baseOffset);

        List<TextureMap> loadedTextureMaps = new ArrayList<>();
        for (int i = 0; i < textureHeader.textureCount; i++) {
            // texture_header.texture_data_offset[i] is already an offset from the start of the WAD
            // No, it's an offset from the start of the TEXTUREx lump.
            long texMapOffset = baseOffset + textureHeader.textureDataOffset.get(i);
            loadedTextureMaps.add(reader.readTextureMap(texMapOffset));
        }
        return loadedTextureMaps;
    }


    private Map<String, int[][]> loadFlats(String startMarker, String endMarker) throws IOException {
        Map<String, int[][]> loadedFlats = new HashMap<>();
        int idx1 = getLumpIndex(startMarker);
        int idx2 = getLumpIndex(endMarker);

        if (idx1 == -1 || idx2 == -1) {
            System.err.println("Warning: Flat markers " + startMarker + "/" + endMarker + " not found.");
            return loadedFlats;
        }

        for (int i = idx1 + 1; i < idx2; i++) {
            LumpInfo flatLump = this.directory.get(i);
            if (flatLump.lumpSize == 0) continue; // Skip empty marker lumps

            // Flat data is 64x64 = 4096 bytes, 1 byte per pixel (palette index)
            List<Integer> flatData = new ArrayList<>(flatLump.lumpSize);
            for (int j = 0; j < flatLump.lumpSize; j++) {
                flatData.add(reader.read1Byte(flatLump.lumpOffset + j));
            }

            Flat flat = new Flat(this, flatData);
            loadedFlats.put(flatLump.lumpName, flat.getImage());
        }
        return loadedFlats;
    }

    // Inner classes Patch, Texture, Flat
    // These need access to AssetData's fields like reader, palette

    public static class Patch {
        private AssetData assetData;
        public String name;
        public PatchHeader header;
        public List<PatchColumn> patchColumns;
        public int width;
        public int height;
        private BufferedImage image; // This will be the final image (possibly scaled)

        public Patch(AssetData assetData, String name, boolean isSprite) throws IOException {
            this.assetData = assetData;
            this.name = name.toUpperCase(); // Ensure consistent naming

            LumpInfo patchLump = assetData.getLumpInfo(this.name);
            if (patchLump == null || patchLump.lumpSize == 0) {
                System.err.println("Patch " + this.name + " not found or empty. Creating dummy patch.");
                this.width = 1;
                this.height = 1;
                this.header = new PatchHeader(); // Minimal header
                this.header.width = 1;
                this.header.height = 1;
                this.patchColumns = new ArrayList<>();
                this.image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = this.image.createGraphics();
                g.setColor(Color.MAGENTA); // Use a distinct color for missing/dummy
                g.fillRect(0, 0, 1, 1);
                g.dispose();
                return;
            }

            long patchOffset = patchLump.lumpOffset;
            this.header = assetData.reader.readPatchHeader(patchOffset);
            this.patchColumns = loadPatchColumns(patchOffset, this.header);
            this.width = this.header.width;
            this.height = this.header.height;

            this.image = createImageFromColumns();
            if (isSprite) {
                this.image = ImageUtils.scaleImage(this.image,
                        (int) (this.width * Settings.SCALE),
                        (int) (this.height * Settings.SCALE));
            }
        }

        private List<PatchColumn> loadPatchColumns(long patchOffset, PatchHeader patchHdr) throws IOException {
            List<PatchColumn> columns = new ArrayList<>();
            for (int i = 0; i < patchHdr.width; i++) {
                long columnDataOffset = patchOffset + patchHdr.columnOffset.get(i);
                while (true) {
                    WADReader.Pair<PatchColumn, Long> colResult = assetData.reader.readPatchColumn(columnDataOffset);
                    PatchColumn patchCol = colResult.key;
                    columns.add(patchCol);
                    columnDataOffset = colResult.value;
                    if (patchCol.topDelta == 0xFF) {
                        break;
                    }
                }
            }
            return columns;
        }

        // In AssetData.Patch.java
        private BufferedImage createImageFromColumns() {
            BufferedImage surf = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);
            // Initialize with full transparency (ARGB = 0x00000000)
            for (int y_init = 0; y_init < this.height; y_init++) {
                for (int x_init = 0; x_init < this.width; x_init++) {
                    surf.setRGB(x_init, y_init, 0x00000000);
                }
            }

            // Corrected logic (as discussed previously)
            int postListIndex = 0;
            for (int visualX = 0; visualX < this.width; visualX++) {

                if (postListIndex >= this.patchColumns.size()) {
                    break;
                }

                while (postListIndex < this.patchColumns.size()) {
                    PatchColumn currentPost = this.patchColumns.get(postListIndex);
                    postListIndex++;

                    if (currentPost.topDelta == 0xFF) { // End of posts for this visualX
                        break;
                    }

                    // visualX is already constrained by the outer loop

                    for (int i = 0; i < currentPost.length; i++) {
                        int colorIdx = currentPost.data.get(i);

                        if (colorIdx >= assetData.currentPalette.size()) { // Doom's transparent color is handled by not drawing
                            continue;
                        }
                        // Palette index 247 is traditionally transparent in Doom patches (often represented by COLOR_KEY)
                        // However, the engine structure expects actual pixel data.
                        // The python code uses a COLOR_KEY for Surface.fill then set_colorkey.
                        // Here, we just skip drawing for transparency, which is effectively what happens if
                        // the underlying ARGB pixel buffer isn't touched for that coordinate.
                        // If COLOR_KEY was an explicit palette index, you'd check colorIdx against it.
                        // For now, assume if a pixel is not in a post, it's transparent.

                        int[] rgbPalette = assetData.currentPalette.get(colorIdx);
                        int yPos = i + currentPost.topDelta;

                        if (yPos >= 0 && yPos < this.height) {
                            ImageUtils.setPixel(surf, visualX, yPos, rgbPalette);
                        }
                    }
                }
            }
            return surf;
        }

        public BufferedImage getImage() {
            return image;
        }
    }

    public static class Texture {
        private AssetData assetData;
        private TextureMap texMap;
        private int[][] image; // int[width][height] column-major ARGB

        public Texture(AssetData assetData, TextureMap texMap) throws IOException {
            this.assetData = assetData;
            this.texMap = texMap;
            this.image = createImage();
        }

        private int[][] createImage() throws IOException {
            BufferedImage surface = new BufferedImage(this.texMap.width, this.texMap.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = surface.createGraphics();

            // Fill with fully transparent background
            g2d.setComposite(java.awt.AlphaComposite.Clear);
            g2d.fillRect(0, 0, this.texMap.width, this.texMap.height);
            g2d.setComposite(java.awt.AlphaComposite.SrcOver);

            for (PatchMap patchMapInfo : this.texMap.patchMaps) {
                if (patchMapInfo.pNameIndex >= assetData.texturePatches.size()) {
                    System.err.println("Warning: pNameIndex " + patchMapInfo.pNameIndex + " out of bounds for texturePatches (size: " + assetData.texturePatches.size() + ") in texture " + texMap.name);
                    continue;
                }
                Patch sourcePatch = assetData.texturePatches.get(patchMapInfo.pNameIndex);
                if (sourcePatch.getImage() != null) {
                    // BufferedImage from Patch is already ARGB with transparency handled
                    g2d.drawImage(sourcePatch.getImage(), patchMapInfo.xOffset, patchMapInfo.yOffset, null);
                }
            }
            g2d.dispose();
            return ImageUtils.bufferedImageToColumnMajorIntArray(surface);
        }

        public int[][] getImage() {
            return image;
        }
    }

    public static class Flat {
        private List<Integer> flatData; // Palette indices
        private List<int[]> palette;
        private int[][] image; // 64x64, int[width][height] column-major ARGB

        public Flat(AssetData assetData, List<Integer> flatData) {
            this.flatData = flatData;
            this.palette = assetData.currentPalette;
            this.image = createImage();
        }

        private int[][] createImage() {
            BufferedImage surface = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB); // Flats are 64x64
            for (int i = 0; i < this.flatData.size(); i++) {
                if (i >= 64 * 64) break; // Safety for malformed flat data
                int ix = i % 64;
                int iy = i / 64;
                int colorIdx = this.flatData.get(i);
                int[] rgb = this.palette.get(colorIdx); // Potential ArrayOutOfBounds if colorIdx is bad
                ImageUtils.setPixel(surface, ix, iy, rgb); // Implicitly opaque
            }

            return ImageUtils.bufferedImageToColumnMajorIntArray(surface);
        }

        public int[][] getImage() {
            return image;
        }
    }
}