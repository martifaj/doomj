package com.doomengine.wad.assets;

import com.doomengine.misc.Constants;
import com.doomengine.rendering.FrameBuffer; // Updated import
import com.doomengine.wad.WADReader;
import com.doomengine.wad.WADReader.LumpInfo;
import com.doomengine.wad.datatypes.*;

import java.util.logging.Logger;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.doomengine.misc.Constants.TEXTURE_HEIGHT;
import static com.doomengine.misc.Constants.TEXTURE_WIDTH;


public class AssetData {
    private final WADReader reader;
    private static final Logger LOGGER = Logger.getLogger(AssetData.class.getName());
    private final List<LumpInfo> directory; // Keep a reference for getLumpIndex

    public List<List<int[]>> palettes; // List of palettes, each palette is a List of int[3] RGB
    public int paletteIdx;
    public List<int[]> currentPalette; // Current active palette

    public Map<String, BufferedImage> sprites; // Name to BufferedImage
    public List<String> pNames; // Patch names
    public List<Patch> texturePatches; // Actual Patch objects

    // Wall and flat textures are stored as int[width][height] (column-major) ARGB pixel arrays
    public Map<String, int[][]> textures;
    private final Map<String, Patch> spritePatches; // To store patch objects for sprites

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

        // Initialize spritePatches before loadSprites is called
        this.spritePatches = new HashMap<>();
        // Sprites
        this.sprites = loadSprites("S_START", "S_END");
        
        // Load face graphics (HUD face sprites are stored as graphics, not sprites)
        loadFaceGraphics();

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
            LOGGER.warning("Sky texture not found: " + this.skyTexName);
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

    public Patch getSpritePatch(String spriteLumpName) {
        if (spritePatches == null) return null; // if loadSprites wasn't modified to populate it
        return spritePatches.get(spriteLumpName.toUpperCase());
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
        int numNames = (int) reader.readUnsignedInt(lumpOffset); // First 4 bytes is count for PNAMES
        long currentOffset = lumpOffset + headerLength; // Skip header
        for (int i = 0; i < numNames; i++) {
            stringList.add(reader.readString(currentOffset + (long) i * stringLength, stringLength));
        }
        return stringList;
    }


    private Map<String, BufferedImage> loadSprites(String startMarker, String endMarker) throws IOException {
        // Make sure this.spritePatches is initialized before calling this
        Map<String, BufferedImage> loadedSpriteImages = new HashMap<>();
        int idx1 = getLumpIndex(startMarker);
        int idx2 = getLumpIndex(endMarker);

        if (idx1 == -1 || idx2 == -1) {
            LOGGER.warning("Sprite markers not found: " + startMarker + "/" + endMarker);
            return loadedSpriteImages;
        }

        LOGGER.info("Loading sprites from " + startMarker + " to " + endMarker + " (indices " + idx1 + "-" + idx2 + ")");
        int spritesLoaded = 0;
        
        for (int i = idx1 + 1; i < idx2; i++) {
            LumpInfo lumpInfo = this.directory.get(i);
            if (lumpInfo.lumpSize == 0) continue;

            String lumpNameUpper = lumpInfo.lumpName.toUpperCase();

            Patch patch = new Patch(this, lumpNameUpper, true); // Use uppercase consistently
            this.spritePatches.put(lumpNameUpper, patch);
            loadedSpriteImages.put(lumpNameUpper, patch.getImage());
            spritesLoaded++;
        }
        
        LOGGER.info("Total sprites loaded: " + spritesLoaded);


        this.sprites = loadedSpriteImages; // Assign to the class member
        return loadedSpriteImages; // Though constructor won't use return value
    }
    
