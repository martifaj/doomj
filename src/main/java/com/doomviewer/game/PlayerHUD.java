package com.doomviewer.game;

import com.doomviewer.audio.SoundEngine;
import com.doomviewer.misc.Constants;
import com.doomviewer.wad.assets.AssetData;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.logging.Logger;

public class PlayerHUD {
    private static final Logger LOGGER = Logger.getLogger(PlayerHUD.class.getName());

    private Player player;
    private Font hudFont;
    private Font largeFont;
    private AssetData assetData;
    
    // Face state tracking
    private long lastDamageTime = 0;
    private long lastAttackTime = 0;
    private String currentFaceSprite = "STFST00"; // DOOM guy face
    private int faceDirection = 0; // 0 = center, 1 = right, 2 = left
    private long lastFaceChangeTime = 0;
    private java.util.Random faceRandom = new java.util.Random();
    
    // Pain state duration
    private static final long PAIN_DURATION = 1000; // 1 second
    private static final long ATTACK_DURATION = 500; // 0.5 seconds
    private static final long FACE_CHANGE_INTERVAL = 1500; // Change face direction every 1.5 seconds
    
    public PlayerHUD(Player player) {
        this.player = player;
        this.hudFont = new Font("Arial", Font.BOLD, 16);
        this.largeFont = new Font("Arial", Font.BOLD, 24);
        
        // AssetData will be passed when needed
        this.assetData = null;
    }
    
    public void setAssetData(AssetData assetData) {
        this.assetData = assetData;
        
        // Debug: Check if AssetData and sprites are available
        if (assetData != null) {
            LOGGER.info("PlayerHUD: AssetData set successfully");
            if (assetData.sprites != null) {
                LOGGER.info("PlayerHUD: Sprites map available with " + assetData.sprites.size() + " sprites");
                
                // Debug: Check specifically for face sprites
                boolean foundFaceSprites = false;
                for (String spriteName : assetData.sprites.keySet()) {
                    if (spriteName.startsWith("STF")) {
                        LOGGER.info("PlayerHUD: Found face sprite: " + spriteName);
                        foundFaceSprites = true;
                    }
                }
                
                if (!foundFaceSprites) {
                    LOGGER.warning("PlayerHUD: WARNING - No face sprites found starting with 'STF'");
                }
            } else {
                LOGGER.warning("PlayerHUD: WARNING - Sprites map is null");
            }
        } else {
            LOGGER.warning("PlayerHUD: WARNING - AssetData is null");
        }
    }
    
    public void renderHUD(Graphics2D g2d) {
        // Set antialiasing for better text rendering
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Background panel for HUD
        drawHUDBackground(g2d);
        
        // Health display
        drawHealth(g2d);
        
        // Armor display
        drawArmor(g2d);
        
        // Ammo display
        drawAmmo(g2d);
        
        // Face/status display
        drawPlayerFace(g2d);
    }
    
    private void drawHUDBackground(Graphics2D g2d) {
        // Draw a semi-transparent background bar at the bottom
        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.fillRect(0, Constants.HEIGHT - 60, Constants.WIDTH, 60);
        
        // Draw border
        g2d.setColor(Color.GRAY);
        g2d.drawLine(0, Constants.HEIGHT - 60, Constants.WIDTH, Constants.HEIGHT - 60);
    }
    
    private void drawHealth(Graphics2D g2d) {
        int health = Math.max(0, player.health);
        
        // Health text
        g2d.setFont(largeFont);
        g2d.setColor(health < 25 ? Color.RED : health < 50 ? Color.YELLOW : Color.GREEN);
        g2d.drawString("HEALTH", 20, Constants.HEIGHT - 40);
        
        g2d.setFont(hudFont);
        g2d.setColor(Color.WHITE);
        g2d.drawString(health + "%", 20, Constants.HEIGHT - 20);
        
        // Health bar
        int barWidth = 100;
        int barHeight = 8;
        int barX = 120;
        int barY = Constants.HEIGHT - 30;
        
        // Background
        g2d.setColor(Color.DARK_GRAY);
        g2d.fillRect(barX, barY, barWidth, barHeight);
        
        // Health bar fill
        g2d.setColor(health < 25 ? Color.RED : health < 50 ? Color.YELLOW : Color.GREEN);
        int fillWidth = (int) ((health / 100.0) * barWidth);
        g2d.fillRect(barX, barY, fillWidth, barHeight);
        
        // Border
        g2d.setColor(Color.WHITE);
        g2d.drawRect(barX, barY, barWidth, barHeight);
    }
    
    private void drawArmor(Graphics2D g2d) {
        int armor = Math.max(0, player.getArmor());
        int maxArmor = player.getMaxArmor();
        
        // Armor text
        g2d.setFont(largeFont);
        g2d.setColor(armor == 0 ? Color.GRAY : Color.BLUE);
        g2d.drawString("ARMOR", 240, Constants.HEIGHT - 40);
        
        g2d.setFont(hudFont);
        g2d.setColor(Color.WHITE);
        g2d.drawString(armor + "/" + maxArmor, 240, Constants.HEIGHT - 20);
        
        // Armor bar
        int barX = 340;
        int barY = Constants.HEIGHT - 30;
        int barWidth = 80;
        int barHeight = 8;
        
        // Background
        g2d.setColor(Color.DARK_GRAY);
        g2d.fillRect(barX, barY, barWidth, barHeight);
        
        // Armor level
        if (armor > 0) {
            g2d.setColor(armor > 100 ? Color.CYAN : Color.BLUE); // Mega armor vs regular armor
            int fillWidth = (int) ((armor / (double)maxArmor) * barWidth);
            g2d.fillRect(barX, barY, fillWidth, barHeight);
        }
        
        // Border
        g2d.setColor(Color.WHITE);
        g2d.drawRect(barX, barY, barWidth, barHeight);
    }
    
