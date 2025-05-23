package com.doomviewer.game;

import com.doomviewer.game.objects.StateDef;
import com.doomviewer.game.objects.StateNum;
import com.doomviewer.misc.InputHandler;
import com.doomviewer.misc.Constants;
import com.doomviewer.misc.math.Vector2D;
import com.doomviewer.game.objects.GameDefinitions;
import com.doomviewer.game.objects.MapObject;
import com.doomviewer.wad.assets.AssetData;
import com.doomviewer.wad.datatypes.Thing;

import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.Map;

public class Player extends MapObject {
    private DoomEngine engine; // For direct access like input, delta time
    public Vector2D pos;
    public double angle; // degrees
    private final double DIAG_MOVE_CORR = 1 / Math.sqrt(2);
    public double height; // Player's eye height from the base (0 level)
    public double floorHeight; // Height of the floor under the player
    
    // Weapons and HUD
    private WeaponType currentWeapon;
    private boolean[] ownedWeapons;
    private Map<AmmoType, Integer> ammo;
    private PlayerHUD hud;
    
    // Weapon state machine (following original Doom)
    private StateNum weaponState;
    private StateDef weaponStateDef;
    private int weaponTics;
    private boolean attackdown; // Track if fire button is held
    private boolean pendingweapon; // Weapon switch pending
    private WeaponType pendingWeaponType;

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
        
