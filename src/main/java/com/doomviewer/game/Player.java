package com.doomviewer.game;

import com.doomviewer.audio.SoundEngine;
import com.doomviewer.config.GameConfiguration;
import com.doomviewer.game.objects.*;
import com.doomviewer.misc.Constants;
import com.doomviewer.misc.math.Vector2D;
import com.doomviewer.services.*;
import com.doomviewer.wad.assets.AssetData;
import com.doomviewer.wad.datatypes.Thing;

import java.awt.event.KeyEvent;
import java.util.*;
import java.util.logging.Logger;

public class Player extends MapObject {
    private static final Logger LOGGER = Logger.getLogger(Player.class.getName());
    private final GameConfiguration config;
    private final CollisionService collisionService;
    private final AudioService audioService;
    private final InputService inputService;
    private final ObjectManager objectManager;
    private final DoorService doorService;
    private final GameEngineTmp gameEngineTmp;

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

    // Player stats
    private int maxHealth = 100;
    private int armor = 0;
    private int maxArmor = 200;
    private boolean hasBackpack = false;

    // Keys
    private Set<KeyType> keys;

    // Weapon state machine (following original Doom)
    private StateNum weaponState;
    private StateDef weaponStateDef;
    private int weaponTics;
    private boolean attackdown; // Track if fire button is held
    private boolean pendingweapon; // Weapon switch pending
    private WeaponType pendingWeaponType;
    private AssetData assetData; // Store AssetData for HUD access

    public Player(Thing playerThing, GameDefinitions gameDefinitions, AssetData assetData,
                  GameConfiguration config, CollisionService collisionService, AudioService audioService,
                  InputService inputService, ObjectManager objectManager, DoorService doorService, GameEngineTmp gameEngineTmp) {
        super(playerThing, gameDefinitions, assetData, collisionService, audioService, gameEngineTmp, objectManager);
        this.config = config;
        this.collisionService = collisionService;
        this.audioService = audioService;
        this.inputService = inputService;
        this.objectManager = objectManager;
        this.doorService = doorService;
        this.assetData = assetData; // Store AssetData for HUD access
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
        this.gameEngineTmp = gameEngineTmp;
        this.angle %= 360.0;

        // Set player collision radius to match standard Doom player size
        this.renderRadius = getPlayerRadius();


        this.height = config.getPlayerHeight(); // Initial height relative to floor
        this.floorHeight = 0;

        // Initialize weapons and ammo
        initializeWeaponsAndAmmo();

        // Set starting health to 100%
        this.health = 100;
        this.setTarget(this);
    }

    // Override update from MapObject. Player doesn't use the generic state machine for its primary logic.
    public void update() {
        // Do NOT call super.update(engine) if player doesn't use the mobj state tics for its main loop.
        // If player has visual states (like pain, death anims), those could potentially use
        // a simplified version of the state machine, but movement/actions are direct.
        updateHeightAndZ();
        control();

        // Handle weapon input and update weapon state machine
        updateWeaponStateMachine();

        // Check for automatic pickups
        checkForPickups();

        // CRITICAL: Sync Player position with MapObject position for AI targeting
        super.pos.x = this.pos.x;
        super.pos.y = this.pos.y;
        super.angle = this.angle;
    }

    private void updateHeightAndZ() {
        // This method should update this.floorHeight (inherited/available)
        // and this.z (the MapObject's base Z coordinate).
        // Use the new BSP method that takes player's current position
        this.floorHeight = collisionService.getSubSectorHeightAt(this.pos.x, this.pos.y);
        this.z = this.floorHeight; // Update z immediately after floorHeight

        // Simple gravity/floor collision for player's base Z
        // For now, player snaps to floor. Add jumping/falling physics later.
        // this.z = this.floorHeight; // Player's feet are on the current floorHeight. // Already set above
        // More complex physics would adjust z based on zVelocity.
    }