    private void loadFaceGraphics() throws IOException {
        // List of face sprite names to look for
        String[] faceSprites = {
            "STFST00", "STFST10", "STFST20", "STFST30", "STFST40",
            "STFST01", "STFST11", "STFST21", "STFST31", "STFST41", 
            "STFST02", "STFST12", "STFST22", "STFST32", "STFST42",
            "STFOUCH0", "STFOUCH1", "STFOUCH2", "STFOUCH3", "STFOUCH4",
            "STFEVL0", "STFEVL1", "STFEVL2", "STFEVL3", "STFEVL4",
            "STFKILL0", "STFKILL1", "STFKILL2", "STFKILL3", "STFKILL4",
            "STFGOD0", "STFDEAD0"
        };
        
        LOGGER.info("Loading face graphics as patches...");
        int facesLoaded = 0;
        
        for (String faceName : faceSprites) {
            try {
                // Try to load as a patch (like regular graphics)
                Patch facePatch = new Patch(this, faceName, true); // Use sprite scaling
                if (facePatch.getImage() != null) {
                    this.sprites.put(faceName, facePatch.getImage());
                    this.spritePatches.put(faceName, facePatch);
                    LOGGER.info("Loaded face graphic: " + faceName);
                    facesLoaded++;
                }
            } catch (Exception e) {
                // Face sprite not found, continue to next
                LOGGER.fine("Face graphic not found: " + faceName);
            }
        }
        
        LOGGER.info("Total face graphics loaded: " + facesLoaded);
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
            LOGGER.warning("Flat markers not found: " + startMarker + "/" + endMarker);
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
        private final AssetData assetData;
        public final String name;
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
                LOGGER.warning("Patch not found or empty: " + this.name + ". Creating dummy.");
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
                // Create a Framebuffer from the patch image to use scaleSelf
                FrameBuffer patchFrameBuffer = new FrameBuffer(this.image);
                patchFrameBuffer.scaleSelf(
                    (int) (this.width * Constants.SCALE),
                    (int) (this.height * Constants.SCALE)
                );
                this.image = patchFrameBuffer.getImageBuffer(); // Get the scaled image back
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

                        int[] rgbPalette = assetData.currentPalette.get(colorIdx);
                        int yPos = i + currentPost.topDelta;

                        if (yPos >= 0 && yPos < this.height) {
                            // Create a temporary Framebuffer to use applyPixelToImageBuffer
                            // This is inefficient if done per pixel. Consider if this logic can be optimized.
                            // For now, directly setting RGB on surf as Framebuffer.setPixel was static.
                            // If Framebuffer instance methods are preferred, surf itself should be a Framebuffer.
                            // However, createImageFromColumns returns BufferedImage.
                            // Let's assume direct manipulation of surf (BufferedImage) is intended here.
                            int colorValue = (255 << 24) | (rgbPalette[0] << 16) | (rgbPalette[1] << 8) | rgbPalette[2];
                            surf.setRGB(visualX, yPos, colorValue);
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
        private final AssetData assetData;
        private final TextureMap texMap;
        private final int[][] image; // int[width][height] column-major ARGB

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
                    LOGGER.warning("pNameIndex out of bounds: " + patchMapInfo.pNameIndex + " (size: " + assetData.texturePatches.size() + ") in texture " + texMap.name);
                    continue;
                }
                Patch sourcePatch = assetData.texturePatches.get(patchMapInfo.pNameIndex);
                if (sourcePatch.getImage() != null) {
                    // BufferedImage from Patch is already ARGB with transparency handled
                    g2d.drawImage(sourcePatch.getImage(), patchMapInfo.xOffset, patchMapInfo.yOffset, null);
                }
            }
            g2d.dispose();
            // Create a Framebuffer from the surface to use getImageBufferAsColumnMajorArray
            FrameBuffer surfaceFrameBuffer = new FrameBuffer(surface);
            return surfaceFrameBuffer.getImageBufferAsColumnMajorArray();
        }

        public int[][] getImage() {
            return image;
        }
    }

    public static class Flat {
        private final List<Integer> flatData; // Palette indices
        private final List<int[]> palette;
        private final int[][] image; // 64x64, int[width][height] column-major ARGB

        public Flat(AssetData assetData, List<Integer> flatData) {
            this.flatData = flatData;
            this.palette = assetData.currentPalette;
            this.image = createImage();
        }

        private int[][] createImage() {
            BufferedImage surface = new BufferedImage(TEXTURE_WIDTH, TEXTURE_HEIGHT, BufferedImage.TYPE_INT_ARGB); // Flats are 64x64
            for (int i = 0; i < this.flatData.size(); i++) {
                if (i >= 64 * 64) break; // Safety for malformed flat data
                int ix = i % 64;
                int iy = i / 64;
                int colorIdx = this.flatData.get(i);
                int[] rgb = this.palette.get(colorIdx);
                // Similar to Patch, directly setting RGB on surface.
                int colorValue = (255 << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
                surface.setRGB(ix, iy, colorValue);
            }

            // Create a Framebuffer from the surface to use getImageBufferAsColumnMajorArray
            FrameBuffer surfaceFrameBuffer = new FrameBuffer(surface);
            return surfaceFrameBuffer.getImageBufferAsColumnMajorArray();
        }

        public int[][] getImage() {
            return image;
        }
    }
}