        // Initialize weapons and ammo
        initializeWeaponsAndAmmo();
    }

    // Override update from MapObject. Player doesn't use the generic state machine for its primary logic.
    @Override
    public void update(DoomEngine engine) {
        // Do NOT call super.update(engine) if player doesn't use the mobj state tics for its main loop.
        // If player has visual states (like pain, death anims), those could potentially use
        // a simplified version of the state machine, but movement/actions are direct.
        updateHeightAndZ();
        control();
        
        // Handle weapon input and update weapon state machine
        updateWeaponStateMachine();
        
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

        // Check if Shift is pressed for running (double speed)
        boolean isRunning = input.isKeyPressed(KeyEvent.VK_SHIFT);
        if (isRunning) {
            speed *= 2.0; // Double speed when running
            rotSpeed *= 1.5; // Slightly faster rotation when running
        }

        // Q/E for rotation
        if (input.isKeyPressed(KeyEvent.VK_Q)) this.angle += rotSpeed;
        if (input.isKeyPressed(KeyEvent.VK_E)) this.angle -= rotSpeed;
        this.angle = (this.angle % 360.0 + 360.0) % 360.0;

        Vector2D inc = new Vector2D(0, 0);
        // Forward/Backward: W/S and Up/Down arrows
        if (input.isKeyPressed(KeyEvent.VK_W) || input.isKeyPressed(KeyEvent.VK_UP)) inc.x += speed;      // Forward
        if (input.isKeyPressed(KeyEvent.VK_S) || input.isKeyPressed(KeyEvent.VK_DOWN)) inc.x -= speed;    // Backward
        // Strafing: A/D and Left/Right arrows
        if (input.isKeyPressed(KeyEvent.VK_A) || input.isKeyPressed(KeyEvent.VK_LEFT)) inc.y += speed;   // Strafe Left
        if (input.isKeyPressed(KeyEvent.VK_D) || input.isKeyPressed(KeyEvent.VK_RIGHT)) inc.y -= speed;  // Strafe Right

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
    
    private void initializeWeaponsAndAmmo() {
        // Initialize weapon ownership - give all weapons for testing
        ownedWeapons = new boolean[WeaponType.values().length];
        ownedWeapons[WeaponType.PISTOL.id] = true;
        ownedWeapons[WeaponType.SHOTGUN.id] = true;
        ownedWeapons[WeaponType.CHAINGUN.id] = true;
        ownedWeapons[WeaponType.ROCKET_LAUNCHER.id] = true;
        ownedWeapons[WeaponType.PLASMA_RIFLE.id] = true;
        ownedWeapons[WeaponType.BFG.id] = true;
        currentWeapon = WeaponType.PISTOL;
        
        // Initialize ammo - give plenty for testing
        ammo = new EnumMap<>(AmmoType.class);
        ammo.put(AmmoType.BULLETS, 200);    // Max bullets
        ammo.put(AmmoType.SHELLS, 50);      // Max shells
        ammo.put(AmmoType.ROCKETS, 50);     // Max rockets
        ammo.put(AmmoType.CELLS, 300);      // Max cells
        
        // Initialize HUD
        hud = new PlayerHUD(this);
        
        // Initialize weapon state machine
        weaponState = getReadyStateForWeapon(currentWeapon);
        weaponStateDef = getGameDefinitions().getState(weaponState);
        weaponTics = weaponStateDef.tics;
        attackdown = false;
        pendingweapon = false;
    }
    
    public WeaponType getCurrentWeapon() {
        return currentWeapon;
    }
    
    public int getAmmo(AmmoType ammoType) {
        return ammo.getOrDefault(ammoType, 0);
    }
    
    public void addAmmo(AmmoType ammoType, int amount) {
        int current = ammo.getOrDefault(ammoType, 0);
        int newAmount = Math.min(current + amount, ammoType.maxAmmo);
        ammo.put(ammoType, newAmount);
    }
    
    public boolean hasWeapon(WeaponType weapon) {
        return ownedWeapons[weapon.id];
    }
    
    public void giveWeapon(WeaponType weapon) {
        ownedWeapons[weapon.id] = true;
    }
    
    public boolean switchWeapon(WeaponType weapon) {
        if (hasWeapon(weapon) && currentWeapon != weapon) {
            // For now, do immediate weapon switch (can add lowering/raising animation later)
            currentWeapon = weapon;
            setWeaponState(getReadyStateForWeapon(currentWeapon));
            System.out.println("Switched to " + weapon.name);
            return true;
        }
        return false;
    }
    
    private void updateWeaponStateMachine() {
        InputHandler input = engine.getInputHandler();
        
        // Handle weapon switching (1-6 keys)
        if (input.isKeyPressed(KeyEvent.VK_1)) switchWeapon(WeaponType.PISTOL);
        if (input.isKeyPressed(KeyEvent.VK_2)) switchWeapon(WeaponType.SHOTGUN);
        if (input.isKeyPressed(KeyEvent.VK_3)) switchWeapon(WeaponType.CHAINGUN);
        if (input.isKeyPressed(KeyEvent.VK_4)) switchWeapon(WeaponType.ROCKET_LAUNCHER);
        if (input.isKeyPressed(KeyEvent.VK_5)) switchWeapon(WeaponType.PLASMA_RIFLE);
        if (input.isKeyPressed(KeyEvent.VK_6)) switchWeapon(WeaponType.BFG);
        
        // Track attack button state
        boolean firePressed = input.isKeyPressed(KeyEvent.VK_SPACE) || input.isKeyPressed(KeyEvent.VK_CONTROL);
        
        if (firePressed && !attackdown) {
            attackdown = true;
            // Attempt to fire if weapon is ready
            if (isWeaponReady()) {
                startWeaponFiring();
            }
        } else if (!firePressed) {
            attackdown = false;
        }
        
        // Update weapon state machine
        if (weaponTics > 0) {
            weaponTics--;
        }
        
        if (weaponTics <= 0) {
            // Execute weapon action and transition to next state
            if (weaponStateDef.action != null) {
                weaponStateDef.action.execute(this, engine);
            }
            
            setWeaponState(weaponStateDef.nextState);
        }
    }
    
    private boolean isWeaponReady() {
        return weaponState == getReadyStateForWeapon(currentWeapon);
    }
    
    private void startWeaponFiring() {
        AmmoType requiredAmmo = getAmmoTypeForWeapon(currentWeapon);
        int currentAmmoCount = getAmmo(requiredAmmo);
        
        if (currentAmmoCount >= currentWeapon.ammoPerShot) {
            // Consume ammo
            ammo.put(requiredAmmo, currentAmmoCount - currentWeapon.ammoPerShot);
            
            // Start weapon firing state
            setWeaponState(getFireStateForWeapon(currentWeapon));
            
            System.out.println("Player fires " + currentWeapon.name + "! Ammo remaining: " + getAmmo(requiredAmmo));
        } else {
            System.out.println("Out of ammo for " + currentWeapon.name + "!");
        }
    }
    
    private void setWeaponState(StateNum newState) {
        weaponState = newState;
        weaponStateDef = getGameDefinitions().getState(weaponState);
        weaponTics = weaponStateDef.tics;
    }
    
    private StateNum getReadyStateForWeapon(WeaponType weapon) {
        switch (weapon) {
            case PISTOL: return StateNum.S_PISTOL;
            case SHOTGUN: return StateNum.S_SGUN;
            case CHAINGUN: return StateNum.S_CHAIN;
            case ROCKET_LAUNCHER: return StateNum.S_MISSILE;
            case PLASMA_RIFLE: return StateNum.S_PLASMA;
            case BFG: return StateNum.S_BFG;
            default: return StateNum.S_PISTOL;
        }
    }
    
    private StateNum getFireStateForWeapon(WeaponType weapon) {
        switch (weapon) {
            case PISTOL: return StateNum.S_PISTOL1;
            case SHOTGUN: return StateNum.S_SGUN1;
            case CHAINGUN: return StateNum.S_CHAIN1;
            case ROCKET_LAUNCHER: return StateNum.S_MISSILE1;
            case PLASMA_RIFLE: return StateNum.S_PLASMA1;
            case BFG: return StateNum.S_BFG1;
            default: return StateNum.S_PISTOL1;
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
    
    private double normalizeAngle(double angle) {
        while (angle < -180) angle += 360;
        while (angle > 180) angle -= 360;
        return angle;
    }
    
    public PlayerHUD getHUD() {
        return hud;
    }
    
    public String getCurrentWeaponSprite() {
        if (weaponStateDef != null) {
            // Generate weapon sprite name with rotation 0 instead of 1
            char frameChar = (char) ('A' + weaponStateDef.getFrameIndex());
            String spriteName = String.format("%s%c0", weaponStateDef.spriteName.getName(), frameChar);
            System.out.println("Debug: Weapon sprite name: " + spriteName);
            return spriteName;
        }
        System.out.println("Debug: Using fallback sprite: " + currentWeapon.spriteName);
        return currentWeapon.spriteName; // Fallback
    }
    
    public boolean isAttackDown() {
        return attackdown;
    }

}

