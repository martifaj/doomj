import java.util.*;

public class debug_sprites {
    public static void main(String[] args) {
        // Test sprite name generation for Imp firing states
        System.out.println("Testing Imp firing sprite names:");
        
        // Frame 8 (I) with rotation 1
        char frameChar8 = (char) ('A' + 8);
        String sprite8 = String.format("TROO%c%d", frameChar8, 1);
        System.out.println("Frame 8: " + sprite8);
        
        // Frame 9 (J) with rotation 1  
        char frameChar9 = (char) ('A' + 9);
        String sprite9 = String.format("TROO%c%d", frameChar9, 1);
        System.out.println("Frame 9: " + sprite9);
        
        // Let's also check what other frames look like
        for (int i = 0; i < 12; i++) {
            char frameChar = (char) ('A' + i);
            String sprite = String.format("TROO%c%d", frameChar, 1);
            System.out.println("Frame " + i + " (" + frameChar + "): " + sprite);
        }
    }
}