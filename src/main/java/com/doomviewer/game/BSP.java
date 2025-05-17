package com.doomviewer.game;

import com.doomviewer.core.Settings;
import com.doomviewer.core.math.Vector2D;
import com.doomviewer.main.DoomEngine;
import com.doomviewer.wad.datatypes.Node;
import com.doomviewer.wad.datatypes.Seg;
import com.doomviewer.wad.datatypes.SubSector;

import java.util.List;

public class BSP {
    public static final int SUB_SECTOR_IDENTIFIER = 0x8000; // 2^15 = 32768

    private DoomEngine engine;
    private Player player;
    private List<Node> nodes;
    private List<SubSector> subSectors;
    private List<Seg> segs;
    private int rootNodeId;
    public boolean isTraverseBsp; // Public for SegHandler to modify

    public BSP(DoomEngine engine) {
        this.engine = engine;
        this.player = engine.getPlayer();
        this.nodes = engine.getWadData().nodes;
        this.subSectors = engine.getWadData().subSectors;
        this.segs = engine.getWadData().segments;
        this.rootNodeId = this.nodes.size() - 1;
        this.isTraverseBsp = true;
    }

    public void update() {
        this.isTraverseBsp = true; // Reset before each traversal
        renderBspNode(this.rootNodeId);
    }

    public double getSubSectorHeight() {
        int currentNodeId = this.rootNodeId;

        while (currentNodeId < SUB_SECTOR_IDENTIFIER) { // while it's a node ID
            if (currentNodeId < 0 || currentNodeId >= nodes.size()) { // Should not happen in valid WAD
                System.err.println("Invalid node ID in getSubSectorHeight: " + currentNodeId);
                return 0; // Default height
            }
            Node node = this.nodes.get(currentNodeId);
            boolean isOnBack = isOnBackSide(node);
            currentNodeId = isOnBack ? node.backChildId : node.frontChildId;
        }
        // It's a sub-sector ID
        int subSectorIdx = currentNodeId - SUB_SECTOR_IDENTIFIER;
        if (subSectorIdx < 0 || subSectorIdx >= subSectors.size()) {
            System.err.println("Invalid subsector ID in getSubSectorHeight: " + subSectorIdx);
            return 0; // Default height
        }
        SubSector subSector = this.subSectors.get(subSectorIdx);

        if (subSector.firstSegId < 0 || subSector.firstSegId >= segs.size()) {
            System.err.println("Invalid firstSegId in subSector: " + subSector.firstSegId);
            return 0; // Default height
        }
        Seg firstSeg = this.segs.get(subSector.firstSegId);

        if (firstSeg.frontSector == null) {
            System.err.println("Seg's front sector is null in getSubSectorHeight.");
            return 0;
        }
        return firstSeg.frontSector.floorHeight;
    }

