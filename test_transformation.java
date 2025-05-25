// Quick test to verify coordinate transformation
import com.doomviewer.geometry.*;

public class test_transformation {
    public static void main(String[] args) {
        // Test case: player at origin, looking east (0 degrees)
        Point2D playerPos = new Point2D(0, 0);
        Angle playerAngle = Angle.degrees(0);
        
        // Object directly in front of player
        Point2D objPos = new Point2D(0, 10);
        
        System.out.println("Test 1: Object directly in front");
        System.out.println("Player: " + playerPos + ", angle: " + playerAngle.degrees() + "°");
        System.out.println("Object: " + objPos);
        
        // Original calculation
        double dx = objPos.x - playerPos.x;  // 0
        double dy = objPos.y - playerPos.y;  // 10
        double playerAngleRad = Math.toRadians(playerAngle.degrees());
        double cosPlayerAngle = Math.cos(playerAngleRad);  // 1
        double sinPlayerAngle = Math.sin(playerAngleRad);  // 0
        double origCamSpaceX = dx * -sinPlayerAngle + dy * cosPlayerAngle;  // 0 * 0 + 10 * 1 = 10
        double origCamSpaceZ = dx * cosPlayerAngle + dy * sinPlayerAngle;   // 0 * 1 + 10 * 0 = 0
        
        System.out.println("Original: camSpaceX=" + origCamSpaceX + ", camSpaceZ=" + origCamSpaceZ);
        
        // New calculation
        Vector2D camSpaceVector = DoomGeometryUtils.worldPositionToCameraSpace(objPos, playerPos, playerAngle);
        System.out.println("New: camSpaceX=" + camSpaceVector.x + ", camSpaceZ=" + camSpaceVector.y);
        
        System.out.println();
        
        // Test case: player looking north (90 degrees)
        playerAngle = Angle.degrees(90);
        System.out.println("Test 2: Player looking north");
        System.out.println("Player: " + playerPos + ", angle: " + playerAngle.degrees() + "°");
        System.out.println("Object: " + objPos);
        
        // Original calculation
        playerAngleRad = Math.toRadians(playerAngle.degrees());
        cosPlayerAngle = Math.cos(playerAngleRad);  // 0
        sinPlayerAngle = Math.sin(playerAngleRad);  // 1
        origCamSpaceX = dx * -sinPlayerAngle + dy * cosPlayerAngle;  // 0 * -1 + 10 * 0 = 0
        origCamSpaceZ = dx * cosPlayerAngle + dy * sinPlayerAngle;   // 0 * 0 + 10 * 1 = 10
        
        System.out.println("Original: camSpaceX=" + origCamSpaceX + ", camSpaceZ=" + origCamSpaceZ);
        
        // New calculation
        camSpaceVector = DoomGeometryUtils.worldPositionToCameraSpace(objPos, playerPos, playerAngle);
        System.out.println("New: camSpaceX=" + camSpaceVector.x + ", camSpaceZ=" + camSpaceVector.y);
    }
}