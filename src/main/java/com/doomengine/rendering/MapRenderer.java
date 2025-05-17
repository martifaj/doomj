package com.doomengine.rendering;

import com.doomengine.game.DoomEngine;
import com.doomengine.game.Player;
import com.doomengine.geometry.Angle;
import com.doomengine.geometry.GeometryUtils;
import com.doomengine.geometry.Point2D;
import com.doomengine.geometry.Vector2D;
import com.doomengine.misc.Constants;
import com.doomengine.wad.WADDataService;
import com.doomengine.wad.datatypes.Linedef;
import com.doomengine.wad.datatypes.Node;
import com.doomengine.wad.datatypes.Seg;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MapRenderer {
    private final WADDataService wadDataService;
    private List<Point2D> remappedVertexes;
    private GeometryUtils.BoundingBox mapBounds; // Enhanced bounds using geometry classes
    private final Player player;
    private final Random random = new Random();

    public MapRenderer(DoomEngine engine) {
        this.wadDataService = engine.getWadData();
        this.player = engine.getPlayer();

        calculateMapBounds();
        remapVertexes();
    }

    private void calculateMapBounds() {
        if (wadDataService.vertexes.isEmpty()) {
            mapBounds = new GeometryUtils.BoundingBox(-1, -1, 1, 1); // Default bounds
            return;
        }

        // Convert old Vector2D vertices to Point2D and calculate bounds
        Point2D[] vertices = new Point2D[wadDataService.vertexes.size()];
        for (int i = 0; i < wadDataService.vertexes.size(); i++) {
            com.doomengine.geometry.Vector2D v = wadDataService.vertexes.get(i);
            vertices[i] = new Point2D(v.x(), v.y());
        }

        mapBounds = GeometryUtils.boundingBox(vertices);

        // Ensure bounds have some extent to prevent division by zero in remap
        if (mapBounds.width() == 0) {
            mapBounds = new GeometryUtils.BoundingBox(mapBounds.minX(), mapBounds.minY(),
                    mapBounds.minX() + 1, mapBounds.maxY());
        }
        if (mapBounds.height() == 0) {
            mapBounds = new GeometryUtils.BoundingBox(mapBounds.minX(), mapBounds.minY(),
                    mapBounds.maxX(), mapBounds.minY() + 1);
        }
    }

    private void remapVertexes() {
        remappedVertexes = new ArrayList<>();
        for (com.doomengine.geometry.Vector2D v : wadDataService.vertexes) {
            remappedVertexes.add(new Point2D(remapX(v.x()), remapY(v.y())));
        }
    }

    private double remapX(double worldX) {
        double margin = 30; // Margin from screen edge for map display
        double outMax = Constants.WIDTH - margin;
        // Clamp to bounds to avoid extreme values if player goes off map for drawing map elements
        worldX = GeometryUtils.clamp(worldX, mapBounds.minX(), mapBounds.maxX());
        return (worldX - mapBounds.minX()) * (outMax - margin) / mapBounds.width() + margin;
    }

    private double remapY(double worldY) {
        double margin = 30;
        double outMax = Constants.HEIGHT - margin;
        worldY = GeometryUtils.clamp(worldY, mapBounds.minY(), mapBounds.maxY());
        // Y is inverted for screen coordinates (0 at top)
        return Constants.HEIGHT - ((worldY - mapBounds.minY()) * (outMax - margin) / mapBounds.height() + margin);
    }

    public void draw(Graphics2D g2d) {
        // Main draw call for the 2D map, if active
        // Example: draw all linedefs and player position

        drawLinedefs(g2d);
        drawPlayer(g2d);

        // Optionally draw nodes, segs etc.
    }

    public void drawLinedefs(Graphics2D g2d) {
        g2d.setColor(Color.RED);
        g2d.setStroke(new BasicStroke(1));
        for (Linedef line : wadDataService.linedefs) {
            if (line.startVertexId < 0 || line.startVertexId >= remappedVertexes.size() ||
                    line.endVertexId < 0 || line.endVertexId >= remappedVertexes.size()) continue;

            Point2D p1 = remappedVertexes.get(line.startVertexId);
            Point2D p2 = remappedVertexes.get(line.endVertexId);
            g2d.drawLine((int) p1.x(), (int) p1.y(), (int) p2.x(), (int) p2.y());
        }
    }

    public void drawPlayer(Graphics2D g2d) {
        // Use geometry classes for cleaner player position handling
        Point2D playerWorldPos = new Point2D(player.pos.x(), player.pos.y());
        Point2D playerScreenPos = new Point2D(remapX(playerWorldPos.x()), remapY(playerWorldPos.y()));

        g2d.setColor(Color.ORANGE);
        g2d.fillOval((int) (playerScreenPos.x() - 5), (int) (playerScreenPos.y() - 5), 10, 10);

        // Draw FOV lines using geometry classes
        double lineLength = 50; // Length of FOV lines on map
        Angle playerAngle = Angle.degrees(-player.angle); // Negate for screen Y-down convention
        Angle halfFOV = Angle.degrees(Constants.H_FOV);

        Angle angleL = playerAngle.add(halfFOV);
        Angle angleR = playerAngle.subtract(halfFOV);

        Vector2D directionL = Vector2D.fromAngle(angleL).multiply(lineLength);
        Vector2D directionR = Vector2D.fromAngle(angleR).multiply(lineLength);

        Point2D endPointL = playerScreenPos.add(directionL);
        Point2D endPointR = playerScreenPos.add(directionR);

        g2d.setColor(Color.YELLOW);
        g2d.drawLine((int) playerScreenPos.x(), (int) playerScreenPos.y(),
                (int) endPointL.x(), (int) endPointL.y());
        g2d.drawLine((int) playerScreenPos.x(), (int) playerScreenPos.y(),
                (int) endPointR.x(), (int) endPointR.y());
    }

    // For BSP debugging: draw vlines for segs
    public void drawVSegLines(Graphics2D g2d, int x1, int x2, int subSectorId) {
        random.setSeed(subSectorId);
        Color color = new Color(random.nextInt(156) + 100, random.nextInt(156) + 100, random.nextInt(156) + 100);
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(3));
        g2d.drawLine(x1, 0, x1, Constants.HEIGHT);
        g2d.drawLine(x2, 0, x2, Constants.HEIGHT);
    }

    // For BSP debugging: draw a specific seg on the 2D map
    public void drawSegOnMap(Graphics2D g2d, Seg seg) {
        if (seg.startVertexId < 0 || seg.startVertexId >= remappedVertexes.size() ||
                seg.endVertexId < 0 || seg.endVertexId >= remappedVertexes.size()) return;
        Point2D v1 = remappedVertexes.get(seg.startVertexId);
        Point2D v2 = remappedVertexes.get(seg.endVertexId);
        g2d.setColor(Color.GREEN);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawLine((int) v1.x(), (int) v1.y(), (int) v2.x(), (int) v2.y());
    }

    public void drawNode(Graphics2D g2d, int nodeId) {
        if (nodeId < 0 || nodeId >= wadDataService.nodes.size()) return;
        Node node = wadDataService.nodes.get(nodeId);

        // Draw partition line
        double x1p = remapX(node.xPartition);
        double y1p = remapY(node.yPartition);
        double x2p = remapX(node.xPartition + node.dxPartition);
        double y2p = remapY(node.yPartition + node.dyPartition);
        g2d.setColor(Color.BLUE);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawLine((int) x1p, (int) y1p, (int) x2p, (int) y2p);

        // Draw bounding boxes
        drawBBox(g2d, node.bbox.get("front"), Color.GREEN);
        drawBBox(g2d, node.bbox.get("back"), Color.MAGENTA); // Changed from red for better contrast
    }

    private void drawBBox(Graphics2D g2d, Node.BBox bbox, Color color) {
        double x = remapX(bbox.left);
        double y = remapY(bbox.top); // Top in world is smaller Y, but remapY inverts
        double w = remapX(bbox.right) - x;
        double h = remapY(bbox.bottom) - y; // Bottom in world is larger Y

        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(1));
        g2d.drawRect((int) x, (int) y, (int) w, (int) h);
    }
}