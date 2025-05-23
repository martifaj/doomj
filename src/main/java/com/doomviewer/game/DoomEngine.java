package com.doomviewer.game;

import com.doomviewer.misc.InputHandler;
import com.doomviewer.misc.Constants;
import com.doomviewer.game.objects.GameDefinitions;
import com.doomviewer.rendering.FrameBuffer; // Import the new Framebuffer class
import com.doomviewer.rendering.MapRenderer;
import com.doomviewer.rendering.SegHandler;
import com.doomviewer.rendering.ViewRenderer;
import com.doomviewer.wad.WADData;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

public class DoomEngine extends JPanel implements Runnable {

    private final String wadPath;
    private final String mapName;

    private JFrame frame;
    private boolean running = false;
    private final InputHandler inputHandler;

    // --- Double buffering for screen image ---
    private final FrameBuffer visibleScreenBuffer;
    private final FrameBuffer renderScreenBuffer;
    
    // --- Depth buffer for sprite occlusion ---
    private final double[] depthBuffer; // Depth value for each pixel [WIDTH * HEIGHT]
    private final Object screenLock = new Object(); // For synchronizing access to visibleScreenImage
    // --- End double buffering fields ---

    private WADData wadData;
    private MapRenderer mapRenderer;
    private Player player;
    private DoorManager doorManager;
    private BSP bsp;
    private SegHandler segHandler;
    private ViewRenderer viewRenderer;
    private ObjectManager objectManager;

    private long lastTime = System.nanoTime();
    private double deltaTime = 0;

    private boolean showMap = false;
    private int currentSkillLevel = 1; // Default to Skill 1 (I'm Too Young To Die - Easy)

    public DoomEngine(String wadPath, String mapName) {
        this.wadPath = wadPath;
        this.mapName = mapName;

        this.inputHandler = new InputHandler();
        setPreferredSize(new Dimension(Constants.WIDTH, Constants.HEIGHT));
        setFocusable(true);
        addKeyListener(inputHandler);

        // Initialize screen images for double buffering
        visibleScreenBuffer = new FrameBuffer(Constants.WIDTH, Constants.HEIGHT);
        renderScreenBuffer = new FrameBuffer(Constants.WIDTH, Constants.HEIGHT);
        
        // Initialize depth buffer
        depthBuffer = new double[Constants.WIDTH * Constants.HEIGHT];

    }

    private void onInit() throws IOException {
        wadData = new WADData(wadPath, mapName); // wadData needs to be initialized first

        // Player needs to be initialized before ObjectManager, as ObjectManager creates MapObjects
        // which might depend on the player (e.g., for initial floor height or as a target).
        com.doomviewer.wad.datatypes.Thing playerThing = null;
        if (wadData.things != null && !wadData.things.isEmpty()) {
            playerThing = wadData.things.stream()
                .filter(t -> t.type == 1) // Assuming type 1 is a player start
                .findFirst()
                .orElseGet(() -> wadData.things.get(0));
        }
        if (playerThing == null) {
            throw new IOException("Player Thing not found in WAD data for map " + mapName);
        }
        // Temporarily get GameDefinitions for Player. ObjectManager will create its own instance.
        // This is a bit of a workaround. Ideally, GameDefinitions is a singleton or passed around.
        GameDefinitions tempGameDefs = new GameDefinitions(); // Create a temporary instance for the player
        player = new Player(this, playerThing, tempGameDefs, wadData.assetData);

        // ObjectManager needs WADData and creates its own GameDefinitions
        objectManager = new ObjectManager(this, wadData);
        // Now that objectManager is created, if Player needs the final GameDefinitions, update it.
        // However, Player constructor already took one. If it stores it, it might be the temp one.
        // For now, let's assume the temp one is sufficient for Player's immediate needs.

        // Initialize door manager
        doorManager = new DoorManager(wadData, this);

        bsp = new BSP(this);
        segHandler = new SegHandler(this);
        viewRenderer = new ViewRenderer(this);
        mapRenderer = new MapRenderer(this);

    }

