import com.doomviewer.wad.WADReader;
import com.doomviewer.wad.WADReader.LumpInfo;
import java.io.IOException;
import java.util.List;

public class ListSprites {
    public static void main(String[] args) {
        try {
            WADReader reader = new WADReader("doom1.wad");
            List<LumpInfo> directory = reader.getDirectory();
            
            // Find sprite section
            int startIdx = -1, endIdx = -1;
            for (int i = 0; i < directory.size(); i++) {
                String name = directory.get(i).lumpName;
                if ("S_START".equals(name)) startIdx = i;
                if ("S_END".equals(name)) endIdx = i;
            }
            
            if (startIdx == -1 || endIdx == -1) {
                System.out.println("Sprite markers not found");
                return;
            }
            
            System.out.println("Sprites from S_START to S_END:");
            System.out.println("Found " + (endIdx - startIdx - 1) + " sprite lumps");
            
            // List all TROO sprites
            System.out.println("\nTROO sprites:");
            for (int i = startIdx + 1; i < endIdx; i++) {
                LumpInfo lump = directory.get(i);
                if (lump.lumpName.startsWith("TROO")) {
                    System.out.println("  " + lump.lumpName + " (size: " + lump.lumpSize + ")");
                }
            }
            
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}