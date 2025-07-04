package com.doomengine.game;

import com.doomengine.config.GameConfiguration;
import com.doomengine.game.objects.GameDefinitions;
import com.doomengine.misc.Constants;
import com.doomengine.misc.InputHandler;
import com.doomengine.rendering.*;
import com.doomengine.rendering.bsp.BSP;
import com.doomengine.rendering.bsp.SegHandler;
import com.doomengine.services.AudioService;
import com.doomengine.services.CollisionService;
import com.doomengine.services.GameEngineTmp;
import com.doomengine.services.InputService;
import com.doomengine.wad.WADDataService;
import com.doomengine.wad.datatypes.Thing;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DoomEngine extends JPanel implements Runnable, GameEngineTmp {

    private final String wadPath;
    private final String mapName;
    private final GameConfiguration config;
    private final AudioService audioService;
    private final InputService inputService;
    private CollisionService collisionService;

    private JFrame frame;
    private boolean running = false;

    // --- Double buffering for screen image ---
    private final FrameBuffer visibleScreenBuffer;
    private final FrameBuffer renderScreenBuffer;

    // --- Depth buffer for sprite occlusion ---
    private final double[] depthBuffer; // Depth value for each pixel [WIDTH * HEIGHT]
    private final Object screenLock = new Object(); // For synchronizing access to visibleScreenImage
    // --- End double buffering fields ---

    private WADDataService wadDataService;
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
    private final int currentSkillLevel = 1; // Default to Skill 1 (I'm Too Young To Die - Easy)

    public DoomEngine(String wadPath, String mapName, GameConfiguration config,
                      AudioService audioService, InputService inputService) {
        this.wadPath = wadPath;
        this.mapName = mapName;
        this.config = config;
        this.audioService = audioService;
        this.inputService = inputService;

        setPreferredSize(new Dimension(config.getWidth(), config.getHeight()));
        setFocusable(true);
        if (inputService instanceof InputHandler) {
            addKeyListener((InputHandler) inputService);
        }

        // Initialize screen images for double buffering
        visibleScreenBuffer = new FrameBuffer(config.getWidth(), config.getHeight());
        renderScreenBuffer = new FrameBuffer(config.getWidth(), config.getHeight());

        // Initialize depth buffer
        depthBuffer = new double[config.getWidth() * config.getHeight()];

    }

    private void onInit() throws IOException {
        wadDataService = new WADDataService(wadPath, mapName); // wadData needs to be initialized first

        // Player needs to be initialized before ObjectManager, as ObjectManager creates MapObjects
        // which might depend on the player (e.g., for initial floor height or as a target).
        Thing playerThing = null;
        if (wadDataService.things != null && !wadDataService.things.isEmpty()) {
            playerThing = wadDataService.things.stream()
                    .filter(t -> t.type == 1) // Assuming type 1 is a player start
                    .findFirst()
                    .orElseGet(() -> wadDataService.things.get(0));
        }
        if (playerThing == null) {
            throw new IOException("Player Thing not found in WAD data for map " + mapName);
        }
        // Create BSP first since it's needed as collision service
        bsp = new BSP(this);
        this.collisionService = bsp; // Set BSP as collision service now that wadData is available

        // Create door manager (no dependencies on engine)
        doorManager = new DoorManager(wadDataService, collisionService);

        bsp.setDoorService(doorManager);

        // Create shared GameDefinitions instance
        GameDefinitions gameDefinitions = new GameDefinitions();

        // Create ObjectManager with injected dependencies (without player dependency)
        objectManager = new ObjectManager(this, collisionService, wadDataService, audioService);

        // Create Player with injected dependencies
        player = new Player(playerThing, gameDefinitions, wadDataService.assetData,
                config, collisionService, audioService, inputService, objectManager, doorManager, this);
        
        // Initialize map objects now that player is created
        objectManager.initializeMapObjects(player);
        
        // Inject player into DoorManager
        doorManager.setPlayer(player);
        
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
                frame.setTitle("DOOM Engine - FPS: " + frames + " - Map: " + mapName);
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

    public void setCollisionService(CollisionService collisionService) {
        this.collisionService = collisionService;
    }

    private void update() {
        // Clear the render framebuffer (for renderScreenImage)
        renderScreenBuffer.clear(0xFF000000); // Opaque black

        // Clear depth buffer (initialize to infinity = no geometry drawn yet)
        java.util.Arrays.fill(depthBuffer, Double.MAX_VALUE);

        player.update();
        segHandler.update();
        bsp.update(); // This will trigger rendering into renderFramebuffer via SegHandler & ViewRenderer
        objectManager.update();
        doorManager.update();

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

        if (inputService.isKeyPressed(java.awt.event.KeyEvent.VK_M) && !mapToggleDebounce) {
            showMap = !showMap;
            mapToggleDebounce = true;
        }
        if (!inputService.isKeyPressed(java.awt.event.KeyEvent.VK_M)) {
            mapToggleDebounce = false;
        }
        if (inputService.isKeyPressed(java.awt.event.KeyEvent.VK_ESCAPE)) {
            running = false;
        }

        // Test sound with T key
        if (inputService.isKeyPressed(java.awt.event.KeyEvent.VK_T) && !testSoundDebounce) {
            audioService.playSound("DSPISTOL");
            testSoundDebounce = true;
        }
        if (!inputService.isKeyPressed(java.awt.event.KeyEvent.VK_T)) {
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

    public WADDataService getWadData() {
        return wadDataService;
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

    public double getDeltaTime() {
        return deltaTime;
    }

    public int getCurrentSkillLevel() {
        return currentSkillLevel;
    } // Getter for skill level

    @Override
    public boolean isRunning() {
        return running;
    }

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

    private static void printUsage() {
        System.out.println("DOOM Engine");
        System.out.println("Usage: java -jar doomj.jar [OPTIONS] [WAD_FILE] [MAP_NAME]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  WAD_FILE    Path to DOOM WAD file (default: DOOM1.WAD)");
        System.out.println("  MAP_NAME    Map to load (default: E1M1)");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --nosound, -ns    Disable sound effects");
        System.out.println("  --help, -h        Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar doomj.jar DOOM2.WAD MAP01");
        System.out.println("  java -jar doomj.jar --nosound DOOM1.WAD E1M2");
        System.out.println("  java -jar doomj.jar -ns");
    }

    public static void main(String[] args) {
        // Configure logging to reduce spam
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.WARNING);

        // Parse command line arguments
        String wadFilePath = "DOOM1.WAD";
        String mapToLoad = "E1M1";
        boolean soundEnabled = true;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--nosound") || arg.equals("-ns")) {
                soundEnabled = false;
            } else if (arg.equals("--help") || arg.equals("-h")) {
                printUsage();
                return;
            } else if (i == 0 && !arg.startsWith("-")) {
                wadFilePath = arg;
            } else if (i == 1 && !arg.startsWith("-")) {
                mapToLoad = arg;
            } else if (arg.startsWith("-") && !arg.equals("--nosound") && !arg.equals("-ns")) {
                System.err.println("Unknown option: " + arg);
                printUsage();
                return;
            }
        }

        // Configure sound engine
        com.doomengine.audio.SoundEngine.getInstance().setEnabled(soundEnabled);
        if (!soundEnabled) {
            System.out.println("Sound disabled");
        }

        final String finalWadPath = wadFilePath;
        final String finalMapName = mapToLoad;
        final boolean finalSoundEnabled = soundEnabled;

        SwingUtilities.invokeLater(() -> {
            // Create dependencies
            DoomEngine engine = getDoomEngine(finalSoundEnabled, finalWadPath, finalMapName);

            // Set BSP as collision service - BSP will be created in onInit()
            engine.setCollisionService(null); // Temporarily null, will be set in onInit()

            engine.frame = new JFrame("DOOM Engine");
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

    private static DoomEngine getDoomEngine(boolean finalSoundEnabled, String finalWadPath, String finalMapName) {
        GameConfiguration config = new GameConfiguration(finalSoundEnabled);
        AudioService audioService = new com.doomengine.audio.SoundEngine();
        audioService.setEnabled(finalSoundEnabled);
        InputService inputService = new InputHandler();

        // We need to create the engine first, then pass it to BSP
        // This is a circular dependency we'll need to handle
        return new DoomEngine(finalWadPath, finalMapName, config, audioService, inputService);
    }
}

