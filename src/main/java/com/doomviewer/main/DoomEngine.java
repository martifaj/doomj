package com.doomviewer.main;

import com.doomviewer.core.InputHandler;
import com.doomviewer.core.Settings;
import com.doomviewer.game.BSP;
import com.doomviewer.game.Player;
import com.doomviewer.rendering.MapRenderer;
import com.doomviewer.rendering.SegHandler;
import com.doomviewer.rendering.ViewRenderer;
import com.doomviewer.wad.WADData;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.util.Arrays;

public class DoomEngine extends JPanel implements Runnable {

    private final String wadPath; // e.g., "wad/DOOM1.WAD"
    private final String mapName; // e.g., "E1M1"

    private JFrame frame;
    private Thread gameThread;
    private boolean running = false;
    private final InputHandler inputHandler;

    private BufferedImage screenImage; // The image we draw the 3D view into
    private int[] screenPixels;    // Pixel data for screenImage

    private WADData wadData;
    private MapRenderer mapRenderer; // Optional 2D map renderer
    private Player player;
    private BSP bsp;
    private SegHandler segHandler;
    private ViewRenderer viewRenderer;

    private long lastTime = System.nanoTime();
    private double deltaTime = 0; // in milliseconds

    private boolean showMap = false; // Toggle for 2D map overlay

    public DoomEngine(String wadPath, String mapName) {
        this.wadPath = wadPath;
        this.mapName = mapName;

        this.inputHandler = new InputHandler();
        setPreferredSize(new Dimension(Settings.WIDTH, Settings.HEIGHT));
        setFocusable(true);
        addKeyListener(inputHandler);

        // Screen image for 3D rendering
        screenImage = new BufferedImage(Settings.WIDTH, Settings.HEIGHT, BufferedImage.TYPE_INT_ARGB);
        screenPixels = ((DataBufferInt) screenImage.getRaster().getDataBuffer()).getData();

        addKeyListener(inputHandler);
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                System.out.println("DoomEngine panel GAINED focus");
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                System.out.println("DoomEngine panel LOST focus. Opposite component: " + e.getOppositeComponent());
            }
        });
    }

    private void onInit() throws IOException {
        wadData = new WADData(wadPath, mapName);
        player = new Player(this);
        bsp = new BSP(this); // BSP needs player, so player first
        segHandler = new SegHandler(this); // SegHandler needs player and WADData
        viewRenderer = new ViewRenderer(this); // ViewRenderer needs player, segHandler, WADData
        mapRenderer = new MapRenderer(this); // MapRenderer needs player and WADData

        System.out.println("DOOM Engine Initialized. Map: " + mapName);
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        try {
            onInit(); // Initialize game components that might throw IOExceptions
        } catch (IOException e) {
            e.printStackTrace();
            running = false;
            // Optionally show an error dialog to the user
            JOptionPane.showMessageDialog(null, "Failed to initialize Doom Engine: " + e.getMessage(), "Initialization Error", JOptionPane.ERROR_MESSAGE);
            return; // Don't start thread if init fails
        }
        gameThread = new Thread(this);
        gameThread.start();
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        try {
            gameThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        final double targetFps = 60.0;
        final double nsPerFrame = 1_000_000_000.0 / targetFps;
        long lastFpsTime = System.nanoTime();
        int frames = 0;

        while (running) {
            long now = System.nanoTime();
            deltaTime = (now - lastTime) / 1_000_000.0; // Delta time in milliseconds
            lastTime = now;

            update();
            repaint(); // This will call paintComponent

            frames++;
            if (System.nanoTime() - lastFpsTime >= 1_000_000_000) {
                frame.setTitle("DOOM Level Viewer - FPS: " + frames + " - Map: " + mapName);
                frames = 0;
                lastFpsTime = System.nanoTime();
            }

            // Frame limiting
            long frameTime = System.nanoTime() - now;
            if (frameTime < nsPerFrame) {
                try {
                    Thread.sleep((long) ((nsPerFrame - frameTime) / 1_000_000.0));
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
    }

    private void update() {
        // Clear the 3D framebuffer before rendering the next frame
        Arrays.fill(screenPixels, 0xFF000000);
        player.update();
        segHandler.update(); // Must be before BSP to init clip arrays
        bsp.update();        // BSP calls SegHandler.classifySegment which uses ViewRenderer

        // Check for map toggle
        if (inputHandler.isKeyPressed(java.awt.event.KeyEvent.VK_M) && !mapToggleDebounce) {
            showMap = !showMap;
            mapToggleDebounce = true;
        }
        if (!inputHandler.isKeyPressed(java.awt.event.KeyEvent.VK_M)) {
            mapToggleDebounce = false;
        }
        if (inputHandler.isKeyPressed(java.awt.event.KeyEvent.VK_ESCAPE)) {
            running = false; // Allow Esc to quit
        }
    }

    private boolean mapToggleDebounce = false;


    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // The framebuffer is cleared before rendering in update(), so skip clearing here

        // The ViewRenderer's drawFlat/drawWallColumn methods (called via SegHandler)
        // will directly modify `this.screenPixels`.

        // After BSP traversal and SegHandler rendering, screenImage contains the 3D view.
        //
        g2d.drawImage(screenImage, 0, 0, Settings.WIDTH, Settings.HEIGHT, null);

        // Draw player sprite (weapon) overlay
        viewRenderer.drawSprite(g2d);

        // Optional: Draw 2D map overlay
        if (showMap) {
            mapRenderer.draw(g2d);
        }

        // Optional: Draw palette for debugging
        // viewRenderer.drawPalette(g2d);

        g2d.dispose();
    }

    // Getters for other components to access engine parts
    public WADData getWadData() {
        return wadData;
    }

    public Player getPlayer() {
        return player;
    }

    public BSP getBsp() {
        return bsp;
    }

    public SegHandler getSegHandler() {
        return segHandler;
    }

    public ViewRenderer getViewRenderer() {
        return viewRenderer;
    }

    public InputHandler getInputHandler() {
        return inputHandler;
    }

    public int[] getFramebuffer() {
        return screenPixels;
    }

    public double getDeltaTime() {
        return deltaTime;
    }


    public static void main(String[] args) {
        // Ensure WAD path and map name are correct
        String wadFilePath = "DOOM1.WAD"; // Default, change if needed
        String mapToLoad = "E1M1";       // Default, change if needed

        if (args.length > 0) {
            wadFilePath = args[0];
        }
        if (args.length > 1) {
            mapToLoad = args[1];
        }

        final String finalWadPath = wadFilePath;
        final String finalMapName = mapToLoad;

        SwingUtilities.invokeLater(() -> {
            DoomEngine engine = new DoomEngine(finalWadPath, finalMapName);
            engine.frame = new JFrame("DOOM Level Viewer");
            engine.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            engine.frame.setResizable(false);
            engine.frame.add(engine);
            engine.frame.pack();
            engine.frame.setLocationRelativeTo(null);
            engine.frame.setVisible(true);
            engine.requestFocusInWindow();
            engine.start(); // Start the game loop
        });
    }
}