    private static int angleToX(double angle) { // angle is relative to player's FOV center
        // angle > 0 means to the right of center in screen space (smaller X)
        // angle < 0 means to the left of center (larger X)
        // Py: if angle > 0: x = SCREEN_DIST - math.tan(math.radians(angle)) * H_WIDTH
        // Py: else: x = -math.tan(math.radians(angle)) * H_WIDTH + SCREEN_DIST
        // This formula seems to map angle=0 to SCREEN_DIST.
        // And H_FOV to 0, -H_FOV to 2*SCREEN_DIST (or WIDTH).
        // Let's re-verify: angle=0 (center) -> x = SCREEN_DIST. This is H_WIDTH.
        // angle=H_FOV (right edge) -> x = SCREEN_DIST - tan(H_FOV_rad) * H_WIDTH
        // Since tan(H_FOV_rad) = H_WIDTH / SCREEN_DIST, this becomes x = SCREEN_DIST - (H_WIDTH/SCREEN_DIST)*H_WIDTH.
        // This does not simplify to 0 or WIDTH.
        // Standard projection: screen_x = H_WIDTH - tan(angle_rad) * SCREEN_DIST;
        // Let's use the Python formula as is.
        double x;
        if (angle > 0) { // Right of center view -> Left on screen (smaller x values in typical graphics)
            x = Settings.SCREEN_DIST - Math.tan(Math.toRadians(angle)) * Settings.H_WIDTH;
        } else { // Left of center view -> Right on screen
            x = -Math.tan(Math.toRadians(angle)) * Settings.H_WIDTH + Settings.SCREEN_DIST;
        }
        // The python SCREEN_DIST is H_WIDTH / tan(H_FOV).
        // If angle = 0, x = SCREEN_DIST. For screen coord, this should be H_WIDTH.
        // Let's assume the formula is correct and SCREEN_DIST is a factor.
        // The formula given maps FOV angles to screen X coordinates.
        // Angle 0 (center) should map to H_WIDTH.
        // Angle H_FOV (rightmost) should map to 0.
        // Angle -H_FOV (leftmost) should map to WIDTH.
        // Let's use a common perspective projection formula:
        // screenX = H_WIDTH - (Math.tan(Math.toRadians(angle)) * SCREEN_DIST_FACTOR);
        // Here, angle is relative to player's view center.
        // Python: angle_to_x used for angles *already clipped* to [-H_FOV, H_FOV] range relative to view center.
        // x = H_WIDTH - Math.tan(Math.toRadians(angle)) * Settings.SCREEN_DIST; (This is a more standard way)
        // Let's stick to the Python one if it's known to work in that context.
        // Screen coordinate system: (0,0) top-left.
        // Center of screen is x = H_WIDTH.
        // Angle > 0 is to the right of player's view. This should map to x < H_WIDTH.
        // Angle < 0 is to the left of player's view. This should map to x > H_WIDTH.
        // Correct projection for x in [0, WIDTH]:
        // x_proj = Settings.H_WIDTH - (Math.tan(Math.toRadians(angle)) * Settings.SCREEN_DIST);
        // Let's use the one from the python code verbatim for now, and adjust if it produces inverted results.
        // Python code: if angle > 0 (right): x = H_WIDTH - tan(angle) * H_WIDTH / tan(H_FOV)
        // No, SCREEN_DIST = H_WIDTH / tan(H_FOV).
        // if angle > 0: x = (H_WIDTH / tan(H_FOV)) - tan(angle) * H_WIDTH
        // if angle = 0: x = SCREEN_DIST ( = H_WIDTH / tan(H_FOV)). This is not H_WIDTH.
        // The `angle_to_x` in python seems to be returning a value in a different coordinate range, perhaps scaled for an internal buffer or specific calculation step.
        // The one from `SegHandler.get_x_to_angle_table` is: angle = degrees(atan((H_WIDTH - i) / SCREEN_DIST))
        // So, tan(angle) = (H_WIDTH - i) / SCREEN_DIST  => i = H_WIDTH - tan(angle) * SCREEN_DIST
        // This is the standard one. Let's use this.
        return (int) (Settings.H_WIDTH - Math.tan(Math.toRadians(angle)) * Settings.SCREEN_DIST);

    }

