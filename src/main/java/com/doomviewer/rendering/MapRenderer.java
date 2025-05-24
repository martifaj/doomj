package com.doomviewer.rendering;

import com.doomviewer.misc.Constants;
import com.doomviewer.misc.math.Vector2D;
import com.doomviewer.game.Player;
import com.doomviewer.game.DoomEngine;
import com.doomviewer.wad.WADDataService;
import com.doomviewer.wad.datatypes.Linedef;
import com.doomviewer.wad.datatypes.Node;
import com.doomviewer.wad.datatypes.Seg;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MapRenderer {
    private DoomEngine engine;
    private WADDataService wadDataService;
    private List<Vector2D> remappedVertexes;
    private double xMin, xMax, yMin, yMax; // Original map bounds
    private Player player;
    private Random random = new Random();

    public MapRenderer(DoomEngine engine) {
        this.engine = engine;
        this.wadDataService = engine.getWadData();
        this.player = engine.getPlayer();

        calculateMapBounds();
        remapVertexes();
    }

    private void calculateMapBounds() {
        if (wadDataService.vertexes.isEmpty()) {
            xMin = yMin = -1; xMax = yMax = 1; // Default bounds
            return;
        }
        List<Double> xCoords = new ArrayList<>();
        List<Double> yCoords = new ArrayList<>();
        for (Vector2D v : wadDataService.vertexes) {
            xCoords.add(v.x);
            yCoords.add(v.y);
        }
        xMin = Collections.min(xCoords);
        xMax = Collections.max(xCoords);
        yMin = Collections.min(yCoords);
        yMax = Collections.max(yCoords);

        // Ensure bounds have some extent to prevent division by zero in remap
        if (xMin == xMax) xMax = xMin + 1;
        if (yMin == yMax) yMax = yMin + 1;
    }

    private void remapVertexes() {
        remappedVertexes = new ArrayList<>();
        for (Vector2D v : wadDataService.vertexes) {
            remappedVertexes.add(new Vector2D(remapX(v.x), remapY(v.y)));
        }
    }

    private double remapX(double worldX) {
        double margin = 30; // Margin from screen edge for map display
        double outMin = margin;
        double outMax = Constants.WIDTH - margin;
        // Clamp n to bounds to avoid extreme values if player goes off map for drawing map elements
        worldX = Math.max(xMin, Math.min(worldX, xMax));
        return (worldX - xMin) * (outMax - outMin) / (xMax - xMin) + outMin;
    }

    private double remapY(double worldY) {
        double margin = 30;
        double outMin = margin;
        double outMax = Constants.HEIGHT - margin;
        worldY = Math.max(yMin, Math.min(worldY, yMax));
        // Y is inverted for screen coordinates (0 at top)
        return Constants.HEIGHT - ((worldY - yMin) * (outMax - outMin) / (yMax - yMin) + outMin);
    }

    public void draw(Graphics2D g2d) {
        // Main draw call for the 2D map, if active
        // Example: draw all linedefs and player position
        // g2d.setColor(Color.DARK_GRAY);
        // g2d.fillRect(0,0, Settings.WIDTH, Settings.HEIGHT); // Clear background for map view

        drawLinedefs(g2d);
        drawPlayer(g2d);

        // Optionally draw nodes, segs etc.
        // if (engine.getBsp().getCurrentDebugNodeId() != -1) {
        //    drawNode(g2d, engine.getBsp().getCurrentDebugNodeId());
        // }
    }

    public void drawLinedefs(Graphics2D g2d) {
        g2d.setColor(Color.RED);
        g2d.setStroke(new BasicStroke(1));
        for (Linedef line : wadDataService.linedefs) {
            if (line.startVertexId < 0 || line.startVertexId >= remappedVertexes.size() ||
                    line.endVertexId < 0 || line.endVertexId >= remappedVertexes.size()) continue;

            Vector2D p1 = remappedVertexes.get(line.startVertexId);
            Vector2D p2 = remappedVertexes.get(line.endVertexId);
            g2d.drawLine((int) p1.x, (int) p1.y, (int) p2.x, (int) p2.y);
        }
    }

    public void drawPlayer(Graphics2D g2d) {
        double playerScreenX = remapX(player.pos.x);
        double playerScreenY = remapY(player.pos.y);

        g2d.setColor(Color.ORANGE);
        g2d.fillOval((int) (playerScreenX - 5), (int) (playerScreenY - 5), 10, 10);

        // Draw FOV lines
        double lineLength = 50; // Length of FOV lines on map
        double angleRad = Math.toRadians(-player.angle); // Negate for screen Y-down, math angle convention

        double angleL = Math.toRadians(-player.angle + Constants.H_FOV);
        double angleR = Math.toRadians(-player.angle - Constants.H_FOV);

        g2d.setColor(Color.YELLOW);
        g2d.drawLine((int)playerScreenX, (int)playerScreenY,
                (int)(playerScreenX + lineLength * Math.cos(angleL)),
                (int)(playerScreenY + lineLength * Math.sin(angleL)));
        g2d.drawLine((int)playerScreenX, (int)playerScreenY,
                (int)(playerScreenX + lineLength * Math.cos(angleR)),
                (int)(playerScreenY + lineLength * Math.sin(angleR)));
    }

    // For BSP debugging: draw vlines for segs
    public void drawVSegLines(Graphics2D g2d, int x1, int x2, int subSectorId) {
        random.setSeed(subSectorId);
        Color color = new Color(random.nextInt(156)+100, random.nextInt(156)+100, random.nextInt(156)+100);
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(3));
        g2d.drawLine(x1, 0, x1, Constants.HEIGHT);
        g2d.drawLine(x2, 0, x2, Constants.HEIGHT);
    }

    // For BSP debugging: draw a specific seg on the 2D map
    public void drawSegOnMap(Graphics2D g2d, Seg seg) {
        if (seg.startVertexId < 0 || seg.startVertexId >= remappedVertexes.size() ||
                seg.endVertexId < 0 || seg.endVertexId >= remappedVertexes.size()) return;
        Vector2D v1 = remappedVertexes.get(seg.startVertexId);
        Vector2D v2 = remappedVertexes.get(seg.endVertexId);
        g2d.setColor(Color.GREEN);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawLine((int)v1.x, (int)v1.y, (int)v2.x, (int)v2.y);
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
        g2d.drawLine((int)x1p, (int)y1p, (int)x2p, (int)y2p);

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
        g2d.drawRect((int)x, (int)y, (int)w, (int)h);
    }
}