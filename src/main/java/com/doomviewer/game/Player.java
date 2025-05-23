package com.doomviewer.game;

import com.doomviewer.misc.InputHandler;
import com.doomviewer.misc.Constants;
import com.doomviewer.misc.math.Vector2D;
import com.doomviewer.game.objects.GameDefinitions;
import com.doomviewer.game.objects.MapObject;
import com.doomviewer.wad.assets.AssetData;
import com.doomviewer.wad.datatypes.Thing;

import java.awt.event.KeyEvent;

public class Player extends MapObject {
    private DoomEngine engine; // For direct access like input, delta time
    public Vector2D pos;
    public double angle; // degrees
    private final double DIAG_MOVE_CORR = 1 / Math.sqrt(2);
    public double height; // Player's eye height from the base (0 level)
    public double floorHeight; // Height of the floor under the player

    public Player(DoomEngine engine, Thing playerThing, GameDefinitions gameDefinitions, AssetData assetData) {
        super(playerThing, gameDefinitions, assetData, engine); // This sets up pos, angle, info, type, health, flags, initial state
        this.engine = engine;
        this.pos = new Vector2D(playerThing.pos.x, playerThing.pos.y);
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
        
        // Set player collision radius to match standard Doom player size
        this.renderRadius = getPlayerRadius();
        
        System.out.println("Player initial pos: " + this.pos + ", angle (deg): " + this.angle);


        this.height = Constants.PLAYER_HEIGHT; // Initial height relative to floor
        this.floorHeight = 0;
    }

    // Override update from MapObject. Player doesn't use the generic state machine for its primary logic.
    @Override
    public void update(DoomEngine engine) {
        // Do NOT call super.update(engine) if player doesn't use the mobj state tics for its main loop.
        // If player has visual states (like pain, death anims), those could potentially use
        // a simplified version of the state machine, but movement/actions are direct.
        updateHeightAndZ();
        control();
        
        // CRITICAL: Sync Player position with MapObject position for AI targeting
        super.pos.x = this.pos.x;
        super.pos.y = this.pos.y;
        super.angle = this.angle;
    }

    private void updateHeightAndZ() {
        // This method should update this.floorHeight (inherited/available)
        // and this.z (the MapObject's base Z coordinate).
        // Use the new BSP method that takes player's current position
        this.floorHeight = engine.getBsp().getSubSectorHeightAt(this.pos.x, this.pos.y);
        this.z = this.floorHeight; // Update z immediately after floorHeight

        // Simple gravity/floor collision for player's base Z
        // For now, player snaps to floor. Add jumping/falling physics later.
        // this.z = this.floorHeight; // Player's feet are on the current floorHeight. // Already set above
        // More complex physics would adjust z based on zVelocity.
    }

    private void control() {
        double speed = Constants.PLAYER_SPEED * engine.getDeltaTime();
        double rotSpeed = Constants.PLAYER_ROT_SPEED * engine.getDeltaTime();
        InputHandler input = engine.getInputHandler();

        if (input.isKeyPressed(KeyEvent.VK_LEFT)) this.angle += rotSpeed;
        if (input.isKeyPressed(KeyEvent.VK_RIGHT)) this.angle -= rotSpeed;
        this.angle = (this.angle % 360.0 + 360.0) % 360.0;

        Vector2D inc = new Vector2D(0, 0);
        if (input.isKeyPressed(KeyEvent.VK_W)) inc.x += speed;
        if (input.isKeyPressed(KeyEvent.VK_S)) inc.x -= speed;
        if (input.isKeyPressed(KeyEvent.VK_A)) inc.y += speed; // Strafe Left
        if (input.isKeyPressed(KeyEvent.VK_D)) inc.y -= speed; // Strafe Right

        if (inc.x != 0 && inc.y != 0) inc = inc.scale(DIAG_MOVE_CORR);

        double playerAngleRad = Math.toRadians(this.angle);
        double cosA = Math.cos(playerAngleRad);
        double sinA = Math.sin(playerAngleRad);

        double worldDx = inc.x * cosA - inc.y * sinA;
        double worldDy = inc.x * sinA + inc.y * cosA;

        // Apply BSP collision detection with wall sliding
        applyMovementWithCollision(worldDx, worldDy);
    }

    /**
     * Apply movement with collision detection and wall sliding
     * @param dx Desired X movement
     * @param dy Desired Y movement
     */
    private void applyMovementWithCollision(double dx, double dy) {
        Vector2D currentPos = new Vector2D(this.pos.x, this.pos.y);
        Vector2D desiredPos = new Vector2D(this.pos.x + dx, this.pos.y + dy);
        double radius = getPlayerRadius();
        
        // Try full movement first
        Vector2D safePos = engine.getBsp().getSafeMovementPosition(currentPos, desiredPos, radius);
        
        // If we couldn't move fully, try sliding along walls
        if (Vector2D.distance(safePos, desiredPos) > 1.0) {
            // Debug: uncomment to see collision blocking
            // System.out.println("Player collision detected, attempting wall slide");
            // Try X movement only (slide along Y-axis walls)
            Vector2D xOnlyPos = new Vector2D(this.pos.x + dx, this.pos.y);
            Vector2D safeXOnly = engine.getBsp().getSafeMovementPosition(currentPos, xOnlyPos, radius);
            
            // Try Y movement only (slide along X-axis walls) 
            Vector2D yOnlyPos = new Vector2D(this.pos.x, this.pos.y + dy);
            Vector2D safeYOnly = engine.getBsp().getSafeMovementPosition(currentPos, yOnlyPos, radius);
            
            // Choose the movement that gets us furthest
            double xDistance = Vector2D.distance(currentPos, safeXOnly);
            double yDistance = Vector2D.distance(currentPos, safeYOnly);
            double fullDistance = Vector2D.distance(currentPos, safePos);
            
            if (fullDistance >= Math.max(xDistance, yDistance)) {
                // Full movement is best
                this.pos.x = safePos.x;
                this.pos.y = safePos.y;
            } else if (xDistance > yDistance) {
                // X-only movement is better (sliding along Y walls)
                this.pos.x = safeXOnly.x;
                this.pos.y = safeXOnly.y;
            } else if (yDistance > 0.1) {
                // Y-only movement is better (sliding along X walls)
                this.pos.x = safeYOnly.x;
                this.pos.y = safeYOnly.y;
            } else {
                // No significant movement possible
                this.pos.x = safePos.x;
                this.pos.y = safePos.y;
            }
        } else {
            // Full movement was successful
            this.pos.x = safePos.x;
            this.pos.y = safePos.y;
        }
    }

    /**
     * Gets the player's collision radius for BSP collision detection
     * Standard Doom player radius is 16 units
     */
    private double getPlayerRadius() {
        return 16.0; // Standard Doom player collision radius
    }

    /**
     * Gets the player's eye level Z coordinate for rendering the view.
     * This is the player's base Z + standard eye height.
     */
    public double getEyeLevelViewZ() {
        return this.z + Constants.PLAYER_HEIGHT;
    }

    // If Player needs to be damageable or interact like other MapObjects:
    // public void takeDamage(int amount, MapObject inflictor) {
    //     super.health -= amount; // Assuming health is in MapObject
    //     if (super.health <= 0) {
    //         setState(info.deathState); // Or player-specific death
    //     } else {
    //         setState(info.painState); // Or player-specific pain
    //     }
    // }
    
    public MapObject getMapObject() {
        return this; // Player extends MapObject
    }
}

