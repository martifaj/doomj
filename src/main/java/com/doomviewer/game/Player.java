package com.doomviewer.game;

import com.doomviewer.core.Settings;
import com.doomviewer.core.InputHandler;
import com.doomviewer.core.math.Vector2D;
import com.doomviewer.main.DoomEngine; // For dt and bsp access
import com.doomviewer.wad.datatypes.Thing;

import java.awt.event.KeyEvent;

public class Player {
    private DoomEngine engine;
    public Vector2D pos;
    public double angle; // degrees
    private final double DIAG_MOVE_CORR = 1 / Math.sqrt(2);
    public double height; // Player's eye height from the base (0 level)
    public double floorHeight; // Height of the floor under the player
    private double zVelocity; // For jumping/falling simulation

    public Player(DoomEngine engine) {
        this.engine = engine;
        Thing playerThing = engine.getWadData().things.get(0); // Assuming player is the first thing
        this.pos = new Vector2D(playerThing.pos.x, playerThing.pos.y);
        this.angle = playerThing.angle; // This is BAMS, needs conversion if not already done by Thing loader or here
        // The Python code uses it directly, implying it's degrees.
        // player.thing.angle is used in `rotate_ip(self.angle)`.
        // WAD `Thing.angle` is BAMS (0-32767 or 0-65535 for 0-360 deg).
        // Python `Thing` class doesn't convert it.
        // Let's assume it's degrees for now as Python code uses it directly with rotate_ip.
        // If it's BAMS: this.angle = (playerThing.angle / 65536.0) * 360.0;
        // For now, let's match Python's direct use. If playerThing.angle is BAMS, then rotateIp needs BAMS-like angle or conversion here.
        // Python's rotate_ip takes degrees. So player.angle must be degrees.
        this.angle = ((playerThing.angle & 0xFFFF) / 65536.0) * 360.0; // Correct for unsigned 16-bit BAMS
        this.angle %= 360.0;
        System.out.println("Player initial pos: " + this.pos + ", angle (deg): " + this.angle);


        this.height = Settings.PLAYER_HEIGHT; // Initial height relative to floor
        this.floorHeight = 0;
        this.zVelocity = 0;

    }

    public void update() {
        getHeight();
        control();
    }

    private void getHeight() {
        this.floorHeight = engine.getBsp().getSubSectorHeight();

        // Simple gravity/floor collision
        // Python: self.height += 0.4 * (self.floor_height + PLAYER_HEIGHT - self.height)
        // This seems to be aiming player's base height (not eye height) towards floor_height + PLAYER_HEIGHT (eye level)
        // Let's reinterpret: player's eye position `self.height` should be `self.floor_height + Settings.PLAYER_HEIGHT`

        double targetEyeHeight = this.floorHeight + Settings.PLAYER_HEIGHT;

        if (this.height < targetEyeHeight) {
            // Smoothly move up to target eye height if below or on floor
            this.height += 0.4 * (targetEyeHeight - this.height);
            this.zVelocity = 0;
        } else {
            // Apply gravity if above target (e.g. falling)
            this.zVelocity -= 0.9; // Gravity effect
            this.height += Math.max(-15.0, this.zVelocity); // Apply velocity, cap fall speed

            if (this.height < targetEyeHeight) { // Landed
                this.height = targetEyeHeight;
                this.zVelocity = 0;
            }
        }
    }

    private void control() {
        double speed = Settings.PLAYER_SPEED * engine.getDeltaTime();
        double rotSpeed = Settings.PLAYER_ROT_SPEED * engine.getDeltaTime(); // This is likely an angular speed in degrees/ms

        InputHandler input = engine.getInputHandler();

        if (input.isKeyPressed(KeyEvent.VK_LEFT)) {
            this.angle += rotSpeed;
        }
        if (input.isKeyPressed(KeyEvent.VK_RIGHT)) {
            this.angle -= rotSpeed;
        }
        this.angle %= 360.0;
        if (this.angle < 0) this.angle += 360.0;


        Vector2D inc = new Vector2D(0, 0);
        if (input.isKeyPressed(KeyEvent.VK_A)) { // Strafe left
            inc.y += speed; // Positive Y in Pygame's rotated coord system (forward is +X)
        }
        if (input.isKeyPressed(KeyEvent.VK_D)) { // Strafe right
            inc.y -= speed; // Negative Y
        }
        if (input.isKeyPressed(KeyEvent.VK_W)) { // Forward
            inc.x += speed;
        }
        if (input.isKeyPressed(KeyEvent.VK_S)) { // Backward
            inc.x -= speed;
        }

        if (inc.x != 0 && inc.y != 0) {
            inc = inc.scale(DIAG_MOVE_CORR);
        }

        // Pygame's inc.rotate_ip(self.angle) rotates (x,y) by angle.
        // If +X is forward, +Y is left:
        // new_dx = dx*cos(a) - dy*sin(a)
        // new_dy = dx*sin(a) + dy*cos(a)
        // Here, inc.x is forward/backward, inc.y is strafe left/right.
        // Angle 0 is East. Increasing angle rotates counter-clockwise.
        // Standard rotation:
        // finalDx = inc.x * cos(player_angle_rad) - inc.y * sin(player_angle_rad)
        // finalDy = inc.x * sin(player_angle_rad) + inc.y * cos(player_angle_rad)
        // However, Pygame's Vector2.rotate_ip might be different or Doom's coord system.
        // Python's inc.rotate_ip(self.angle): if angle is 0, (1,0) remains (1,0). If angle is 90, (1,0) becomes (0,1).
        // This is standard counter-clockwise rotation.

        // The `inc` vector is in player's local space (x forward, y left).
        // We need to rotate it by player's world angle to get world-space movement.
        double playerAngleRad = Math.toRadians(this.angle);
        double cosA = Math.cos(playerAngleRad);
        double sinA = Math.sin(playerAngleRad);

        double worldDx = inc.x * cosA - inc.y * sinA;
        double worldDy = inc.x * sinA + inc.y * cosA;

        this.pos.x += worldDx;
        this.pos.y += worldDy;
    }
}