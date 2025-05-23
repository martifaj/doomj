package com.doomviewer.game;

import com.doomviewer.misc.Constants;
import java.awt.*;
import java.awt.image.BufferedImage;

public class PlayerHUD {
    private Player player;
    private Font hudFont;
    private Font largeFont;
    
    public PlayerHUD(Player player) {
        this.player = player;
        this.hudFont = new Font("Arial", Font.BOLD, 16);
        this.largeFont = new Font("Arial", Font.BOLD, 24);
    }
    
    public void renderHUD(Graphics2D g2d) {
        // Set antialiasing for better text rendering
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Background panel for HUD
        drawHUDBackground(g2d);
        
        // Health display
        drawHealth(g2d);
        
        // Ammo display
        drawAmmo(g2d);
        
        // Weapon display
        drawWeapon(g2d);
        
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
        g2d.drawString("HEALTH", 20, Constants.HEIGHT - 35);
        
        g2d.setFont(hudFont);
        g2d.setColor(Color.WHITE);
        g2d.drawString(health + "%", 20, Constants.HEIGHT - 15);
        
        // Health bar
        int barWidth = 100;
        int barHeight = 8;
        int barX = 120;
        int barY = Constants.HEIGHT - 25;
        
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
    
    private void drawAmmo(Graphics2D g2d) {
        // Get current weapon's ammo
        WeaponType currentWeapon = player.getCurrentWeapon();
        AmmoType ammoType = getAmmoTypeForWeapon(currentWeapon);
        int currentAmmo = player.getAmmo(ammoType);
        int maxAmmo = ammoType.maxAmmo;
        
        // Ammo text
        g2d.setFont(largeFont);
        g2d.setColor(currentAmmo == 0 ? Color.RED : Color.CYAN);
        g2d.drawString("AMMO", Constants.WIDTH - 150, Constants.HEIGHT - 35);
        
        g2d.setFont(hudFont);
        g2d.setColor(Color.WHITE);
        g2d.drawString(currentAmmo + "/" + maxAmmo, Constants.WIDTH - 150, Constants.HEIGHT - 15);
    }
    
    private void drawWeapon(Graphics2D g2d) {
        WeaponType weapon = player.getCurrentWeapon();
        
        // Weapon name
        g2d.setFont(hudFont);
        g2d.setColor(Color.ORANGE);
        g2d.drawString(weapon.name, Constants.H_WIDTH - 60, Constants.HEIGHT - 35);
        
        // Weapon number
        g2d.setFont(largeFont);
        g2d.setColor(Color.YELLOW);
        g2d.drawString("" + (weapon.id + 1), Constants.H_WIDTH - 60, Constants.HEIGHT - 15);
    }
    
    private void drawPlayerFace(Graphics2D g2d) {
        // Simple face representation based on health
        int health = player.health;
        int faceX = Constants.H_WIDTH - 20;
        int faceY = Constants.HEIGHT - 40;
        int faceSize = 30;
        
        // Face background
        g2d.setColor(health < 25 ? Color.RED : health < 50 ? Color.YELLOW : Color.GREEN);
        g2d.fillOval(faceX, faceY, faceSize, faceSize);
        
        // Face border
        g2d.setColor(Color.BLACK);
        g2d.drawOval(faceX, faceY, faceSize, faceSize);
        
        // Eyes
        g2d.setColor(Color.BLACK);
        g2d.fillOval(faceX + 8, faceY + 8, 4, 4);
        g2d.fillOval(faceX + 18, faceY + 8, 4, 4);
        
        // Mouth based on health
        if (health < 25) {
            // Frown
            g2d.drawArc(faceX + 10, faceY + 18, 10, 8, 0, -180);
        } else if (health < 75) {
            // Neutral
            g2d.drawLine(faceX + 10, faceY + 20, faceX + 20, faceY + 20);
        } else {
            // Smile
            g2d.drawArc(faceX + 10, faceY + 15, 10, 8, 0, 180);
        }
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