    private void control() {
        double speed = config.getPlayerSpeed() * gameEngineTmp.getDeltaTime();
        double rotSpeed = config.getPlayerRotSpeed() * gameEngineTmp.getDeltaTime();
        InputService input = inputService;

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
     *
     * @param dx Desired X movement
     * @param dy Desired Y movement
     */
    private void applyMovementWithCollision(double dx, double dy) {
        Vector2D currentPos = new Vector2D(this.pos.x, this.pos.y);
        Vector2D desiredPos = new Vector2D(this.pos.x + dx, this.pos.y + dy);
        double radius = getPlayerRadius();

        // Try full movement first
        Vector2D safePos = collisionService.getSafeMovementPosition(currentPos, desiredPos, radius, false);

        // If we couldn't move fully, try sliding along walls
        if (Vector2D.distance(safePos, desiredPos) > 1.0) {
            // Debug: uncomment to see collision blocking
            // Try X movement only (slide along Y-axis walls)
            Vector2D xOnlyPos = new Vector2D(this.pos.x + dx, this.pos.y);
            Vector2D safeXOnly = collisionService.getSafeMovementPosition(currentPos, xOnlyPos, radius, false);

            // Try Y movement only (slide along X-axis walls) 
            Vector2D yOnlyPos = new Vector2D(this.pos.x, this.pos.y + dy);
            Vector2D safeYOnly = collisionService.getSafeMovementPosition(currentPos, yOnlyPos, radius, false);

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

        // Initialize keys
        keys = EnumSet.noneOf(KeyType.class);

        // Initialize HUD
        hud = new PlayerHUD(this);
        hud.setAssetData(this.assetData); // Pass AssetData to HUD

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
        int maxCapacity = getMaxAmmoCapacity(ammoType);
        int newAmount = Math.min(current + amount, maxCapacity);
        ammo.put(ammoType, newAmount);
    }

    public int getMaxAmmoCapacity(AmmoType ammoType) {
        return hasBackpack ? ammoType.maxAmmo * 2 : ammoType.maxAmmo;
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
            return true;
        }
        return false;
    }

    private void updateWeaponStateMachine() {
        InputService input = inputService;

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
                hud.onPlayerAttack(); // Notify HUD of attack
            }
        } else if (!firePressed) {
            attackdown = false;
        }

        // Use key for doors and interactions
        if (input.isKeyPressed(KeyEvent.VK_ENTER)) {
            tryUseAction();
        }

        // Update weapon state machine
        if (weaponTics > 0) {
            weaponTics--;
        }

        if (weaponTics <= 0) {
            // Execute weapon action and transition to next state
            if (weaponStateDef.action != null) {
                weaponStateDef.action.execute(this, objectManager, this, audioService, gameEngineTmp, collisionService);
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

            // Create projectile based on weapon type
            createProjectileForWeapon();

            // Start weapon firing state
            setWeaponState(getFireStateForWeapon(currentWeapon));

        } else {
            // No ammo click sound would go here
        }
    }

    private void createProjectileForWeapon() {
        MobjType projectileType = getProjectileTypeForWeapon(currentWeapon);
        if (projectileType != null) {
            // Calculate firing position (slightly in front of player)
            double fireAngleRad = Math.toRadians(this.angle);
            Vector2D firePos = new Vector2D(
                    this.pos.x + Math.cos(fireAngleRad) * (this.renderRadius + 16),
                    this.pos.y + Math.sin(fireAngleRad) * (this.renderRadius + 16)
            );

            // Create projectile through ObjectManager
            objectManager.createProjectile(projectileType, firePos, this.angle, this);

            // Play weapon sound
            playWeaponSound();
        } else {
            // Hitscan weapons (pistol, shotgun, chaingun) don't create projectiles
            // They would use instant hit logic here
            performHitscanAttack();
        }
    }

    private MobjType getProjectileTypeForWeapon(WeaponType weapon) {
        switch (weapon) {
            case ROCKET_LAUNCHER:
                return MobjType.MT_ROCKET;
            case PLASMA_RIFLE:
                return MobjType.MT_PLASMA;
            case BFG:
                return MobjType.MT_BFG;
            default:
                return null; // Hitscan weapons
        }
    }

    private void performHitscanAttack() {
        // For hitscan weapons (pistol, shotgun, chaingun)
        // This would implement instant hit logic
        // For now, just create a muzzle flash effect
        LOGGER.info("Player fired " + currentWeapon.name + " (hitscan)");

        // TODO: Implement hitscan collision detection
        // - Cast ray from player position in facing direction
        // - Check for enemy hits using BSP traversal
        // - Apply damage to first target hit
        // - Create bullet puff effect at impact point
    }

    private void playWeaponSound() {
        // Play appropriate weapon sound
        String soundName = getWeaponSoundName(currentWeapon);
        if (soundName != null) {
            // TODO: Play sound through SoundEngine
            System.out.println("Playing weapon sound: " + soundName);
        }
    }

