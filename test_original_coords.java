// Test original coordinate calculation
import com.doomviewer.misc.Constants;

public class test_original_coords {
    public static void main(String[] args) {
        System.out.println("Screen width: " + Constants.WIDTH + ", H_WIDTH: " + Constants.H_WIDTH);
        System.out.println("SCREEN_DIST: " + Constants.SCREEN_DIST);
        
        // Test case: player at origin looking east (0 degrees)
        double playerX = 0, playerY = 0, playerAngle = 0;
        
        // Test objects at different positions
        testObject("Object directly in front (east)", playerX, playerY, playerAngle, 10, 0);
        testObject("Object to the left (north)", playerX, playerY, playerAngle, 0, 10);
        testObject("Object to the right (south)", playerX, playerY, playerAngle, 0, -10);
        testObject("Object behind (west)", playerX, playerY, playerAngle, -10, 0);
        testObject("Object diagonally front-left", playerX, playerY, playerAngle, 10, 10);
        testObject("Object diagonally front-right", playerX, playerY, playerAngle, 10, -10);
    }
    
    static void testObject(String description, double playerX, double playerY, double playerAngle, 
                          double objX, double objY) {
        System.out.println("\n" + description + ": (" + objX + ", " + objY + ")");
        
        // Original calculation
        double dx = objX - playerX;
        double dy = objY - playerY;
        double playerAngleRad = Math.toRadians(playerAngle);
        double cosPlayerAngle = Math.cos(playerAngleRad);
        double sinPlayerAngle = Math.sin(playerAngleRad);
        double camSpaceX = dx * -sinPlayerAngle + dy * cosPlayerAngle;
        double camSpaceZ = dx * cosPlayerAngle + dy * sinPlayerAngle;
        
        if (camSpaceZ <= 0.5) {
            System.out.println("  Behind player or too close");
            return;
        }
        
        double scale = Constants.SCREEN_DIST / camSpaceZ;
        double screenXCenter = Constants.H_WIDTH - camSpaceX * scale;
        
        System.out.println("  dx: " + dx + ", dy: " + dy);
        System.out.println("  camSpaceX: " + String.format("%.3f", camSpaceX) + 
                          ", camSpaceZ: " + String.format("%.3f", camSpaceZ));
        System.out.println("  scale: " + String.format("%.3f", scale));
        System.out.println("  screenXCenter: " + String.format("%.1f", screenXCenter) + 
                          " (center=" + Constants.H_WIDTH + ")");
        
        if (screenXCenter < Constants.H_WIDTH) {
            System.out.println("  → Appears LEFT of center");
        } else if (screenXCenter > Constants.H_WIDTH) {
            System.out.println("  → Appears RIGHT of center");
        } else {
            System.out.println("  → Appears at CENTER");
        }
    }
}