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

    private final String wadPath;
    private final String mapName;

    private JFrame frame;
    private Thread gameThread;
    private boolean running = false;
    private final InputHandler inputHandler;

    // --- Double buffering for screen image ---
    private BufferedImage visibleScreenImage; // For paintComponent to display
    private BufferedImage renderScreenImage;  // For game logic to draw onto
    private int[] renderFramebuffer;    // Pixel data for renderScreenImage
    private final Object screenLock = new Object(); // For synchronizing access to visibleScreenImage
    // --- End double buffering fields ---

    private WADData wadData;
    private MapRenderer mapRenderer;
    private Player player;
    private BSP bsp;
    private SegHandler segHandler;
    private ViewRenderer viewRenderer;

    private long lastTime = System.nanoTime();
    private double deltaTime = 0;

    private boolean showMap = false;

    public DoomEngine(String wadPath, String mapName) {
        this.wadPath = wadPath;
        this.mapName = mapName;

        this.inputHandler = new InputHandler();
        setPreferredSize(new Dimension(Settings.WIDTH, Settings.HEIGHT));
        setFocusable(true);
        addKeyListener(inputHandler);

        // Initialize screen images for double buffering
        visibleScreenImage = new BufferedImage(Settings.WIDTH, Settings.HEIGHT, BufferedImage.TYPE_INT_ARGB);
        renderScreenImage = new BufferedImage(Settings.WIDTH, Settings.HEIGHT, BufferedImage.TYPE_INT_ARGB);
        renderFramebuffer = ((DataBufferInt) renderScreenImage.getRaster().getDataBuffer()).getData();

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
        bsp = new BSP(this);
        segHandler = new SegHandler(this);
        viewRenderer = new ViewRenderer(this);
        mapRenderer = new MapRenderer(this);

        System.out.println("DOOM Engine Initialized. Map: " + mapName);
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        try {
            onInit();
        } catch (IOException e) {
            e.printStackTrace();
            running = false;
            JOptionPane.showMessageDialog(null, "Failed to initialize Doom Engine: " + e.getMessage(), "Initialization Error", JOptionPane.ERROR_MESSAGE);
            return;
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
        Arrays.fill(renderFramebuffer, 0xFF000000); // Opaque black

        player.update();
        segHandler.update();
        bsp.update(); // This will trigger rendering into renderFramebuffer via SegHandler & ViewRenderer

        // After all rendering to renderScreenImage is complete, copy it to visibleScreenImage
        synchronized (screenLock) {
            Graphics g = visibleScreenImage.getGraphics();
            g.drawImage(renderScreenImage, 0, 0, null);
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
    }

    private boolean mapToggleDebounce = false;

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        synchronized (screenLock) {
            g2d.drawImage(visibleScreenImage, 0, 0, Settings.WIDTH, Settings.HEIGHT, null);
        }

        // Draw player sprite (weapon) overlay directly on top
        viewRenderer.drawSprite(g2d); // viewRenderer needs to be initialized before this is called

        if (showMap) {
            mapRenderer.draw(g2d); // mapRenderer needs to be initialized
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

    // This method now provides the framebuffer that game logic should draw onto
    public int[] getFramebuffer() {
        return renderFramebuffer;
    }

    public static void main(String[] args) {
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