    // Returns: {x1, x2, raw_world_angle1} or null
    public double[] addSegmentToFov(Vector2D vertex1, Vector2D vertex2) {
        double angle1 = pointToAngle(vertex1); // World angle to vertex1
        double angle2 = pointToAngle(vertex2); // World angle to vertex2

        double span = normalizeAngle(angle1 - angle2);

        // Backface culling
        if (span >= 180.0) {
            return null; // Segment is facing away or edge-on
        }

        double rwAngle1 = angle1; // Store raw world angle for later use by SegHandler

        // Angles relative to player's current view direction
        double relAngle1 = normalizeAngle(angle1 - player.angle);
        double relAngle2 = normalizeAngle(angle2 - player.angle);

        // Clip to FOV
        // H_FOV is positive. View is [player.angle - H_FOV, player.angle + H_FOV]
        // Or, relative angles: [-H_FOV, H_FOV]
        // Python: angle1 -= self.player.angle ... span1 = self.norm(angle1 + H_FOV)
        // This means relAngle1 is now in range, e.g. -360 to 360.
        // norm(relAngle1 + H_FOV) > FOV
        // If relAngle1 is 10, H_FOV is 45. norm(10+45)=55. FOV is 90. 55 > 90 is false.
        // If relAngle1 is 50, H_FOV is 45. norm(50+45)=95. FOV is 90. 95 > 90 is true.
        //    If 95 >= span + FOV (e.g. span=5, 95 >= 5+90=95), then outside.
        //    Else clip: relAngle1 = H_FOV (45)

        // Convert angles to be centered around 0 for player's view direction
        // Positive angles are to the player's "left", negative to "right" based on typical math atan2 results vs screen view
        // DOOM angles: 0 is East, 90 North, 180 West, 270 South (CW from East)
        // Player angle: 0 is East.
        // pointToAngle gives angle in math convention (0 East, positive CCW).
        // Let's maintain player.angle as degrees, 0 East, CCW positive.
        // relAngle1/2 are world angles shifted by player's orientation.
        // To make them relative to player's forward view:
        // angle_v_player_fwd = norm(angle_to_vertex - player.angle)
        // Then this angle needs to be clamped to [-H_FOV, H_FOV]
        // The python code's angle manipulation for clipping is a bit complex. Let's simplify.

        // Transform angles to player's view space where 0 is forward.
        double pAngle = player.angle;
        double tAngle1 = normalizeAngle(angle1 - pAngle); // Angle of v1 relative to player's view direction
        double tAngle2 = normalizeAngle(angle2 - pAngle); // Angle of v2 relative to player's view direction

        // Ensure tAngle1 is "left" of tAngle2 if segment crosses 0-degree view transition
        // This part is tricky. The python code's `span1 = self.norm(angle1 + H_FOV)` etc. handles this.
        // Let's stick to its logic for now for `add_segment_to_fov`.
        // Python angles: angle1, angle2 were relative to player.angle.
        // `angle1 -= self.player.angle` effectively.
        // `span1 = self.norm(relAngle1 + H_FOV)`
        // `span2 = self.norm(H_FOV - relAngle2)`
        // Note: relAngle1, relAngle2 can be >180 if they were originally e.g. -10 and became 350.
        // A common way to handle this is to check if a segment (v1,v2) intersects the view frustum planes.

        // Simpler approach: check if span overlaps FOV
        // Convert angles so they are in -180 to 180 range for easier comparison with H_FOV
        double r1 = normalizeAngle180(tAngle1);
        double r2 = normalizeAngle180(tAngle2);

        // If segment is completely outside FOV
        if (r1 > Settings.H_FOV && r2 > Settings.H_FOV && r1 > r2) return null; // Both right, r1 further right
        if (r1 < -Settings.H_FOV && r2 < -Settings.H_FOV && r1 < r2) return null; // Both left, r1 further left

        // Clip angles to FOV bounds
        double cAngle1 = Math.max(-Settings.H_FOV, Math.min(Settings.H_FOV, r1));
        double cAngle2 = Math.max(-Settings.H_FOV, Math.min(Settings.H_FOV, r2));

        // If after clipping, the order is reversed or they are the same, segment is outside or too small
        if (cAngle1 == cAngle2) return null; // Too small or outside

        // One of the original points might be behind player, need to handle that by clipping ray to frustum edge
        // The python code does this with the span checks.
        // `if span1 > FOV: if span1 >= span + FOV: return False; angle1 = H_FOV`
        // This logic correctly clips segments that partially extend beyond FOV.

        // Re-implementing python's clipping:
        double tempAngle1 = tAngle1; // tempAngle1 is relative to player's view, 0 is forward. Can be > 180.
        double tempAngle2 = tAngle2;

        // Clip first point (left view boundary H_FOV, right view boundary -H_FOV for projection function)
        double s1 = normalizeAngle(tempAngle1 + Settings.H_FOV); // Angle from left FOV edge (positive means inside or to the right)
        if (s1 > Settings.FOV) { // tempAngle1 is to the left of the left FOV edge
            if (s1 >= span + Settings.FOV) return null; // Segment entirely to the left of FOV
            tempAngle1 = Settings.H_FOV; // Clip to left FOV edge
        }

        // Clip second point
        double s2 = normalizeAngle(Settings.H_FOV - tempAngle2); // Angle from right FOV edge (positive means inside or to the left)
        if (s2 > Settings.FOV) { // tempAngle2 is to the right of the right FOV edge
            if (s2 >= span + Settings.FOV) return null; // Segment entirely to the right of FOV
            tempAngle2 = -Settings.H_FOV; // Clip to right FOV edge
        }

        // Convert to screen X
        int x1 = angleToX(tempAngle1);
        int x2 = angleToX(tempAngle2);

        if (x1 == x2) return null; // Degenerate segment on screen

        return new double[]{x1, x2, rwAngle1}; // Use double for x1, x2 before passing to SegHandler
    }


    private void renderSubSector(int subSectorId) {
        SubSector subSector = this.subSectors.get(subSectorId);
        for (int i = 0; i < subSector.segCount; i++) {
            Seg seg = this.segs.get(subSector.firstSegId + i);
            if (seg.startVertex == null || seg.endVertex == null) {
                System.err.println("Seg has null vertex. ID: " + (subSector.firstSegId + i));
                continue;
            }

            double[] result = addSegmentToFov(seg.startVertex, seg.endVertex);
            if (result != null) {
                engine.getSegHandler().classifySegment(seg, result[0], result[1], result[2]);
            }
        }
    }

    public static double normalizeAngle(double angle) {
        angle %= 360.0;
        if (angle < 0) angle += 360.0;
        return angle;
    }

    private double normalizeAngle180(double angle) {
        angle = normalizeAngle(angle);
        if (angle > 180.0) {
            angle -= 360.0;
        }
        return angle;
    }