    private String getWeaponSoundName(WeaponType weapon) {
        switch (weapon) {
            case PISTOL:
                return "pistol";
            case SHOTGUN:
                return "shotgn";
            case CHAINGUN:
                return "pistol"; // Chaingun uses rapid pistol sounds
            case ROCKET_LAUNCHER:
                return "rlaunc";
            case PLASMA_RIFLE:
                return "plasma";
            case BFG:
                return "bfg";
            default:
                return null;
        }
    }

    private void setWeaponState(StateNum newState) {
        weaponState = newState;
        weaponStateDef = getGameDefinitions().getState(weaponState);
        weaponTics = weaponStateDef.tics;
    }

    private StateNum getReadyStateForWeapon(WeaponType weapon) {
        switch (weapon) {
            case PISTOL:
                return StateNum.S_PISTOL;
            case SHOTGUN:
                return StateNum.S_SGUN;
            case CHAINGUN:
                return StateNum.S_CHAIN;
            case ROCKET_LAUNCHER:
                return StateNum.S_MISSILE;
            case PLASMA_RIFLE:
                return StateNum.S_PLASMA;
            case BFG:
                return StateNum.S_BFG;
            default:
                return StateNum.S_PISTOL;
        }
    }

    private StateNum getFireStateForWeapon(WeaponType weapon) {
        switch (weapon) {
            case PISTOL:
                return StateNum.S_PISTOL1;
            case SHOTGUN:
                return StateNum.S_SGUN1;
            case CHAINGUN:
                return StateNum.S_CHAIN1;
            case ROCKET_LAUNCHER:
                return StateNum.S_MISSILE1;
            case PLASMA_RIFLE:
                return StateNum.S_PLASMA1;
            case BFG:
                return StateNum.S_BFG1;
            default:
                return StateNum.S_PISTOL1;
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

    public void takeDamage(int amount) {
        health -= amount;
        hud.onPlayerDamage(); // Notify HUD of damage for pain face

        if (health <= 0) {
            // Player death logic would go here
        }
    }

    public String getCurrentWeaponSprite() {
        if (weaponStateDef != null) {
            // Generate weapon sprite name with rotation 0 instead of 1
            char frameChar = (char) ('A' + weaponStateDef.getFrameIndex());
            String spriteName = String.format("%s%c0", weaponStateDef.spriteName.getName(), frameChar);
            return spriteName;
        }
        return currentWeapon.spriteName; // Fallback
    }

    public boolean isAttackDown() {
        return attackdown;
    }

    // Key management methods
    public void addKey(KeyType keyType) {
        keys.add(keyType);
        LOGGER.info("Player picked up: " + keyType.name);
    }

    public boolean hasKey(KeyType keyType) {
        return keys.stream().anyMatch(key -> key.matches(keyType));
    }

    public Set<KeyType> getKeys() {
        return keys;
    }

    public int getArmor() {
        return armor;
    }

    public int getMaxArmor() {
        return maxArmor;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public boolean hasBackpack() {
        return hasBackpack;
    }

    private void tryUseAction() {
        // Try to use doors within range (64 units, standard DOOM use range)
        boolean doorUsed = doorService.tryUseDoor(this, this.pos, 64.0);
        LOGGER.info("Door use attempt: " + (doorUsed ? "SUCCESS" : "FAILED"));

        if (!doorUsed) {
            // Try to pick up items
            tryPickupItems();
        }
    }

    private void tryPickupItems() {
        // Check for keys and other items near the player (for use action)
        for (MapObject obj : objectManager.getMapObjects()) {
            if (Vector2D.distance(obj.pos, this.pos) <= 32.0) { // Pickup range
                if (tryPickupObject(obj)) {
                    break; // Only pick up one item per use
                }
            }
        }
    }

    private void checkForPickups() {
        // Automatic pickup system - runs every frame
        List<MapObject> objectsToRemove = new ArrayList<>();

        for (MapObject obj : objectManager.getMapObjects()) {
            double distance = Vector2D.distance(obj.pos, this.pos);

            // Different pickup ranges for different items
            double pickupRange = getPickupRange(obj);

            if (distance <= pickupRange) {
                if (tryPickupObject(obj)) {
                    objectsToRemove.add(obj);
                }
            }
        }

        // Remove picked up objects
        for (MapObject obj : objectsToRemove) {
            objectManager.removeObject(obj);
        }
    }

    private double getPickupRange(MapObject obj) {
        // Different pickup ranges based on item type
        switch (obj.type) {
            case MT_BLUEKEY:
            case MT_YELLOWKEY:
            case MT_REDKEY:
            case MT_BLUESKULL:
            case MT_YELLOWSKULL:
            case MT_REDSKULL:
                return 24.0; // Keys require closer contact

            case MT_STIMPACK:
            case MT_MEDIKIT:
            case MT_SOULSPHERE:
            case MT_MEGAARMOR:
            case MT_GREENARMOR:
            case MT_HEALTH_BONUS:
            case MT_ARMOR_BONUS:
                return 20.0; // Health/armor items

            case MT_CLIP:
            case MT_AMMO:
            case MT_SHELLS:
            case MT_SHELLBOX:
            case MT_CELL:
            case MT_CELLPACK:
            case MT_BACKPACK:
                return 20.0; // Ammo items

            case MT_SHOTGUN:
            case MT_CHAINGUN:
            case MT_MISSILE:
            case MT_PLASMA_RIFLE:
            case MT_CHAINSAW:
            case MT_SUPERSHOTGUN:
                return 24.0; // Weapons

            case MT_INVULNERABILITY:
            case MT_BERSERK:
            case MT_INVISIBILITY:
            case MT_SUIT:
            case MT_COMPUTER_MAP:
            case MT_LIGHT_AMP:
                return 20.0; // Power-ups

            default:
                return 32.0; // Default range
        }
    }

    private boolean tryPickupObject(MapObject obj) {
        switch (obj.type) {
            // Keys
            case MT_BLUEKEY:
            case MT_YELLOWKEY:
            case MT_REDKEY:
            case MT_BLUESKULL:
            case MT_YELLOWSKULL:
            case MT_REDSKULL:
                return tryPickupKey(obj);

            // Health items
            case MT_STIMPACK:
                return tryPickupHealth(10);
            case MT_MEDIKIT:
                return tryPickupHealth(25);
            case MT_SOULSPHERE:
                return tryPickupSoulsphere();
            case MT_HEALTH_BONUS:
                return tryPickupHealthBonus();

            // Armor items
            case MT_GREENARMOR:
                return tryPickupArmor(100, false);
            case MT_MEGAARMOR:
                return tryPickupArmor(200, true);
            case MT_ARMOR_BONUS:
                return tryPickupArmorBonus();

            // Ammo items
            case MT_CLIP:
                return tryPickupAmmo(AmmoType.BULLETS, 10);
            case MT_AMMO:
                return tryPickupAmmo(AmmoType.BULLETS, 50);
            case MT_SHELLS:
                return tryPickupAmmo(AmmoType.SHELLS, 4);
            case MT_SHELLBOX:
                return tryPickupAmmo(AmmoType.SHELLS, 20);
            case MT_CELL:
                return tryPickupAmmo(AmmoType.CELLS, 20);
            case MT_CELLPACK:
                return tryPickupAmmo(AmmoType.CELLS, 100);
            case MT_BACKPACK:
                return tryPickupBackpack();

            // Weapons
            case MT_SHOTGUN:
                return tryPickupWeapon(WeaponType.SHOTGUN, AmmoType.SHELLS, 8);
            case MT_CHAINGUN:
                return tryPickupWeapon(WeaponType.CHAINGUN, AmmoType.BULLETS, 20);
            case MT_MISSILE:
                return tryPickupWeapon(WeaponType.ROCKET_LAUNCHER, AmmoType.ROCKETS, 2);
            case MT_PLASMA_RIFLE:
                return tryPickupWeapon(WeaponType.PLASMA_RIFLE, AmmoType.CELLS, 40);
            case MT_CHAINSAW:
                return tryPickupWeapon(WeaponType.PISTOL, null, 0); // Chainsaw doesn't use ammo
            case MT_SUPERSHOTGUN:
                return tryPickupWeapon(WeaponType.SHOTGUN, AmmoType.SHELLS, 8); // Treat as shotgun for now

            // Power-ups (placeholder - these would need timer/effect system)
            case MT_INVULNERABILITY:
            case MT_BERSERK:
            case MT_INVISIBILITY:
            case MT_SUIT:
            case MT_COMPUTER_MAP:
            case MT_LIGHT_AMP:
                return tryPickupPowerup(obj.type);

            default:
                return false; // Not a pickup item
        }
    }

    private boolean tryPickupKey(MapObject obj) {
        KeyType keyType = KeyType.fromThingType(obj.info.doomednum);
        if (keyType != null) {
            addKey(keyType);
            SoundEngine.getInstance().playSound("DSITEMUP");
            return true;
        }
        return false;
    }

    private boolean tryPickupHealth(int amount) {
        if (health < maxHealth) {
            health = Math.min(health + amount, maxHealth);
            SoundEngine.getInstance().playSound("DSITEMUP");
            LOGGER.info("Picked up health: +" + amount + " (total: " + health + ")");
            return true;
        }
        return false;
    }

    private boolean tryPickupSoulsphere() {
        health = Math.min(health + 100, 200); // Soulsphere can exceed normal max health
        maxHealth = Math.max(maxHealth, 200); // Increase max health if needed
        SoundEngine.getInstance().playSound("DSGETPOW");
        LOGGER.info("Picked up Soulsphere! Health: " + health);
        return true;
    }

    private boolean tryPickupHealthBonus() {
        if (health < 200) { // Health bonus can go up to 200
            health = Math.min(health + 1, 200);
            SoundEngine.getInstance().playSound("DSITEMUP");
            LOGGER.info("Picked up health bonus: +1 (total: " + health + ")");
            return true;
        }
        return false;
    }

    private boolean tryPickupArmor(int amount, boolean isMegaArmor) {
        if (isMegaArmor || armor < amount) {
            armor = amount;
            maxArmor = isMegaArmor ? 200 : 100;
            SoundEngine.getInstance().playSound(isMegaArmor ? "DSGETPOW" : "DSITEMUP");
            LOGGER.info("Picked up armor: " + amount + (isMegaArmor ? " (MegaArmor)" : " (Green Armor)"));
            return true;
        }
        return false;
    }

    private boolean tryPickupArmorBonus() {
        if (armor < 200) { // Armor bonus can go up to 200
            armor = Math.min(armor + 1, 200);
            SoundEngine.getInstance().playSound("DSITEMUP");
            LOGGER.info("Picked up armor bonus: +1 (total: " + armor + ")");
            return true;
        }
        return false;
    }

    private boolean tryPickupAmmo(AmmoType ammoType, int amount) {
        int currentAmmo = getAmmo(ammoType);
        int maxCapacity = getMaxAmmoCapacity(ammoType);
        if (currentAmmo < maxCapacity) {
            addAmmo(ammoType, amount);
            SoundEngine.getInstance().playSound("DSITEMUP");
            LOGGER.info("Picked up " + ammoType.name() + ": +" + amount + " (total: " + getAmmo(ammoType) + ")");
            return true;
        }
        return false;
    }

    private boolean tryPickupBackpack() {
        if (!hasBackpack) {
            // Backpack doubles max ammo capacity and gives some ammo
            hasBackpack = true;
            for (AmmoType ammoType : AmmoType.values()) {
                addAmmo(ammoType, ammoType.maxAmmo / 2); // Give 50% of base max capacity
            }
            SoundEngine.getInstance().playSound("DSITEMUP");
            LOGGER.info("Picked up Backpack! All ammo capacity doubled.");
            return true;
        }
        return false; // Already have backpack
    }

    private boolean tryPickupWeapon(WeaponType weapon, AmmoType ammoType, int ammoAmount) {
        boolean hadWeapon = hasWeapon(weapon);
        giveWeapon(weapon);

        boolean pickedUpAmmo = false;
        if (ammoType != null && ammoAmount > 0) {
            // Don't play sound here - tryPickupAmmo will play its own sound if successful
            int currentAmmo = getAmmo(ammoType);
            int maxCapacity = getMaxAmmoCapacity(ammoType);
            if (currentAmmo < maxCapacity) {
                addAmmo(ammoType, ammoAmount);
                pickedUpAmmo = true;
            }
        }

        if (!hadWeapon) {
            SoundEngine.getInstance().playSound("DSWPNUP");
            LOGGER.info("Picked up weapon: " + weapon.name());
            return true;
        } else if (pickedUpAmmo) {
            SoundEngine.getInstance().playSound("DSITEMUP");
            LOGGER.info("Picked up ammo from weapon: " + weapon.name());
            return true;
        }

        return false; // Already had weapon and didn't need ammo
    }

    private boolean tryPickupPowerup(MobjType powerupType) {
        // Placeholder for power-up effects - would need timer system
        SoundEngine.getInstance().playSound("DSGETPOW");
        LOGGER.info("Picked up power-up: " + powerupType.name());
        return true;
    }

}