    private void drawAmmo(Graphics2D g2d) {
        // Get current weapon's ammo
        WeaponType currentWeapon = player.getCurrentWeapon();
        AmmoType ammoType = getAmmoTypeForWeapon(currentWeapon);
        int currentAmmo = player.getAmmo(ammoType);
        int maxAmmo = player.getMaxAmmoCapacity(ammoType);
        
        // Ammo text
        g2d.setFont(largeFont);
        g2d.setColor(currentAmmo == 0 ? Color.RED : Color.CYAN);
        g2d.drawString("AMMO", Constants.WIDTH - 150, Constants.HEIGHT - 35);
        
        g2d.setFont(hudFont);
        g2d.setColor(Color.WHITE);
        g2d.drawString(currentAmmo + "/" + maxAmmo, Constants.WIDTH - 150, Constants.HEIGHT - 15);
    }
    
    
    private void drawPlayerFace(Graphics2D g2d) {
        updateFaceState();
        
        // Position for the face (center of HUD area)
        int faceX = Constants.H_WIDTH - 16; // Center horizontally (assuming 32px face width)
        int faceY = Constants.HEIGHT - 65;  // Higher up to avoid cutoff
        
        // Check if we have AssetData and sprites available
        if (assetData != null && assetData.sprites != null) {
            BufferedImage faceImage = assetData.sprites.get(currentFaceSprite);
            
            if (faceImage != null) {
                // Draw the DOOM guy face sprite
                g2d.drawImage(faceImage, faceX, faceY, null);
            }
        }
    }
    
    private void updateFaceState() {
        long currentTime = System.currentTimeMillis();
        int health = player.health;
        
        // Update face direction randomly over time
        updateFaceDirection(currentTime);
        
        // Check for recent damage (pain state)
        if (currentTime - lastDamageTime < PAIN_DURATION) {
            currentFaceSprite = getFacePainSprite(health);
            return;
        }
        
        // Check for recent attack (firing state)
        if (currentTime - lastAttackTime < ATTACK_DURATION && player.isAttackDown()) {
            currentFaceSprite = getFaceAttackSprite(health);
            return;
        }
        
        // Normal state based on health and direction
        currentFaceSprite = getFaceNormalSprite(health);
    }
    
    private void updateFaceDirection(long currentTime) {
        // Change face direction randomly at intervals (like original DOOM)
        if (currentTime - lastFaceChangeTime > FACE_CHANGE_INTERVAL) {
            // Random face direction with more frequent center position
            int rand = faceRandom.nextInt(100);
            if (rand < 20) {
                faceDirection = 1; // Look right (20% chance)
            } else if (rand < 40) {
                faceDirection = 2; // Look left (20% chance)
            } else {
                faceDirection = 0; // Look straight (60% chance)
            }
            
            // Set next change time with some randomness (1-3 seconds)
            long randomInterval = FACE_CHANGE_INTERVAL + faceRandom.nextInt(1500);
            lastFaceChangeTime = currentTime + randomInterval - FACE_CHANGE_INTERVAL;
        }
    }
    
    private String getFaceNormalSprite(int health) {
        // DOOM face sprites based on health percentage and direction
        String baseSprite;
        if (health >= 80) {
            baseSprite = "STFST0"; // Healthy - normal face
        } else if (health >= 60) {
            baseSprite = "STFST1"; // Slightly hurt  
        } else if (health >= 40) {
            baseSprite = "STFST2"; // Hurt
        } else if (health >= 20) {
            baseSprite = "STFST3"; // Badly hurt
        } else {
            baseSprite = "STFST4"; // Near death
        }
        
        // Add direction suffix: 0=center, 1=right, 2=left
        return baseSprite + faceDirection;
    }
    
    private String getFacePainSprite(int health) {
        // Pain sprites - OUCH face (pain faces don't have direction variants in original DOOM)
        if (health >= 40) {
            return "STFOUCH0"; // Normal pain
        } else if (health >= 20) {
            return "STFOUCH2"; // Hurt pain
        } else {
            return "STFOUCH4"; // Critical pain
        }
    }
    
    private String getFaceAttackSprite(int health) {
        // Attack/firing sprites (evil grin faces don't have direction variants)
        if (health >= 40) {
            return "STFEVL0"; // Evil grin while firing
        } else if (health >= 20) {
            return "STFEVL2"; // Evil grin while hurt
        } else {
            return "STFKILL0"; // Rambo face while critical
        }
    }
    
    
    // Call this when player takes damage
    public void onPlayerDamage() {
        lastDamageTime = System.currentTimeMillis();
    }
    
    // Call this when player attacks
    public void onPlayerAttack() {
        lastAttackTime = System.currentTimeMillis();
    }
    
    private AmmoType getAmmoTypeForWeapon(WeaponType weapon) {
        switch (weapon) {
            case PISTOL:
            case CHAINGUN:
                return AmmoType.BULLETS;
            case SHOTGUN:
                return AmmoType.SHELLS;
            case ROCKET_LAUNCHER:
                return AmmoType.ROCKETS;
            case PLASMA_RIFLE:
            case BFG:
                return AmmoType.CELLS;
            default:
                return AmmoType.BULLETS;
        }
    }
}