    private boolean checkBox(Node.BBox bbox) {
        // Simplified Python logic: checks if player is outside bbox first, then projects corners.
        // This is a coarse check. If player inside bbox, return true.
        double px = player.pos.x;
        double py = player.pos.y;

        if (px >= bbox.left && px <= bbox.right && py >= bbox.bottom && py <= bbox.top) {
            return true; // Player is inside the bbox
        }

        // Check if any part of the BBox is within player's FOV
        // Form 4 vertices of the bbox
        Vector2D vBL = new Vector2D(bbox.left, bbox.bottom);
        Vector2D vTL = new Vector2D(bbox.left, bbox.top);
        Vector2D vTR = new Vector2D(bbox.right, bbox.top);
        Vector2D vBR = new Vector2D(bbox.right, bbox.bottom);

        // Project all 4 corners to player's view space angles
        double aBL = normalizeAngle180(pointToAngle(vBL) - player.angle);
        double aTL = normalizeAngle180(pointToAngle(vTL) - player.angle);
        double aTR = normalizeAngle180(pointToAngle(vTR) - player.angle);
        double aBR = normalizeAngle180(pointToAngle(vBR) - player.angle);

        // Check if any angle is within FOV
        if (Math.abs(aBL) <= Settings.H_FOV || Math.abs(aTL) <= Settings.H_FOV ||
                Math.abs(aTR) <= Settings.H_FOV || Math.abs(aBR) <= Settings.H_FOV) {
            return true;
        }

        // Check if FOV lines cross any bbox edges (more complex, for now simple check)
        // The python code checks if any FOV boundary ray (player to FOV edge)
        // is between the angles to two adjacent bbox vertices.
        // For simplicity here: if min_angle < H_FOV and max_angle > -H_FOV.
        double minAngle = Math.min(Math.min(aBL, aTL), Math.min(aTR, aBR));
        double maxAngle = Math.max(Math.max(aBL, aTL), Math.max(aTR, aBR));

        if (maxAngle >= -Settings.H_FOV && minAngle <= Settings.H_FOV) {
            // This covers cases where FOV is between min/max angles of bbox points,
            // or bbox angles are between FOV limits.
            return true;
        }

        return false; // Default to not visible if simple checks fail
    }

    private double pointToAngle(Vector2D vertex) {
        double dx = vertex.x - player.pos.x;
        double dy = vertex.y - player.pos.y;
        // Math.atan2 returns angle in radians from -PI to PI. 0 is along positive X-axis.
        // Convert to degrees: 0-360, 0 = East, 90 = North (Doom convention for angles)
        double angleRad = Math.atan2(dy, dx);
        double angleDeg = Math.toDegrees(angleRad);
        return normalizeAngle(angleDeg); // Ensure 0-360 range
    }

    private void renderBspNode(int nodeId) {
        if (!this.isTraverseBsp) {
            return; // Stop traversal if SegHandler determined scene is full
        }

        // Is it a sub-sector?
        if ((nodeId & SUB_SECTOR_IDENTIFIER) != 0) { // Check MSB
            int subSectorIdx = nodeId & (~SUB_SECTOR_IDENTIFIER); // Clear MSB to get index
            if (subSectorIdx < 0 || subSectorIdx >= subSectors.size()) {
                System.err.println("Invalid subsector index from node ID: " + subSectorIdx);
                return;
            }
            renderSubSector(subSectorIdx);
            return;
        }

        // It's a node
        if (nodeId < 0 || nodeId >= nodes.size()) {
            System.err.println("Invalid node ID in renderBspNode: " + nodeId);
            return;
        }
        Node node = this.nodes.get(nodeId);

        boolean isOnBack = isOnBackSide(node);

        // Render the child node on the same side as the player first
        renderBspNode(isOnBack ? node.backChildId : node.frontChildId);

        // Check if the other side's bounding box is visible
        Node.BBox otherBBox = isOnBack ? node.bbox.get("front") : node.bbox.get("back");
        if (checkBox(otherBBox)) {
            renderBspNode(isOnBack ? node.frontChildId : node.backChildId);
        }
    }

    private boolean isOnBackSide(Node node) {
        double dx = player.pos.x - node.xPartition;
        double dy = player.pos.y - node.yPartition;
        // Partition line: (x_part, y_part) to (x_part + dx_part, y_part + dy_part)
        // Normal to partition line is (-dy_part, dx_part) for front side.
        // Dot product of (dx, dy) with normal: dx * (-dy_part) + dy * dx_part
        // Python: dx * node.dy_partition - dy * node.dx_partition <= 0
        // If this is <= 0, player is on the back side (side 1). Otherwise front (side 0).
        return (dx * node.dyPartition - dy * node.dxPartition) <= 0;
    }
}