    public synchronized void start() {
        if (running) return;
        running = true;
        Thread gameThread; // Declared as local variable
        try {
            onInit();
        } catch (IOException e) {
            running = false;
            JOptionPane.showMessageDialog(null, "Failed to initialize Doom Engine: " + e.getMessage(), "Initialization Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        gameThread = new Thread(this);
        gameThread.start();
    }

    @Override
    public void run() {
        final double targetFps = 60.0;
        final double nsPerFrame = 1_000_000_000.0 / targetFps;
        long lastFpsTime = System.nanoTime();
        int frames = 0;

        while (running) {
            long now = System.nanoTime();
            deltaTime = (now - lastTime) / 1_000_000.0;
            lastTime = now;

            update();
            repaint();

            frames++;
            if (System.nanoTime() - lastFpsTime >= 1_000_000_000) {
                frame.setTitle("DOOM Level Viewer - FPS: " + frames + " - Map: " + mapName);
                frames = 0;
                lastFpsTime = System.nanoTime();
            }

            long frameTime = System.nanoTime() - now;
            if (frameTime < nsPerFrame) {
                try {
                    Thread.sleep((long) ((nsPerFrame - frameTime) / 1_000_000.0));
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
        // Ensure the application exits cleanly if the loop terminates
        if (frame != null) {
            frame.dispose(); // Close the window
        }
        System.exit(0); // Terminate the application
    }

    private void update() {
        // Clear the render framebuffer (for renderScreenImage)
        renderScreenBuffer.clear(0xFF000000); // Opaque black
        
        // Clear depth buffer (initialize to infinity = no geometry drawn yet)
        java.util.Arrays.fill(depthBuffer, Double.MAX_VALUE);

        player.update(this);
        segHandler.update();
        bsp.update(); // This will trigger rendering into renderFramebuffer via SegHandler & ViewRenderer
        objectManager.update();
        doorManager.update(player);
        
        // Draw world sprites (enemies, etc.) to the render buffer with occlusion
        if (viewRenderer != null) {
            // Use the new occlusion-aware sprite rendering
            viewRenderer.drawWorldSpritesWithOcclusion(renderScreenBuffer.getPixelData(), objectManager.getVisibleSortedMapObjects());
        }

        // After all rendering to renderScreenImage is complete, copy it to visibleScreenImage
        synchronized (screenLock) {
            Graphics g = visibleScreenBuffer.getImageBuffer().getGraphics();
            g.drawImage(renderScreenBuffer.getImageBuffer(), 0, 0, null);
            g.dispose();
        }

        if (inputHandler.isKeyPressed(java.awt.event.KeyEvent.VK_M) && !mapToggleDebounce) {
            showMap = !showMap;
            mapToggleDebounce = true;
        }
        if (!inputHandler.isKeyPressed(java.awt.event.KeyEvent.VK_M)) {
            mapToggleDebounce = false;
        }
        if (inputHandler.isKeyPressed(java.awt.event.KeyEvent.VK_ESCAPE)) {
            running = false;
        }
        
        // Test sound with T key
        if (inputHandler.isKeyPressed(java.awt.event.KeyEvent.VK_T) && !testSoundDebounce) {
            com.doomviewer.audio.SoundEngine.getInstance().testSound("DSPISTOL");
            testSoundDebounce = true;
        }
        if (!inputHandler.isKeyPressed(java.awt.event.KeyEvent.VK_T)) {
            testSoundDebounce = false;
        }
    }

    private boolean mapToggleDebounce = false;
    private boolean testSoundDebounce = false;

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        synchronized (screenLock) {
            g2d.drawImage(visibleScreenBuffer.getImageBuffer(), 0, 0, Constants.WIDTH, Constants.HEIGHT, null);
        }

        // Draw player sprite (weapon) overlay directly on top
        if (viewRenderer != null) { // Ensure viewRenderer is initialized
            viewRenderer.drawSprite(g2d);
        }
        
        // Draw HUD overlay
        if (player != null && player.getHUD() != null) {
            player.getHUD().renderHUD(g2d);
        }

        // Optional: Draw 2D map overlay
        if (showMap) {
            if (mapRenderer != null) { // Ensure mapRenderer is initialized
                mapRenderer.draw(g2d);
            }
        }
        // g2d.dispose(); // Do not dispose g here, it's managed by Swing
    }

    public WADData getWadData() { return wadData; }
    public Player getPlayer() { return player; }
    public BSP getBsp() { return bsp; }
    public SegHandler getSegHandler() { return segHandler; }
    public ViewRenderer getViewRenderer() { return viewRenderer; }
    public InputHandler getInputHandler() { return inputHandler; }
    public double getDeltaTime() { return deltaTime; }
    public int getCurrentSkillLevel() { return currentSkillLevel; } // Getter for skill level

    public ObjectManager getObjectManager() {
        return objectManager;
    }
    
    public DoorManager getDoorManager() {
        return doorManager;
    }

    // This method now provides the framebuffer that game logic should draw onto
    public int[] getFramebuffer() {
        return renderScreenBuffer.getPixelData();
    }
    
    // Depth buffer for sprite occlusion
    public double[] getDepthBuffer() {
        return depthBuffer;
    }

    public static void main(String[] args) {
        // Configure logging to reduce spam
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.ALL);
        
        // Set specific loggers for user feedback  
        Logger.getLogger("com.doomviewer.game.Door").setLevel(Level.INFO);
        Logger.getLogger("com.doomviewer.game.Player").setLevel(Level.INFO);
        Logger.getLogger("com.doomviewer.game.DoorManager").setLevel(Level.INFO);
        Logger.getLogger("com.doomviewer.game.BSP").setLevel(Level.INFO);
        
        String wadFilePath = "DOOM1.WAD";
        String mapToLoad = "E1M1";

        if (args.length > 0) wadFilePath = args[0];
        if (args.length > 1) mapToLoad = args[1];

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
            engine.requestFocusInWindow(); // Crucial for KeyListener to work immediately
            engine.start();
        });
    }
}

