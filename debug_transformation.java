// Debug transformation to compare original vs new implementation
import com.doomviewer.geometry.*;

public class debug_transformation {
    public static void main(String[] args) {
        // Test multiple scenarios
        testScenario("Player at origin, looking east, object in front", 
                    0, 0, 0,     // player x, y, angle
                    0, 10);      // object x, y
        
        testScenario("Player at origin, looking east, object to the right", 
                    0, 0, 0,     // player x, y, angle
                    10, 0);      // object x, y
        
        testScenario("Player at origin, looking north, object in front", 
                    0, 0, 90,    // player x, y, angle
                    0, 10);      // object x, y
        
        testScenario("Player moved, looking east, object stationary", 
                    5, 5, 0,     // player x, y, angle
                    0, 10);      // object x, y
        
        testScenario("Player rotated, object should appear to move on screen", 
                    0, 0, 45,    // player x, y, angle
                    0, 10);      // object x, y
    }
    
    static void testScenario(String description, double playerX, double playerY, double playerAngle, 
                           double objX, double objY) {
        System.out.println("\n" + description);
        System.out.println("Player: (" + playerX + ", " + playerY + ") angle: " + playerAngle + "°");
        System.out.println("Object: (" + objX + ", " + objY + ")");
        
        // Original calculation
        double dx = objX - playerX;
        double dy = objY - playerY;
        double playerAngleRad = Math.toRadians(playerAngle);
        double cosPlayerAngle = Math.cos(playerAngleRad);
        double sinPlayerAngle = Math.sin(playerAngleRad);
        double origCamSpaceX = dx * -sinPlayerAngle + dy * cosPlayerAngle;
        double origCamSpaceZ = dx * cosPlayerAngle + dy * sinPlayerAngle;
        
        // New calculation
        Point2D playerPos = new Point2D(playerX, playerY);
        Point2D objPos = new Point2D(objX, objY);
        Angle angle = Angle.degrees(playerAngle);
        Vector2D camSpaceVector = DoomGeometryUtils.worldPositionToCameraSpace(objPos, playerPos, angle);
        
        System.out.println("Original: camSpaceX=" + String.format("%.3f", origCamSpaceX) + 
                          ", camSpaceZ=" + String.format("%.3f", origCamSpaceZ));
        System.out.println("New:      camSpaceX=" + String.format("%.3f", camSpaceVector.x) + 
                          ", camSpaceZ=" + String.format("%.3f", camSpaceVector.y));
        
        // Check if they match
        double diffX = Math.abs(origCamSpaceX - camSpaceVector.x);
        double diffZ = Math.abs(origCamSpaceZ - camSpaceVector.y);
        boolean matches = diffX < 1e-10 && diffZ < 1e-10;
        System.out.println("Match: " + matches + (matches ? "" : " (diff X: " + diffX + ", Z: " + diffZ + ")"));
    }
}