// Test screen coordinate calculation
import com.doomviewer.geometry.*;
import com.doomviewer.misc.Constants;

public class test_screen_coords {
    public static void main(String[] args) {
        System.out.println("Screen width: " + Constants.WIDTH + ", H_WIDTH: " + Constants.H_WIDTH);
        System.out.println("SCREEN_DIST: " + Constants.SCREEN_DIST);
        
        // Test case: player at origin looking east (0 degrees)
        Point2D playerPos = new Point2D(0, 0);
        Angle playerAngle = Angle.degrees(0);
        
        // Test objects at different positions
        testObject("Object directly in front", playerPos, playerAngle, new Point2D(0, 10));
        testObject("Object to the right", playerPos, playerAngle, new Point2D(10, 10));
        testObject("Object to the left", playerPos, playerAngle, new Point2D(-10, 10));
        testObject("Object far to the right", playerPos, playerAngle, new Point2D(20, 10));
        testObject("Object far to the left", playerPos, playerAngle, new Point2D(-20, 10));
    }
    
    static void testObject(String description, Point2D playerPos, Angle playerAngle, Point2D objPos) {
        System.out.println("\n" + description + ": " + objPos);
        
        Vector2D camSpaceVector = DoomGeometryUtils.worldPositionToCameraSpace(objPos, playerPos, playerAngle);
        double camSpaceX = camSpaceVector.x;
        double camSpaceZ = camSpaceVector.y;
        
        if (camSpaceZ <= 0.5) {
            System.out.println("  Behind player or too close");
            return;
        }
        
        double scale = Constants.SCREEN_DIST / camSpaceZ;
        double screenXCenter = Constants.H_WIDTH - camSpaceX * scale;
        
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