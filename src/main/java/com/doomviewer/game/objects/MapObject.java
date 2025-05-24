package com.doomviewer.game.objects;

import com.doomviewer.game.DoomEngine;
import com.doomviewer.game.ObjectManager;
import com.doomviewer.game.Player;
import com.doomviewer.misc.math.Vector2D;
import com.doomviewer.services.AudioService;
import com.doomviewer.services.CollisionService;
import com.doomviewer.services.GameEngineTmp;
import com.doomviewer.wad.assets.AssetData;
import com.doomviewer.wad.datatypes.Thing;

import java.awt.image.BufferedImage;
import java.util.logging.Logger;

import static com.doomviewer.game.objects.Actions.*;

public abstract class MapObject {
    private static final Logger LOGGER = Logger.getLogger(MapObject.class.getName());

    protected final GameEngineTmp engineTmp;
    protected final ObjectManager objectManager;
    protected final AudioService audioService;
    protected final CollisionService collisionService;

    public Vector2D pos;
    public Vector2D velocity; // For projectiles and moving objects
    public double angle; // degrees, 0 = East, 90 = North (Doom convention)
    public double z;     // z-coordinate of object's bottom, relative to sector floor. Initially 0.
    public double floorHeight; // Current floor height under the object
    public double ceilingHeight; // Current ceiling height over the object

    public MobjType type;
    public MobjInfoDef info;
    public StateNum currentStateNum;
    public StateDef currentStateDef;
    public int ticsRemainingInState;
    public int health;
    public int flags;
    public int thingFlags; // Added to store original Thing flags

    // Rendering related
    private final GameDefinitions gameDefinitions;
    private final AssetData assetData;
    public String currentSpriteLumpName;
    public boolean currentSpriteFullBright;
    public double renderRadius;
    public double renderHeight; // For sprite scaling and clipping.
    public double spriteXOffset; // From patch header, for centering
    public double spriteYOffset; // From patch header, for vertical anchor

    // AI system
    public AIState aiState = AIState.IDLE;
    public int aiTics = 0; // Countdown for current AI action
    public Vector2D lastKnownPlayerPos; // Last position where player was seen
    public double moveSpeed = 1.0; // Units per second
    public double turnSpeed = 90.0; // Degrees per second
    public int alertTics = 0; // How long to stay alert after losing sight of player
    public int attackCooldown = 0; // Prevent attacking too frequently
    private int debugCounter = 0; // Per-object debug counter
    private MapObject target;


    public enum AIState {
        IDLE,        // Standing still, not aware of player
        WANDERING,   // Moving randomly
        CHASING,     // Actively pursuing player
        ATTACKING,   // In attack animation
        SEARCHING,   // Looking for player after losing sight
        DEAD         // Dead, no AI processing
    }


    public MapObject(Thing thing, GameDefinitions gameDefinitions, AssetData assetData, CollisionService collisionService, AudioService audioService, GameEngineTmp engineTmp, ObjectManager objectManager) {
        this.gameDefinitions = gameDefinitions;
        this.assetData = assetData;
        this.collisionService = collisionService; // Store engine instance
        this.audioService = audioService;
        this.engineTmp = engineTmp;
        this.objectManager = objectManager;

        this.info = gameDefinitions.getMobjInfoByDoomedNum(thing.type);
        if (this.info == null) {
            throw new IllegalArgumentException("No MobjInfoDef found for Thing type: " + thing.type);
        }
        this.type = gameDefinitions.doomedNumToMobjType.get(thing.type);

        this.pos = new Vector2D(thing.pos.x, thing.pos.y);
        this.velocity = new Vector2D(0, 0); // Initialize velocity to zero
        this.angle = GameDefinitions.bamsToDegrees((short) thing.angle); // Convert BAMS to degrees
        this.health = this.info.spawnHealth;
        this.flags = this.info.flags;
        this.thingFlags = thing.flags; // Store the original Thing flags
        this.renderRadius = this.info.radius;
        this.renderHeight = this.info.height;

        // Defer floorHeight and z initialization
        // this.floorHeight = engine.getPlayer().floorHeight; // Removed
        // this.z = this.floorHeight; // Removed


        setState(this.info.spawnState); // Initial state set

        // Initialize AI state for enemies
        initializeAI();

        // Pass BSP for floor height lookup
        if (this.type == MobjType.MT_PLAYER) {
            // Player's own floorHeight will be set by its specific logic (e.g., BSP collision in updateHeightAndZ)
            // Player target is usually null or set based on game mode (e.g. another player in DM)
            this.floorHeight = this.collisionService.getSubSectorHeightAt(this.pos.x, this.pos.y);
            this.z = this.floorHeight;
        } else {
            // For non-player objects
            this.floorHeight = this.collisionService.getSubSectorHeightAt(this.pos.x, this.pos.y);
            this.z = this.floorHeight;
        }
        // System.out.println("Initialized " + this.info.name + " at " + this.pos + " z: " + this.z + " floorH: " + this.floorHeight);
    }

    public MapObject getTarget() {
        return target;
    }

    public void setTarget(MapObject target) {
        this.target = target;
    }

    // New method to initialize positioning-dependent fields

    public void update(Player player) {
        // Update projectile movement (only if not a corpse AND is a projectile)
        if (isProjectile() && (flags & MobjFlags.MF_CORPSE) == 0) {
            updateProjectileMovement();
        }

        // Update Z position based on current sector's floor height
        // This is always true, even for corpses (A_Fall handles their floor snapping)
        if ((this.flags & MobjFlags.MF_NOGRAVITY) == 0) {
            this.floorHeight = collisionService.getSubSectorHeightAt(this.pos.x, this.pos.y);
            this.z = this.floorHeight;
        }

        // Handle state transitions (applies to all living objects AND corpses still animating)
        if (ticsRemainingInState > 0 && ticsRemainingInState != Integer.MAX_VALUE) {
            ticsRemainingInState--;
        }

        if (ticsRemainingInState <= 0 && ticsRemainingInState != Integer.MAX_VALUE) {
            if (currentStateDef.action != null) {
                currentStateDef.action.execute(this, objectManager, player, audioService, engineTmp, collisionService);
            }
            setState(currentStateDef.nextState);
        } else if (currentStateDef.action != null) {
            // Only execute continuous actions for living objects, or specific corpse-related actions
            if ((health > 0 && (A_CHASE.equals(currentStateDef.action) || A_LOOK.equals(currentStateDef.action)))
                    || (currentStateDef.action.equals(A_FALL) && (flags & MobjFlags.MF_CORPSE) != 0)) // Example: let A_Fall re-run if needed, though its effect is mostly one-shot
            {
                currentStateDef.action.execute(this, objectManager, player, audioService, engineTmp, collisionService);
            }
        }

        // Update AI for living enemies only (this part should skip for corpses)
        if (health > 0 && isEnemy()) { // isEnemy() check is important
            updateAI(player);
        }
    }

    public void takeDamage(int damage, MapObject inflictor) {
        // Don't damage already dead objects
        if (health <= 0 || (flags & MobjFlags.MF_CORPSE) != 0) {
            return;
        }

        health -= damage;

        if (health <= 0) {
            // Object killed
            if (info.deathState != StateNum.S_NULL) {
                setState(info.deathState);
            }
        } else if (Math.random() < (info.painChance / 255.0)) {
            // Pain chance
            if (info.painState != StateNum.S_NULL) {
                setState(info.painState);
            }
        }
    }

    public void setState(StateNum newStateNum) {
        LOGGER.info(info.name + " changing state from " + currentStateNum + " to " + newStateNum);
        this.currentStateNum = newStateNum;
        this.currentStateDef = gameDefinitions.getState(newStateNum);
        if (this.currentStateDef == null) {
            System.err.println("Error: Could not find state definition for " + newStateNum);
            // Fallback to a known safe state if possible, or throw error
            this.currentStateNum = StateNum.S_NULL;
            this.currentStateDef = gameDefinitions.getState(StateNum.S_NULL);
        }

        this.ticsRemainingInState = this.currentStateDef.tics;
        if (this.ticsRemainingInState == -1) { // Infinite duration state
            this.ticsRemainingInState = Integer.MAX_VALUE; // effectively infinite for game loop
        }

        // Update sprite representation
        this.currentSpriteLumpName = this.currentStateDef.getSpriteLumpName();
        this.currentSpriteFullBright = this.currentStateDef.isFullBright();


        // Cache sprite offsets if needed, from AssetData.Patch.header
        BufferedImage spriteImage = assetData.sprites.get(this.currentSpriteLumpName);
        if (spriteImage != null) { // Should always be found if AssetData is correct
            // The Patch object in AssetData would hold the original offsets.
            // For now, assume sprite origin is center-bottom or use fixed offsets.
            // We need access to PatchHeader associated with this sprite.
            // This is not directly available from BufferedImage.
            // A better way: MapObject stores Patch ref, or AssetData provides offsets.
            // For now, approximate. True centering needs original patch offsets.
            // Assuming AssetData.Patch stores its original width/height before scaling:
            AssetData.Patch patch = assetData.getSpritePatch(this.currentSpriteLumpName); // Need this method in AssetData
            if (patch != null) {
                this.spriteXOffset = patch.header.leftOffset; // Original engine uses this
                this.spriteYOffset = patch.header.topOffset;  // Original engine uses this
            } else {
                this.spriteXOffset = 0; // Fallback
                this.spriteYOffset = 0; // Fallback
            }
        }


        // Execute action associated with entering the new state
        if (this.currentStateDef.action != null) {
            this.currentStateDef.action.execute(this, objectManager, engineTmp.getPlayer(), audioService, engineTmp, collisionService);
        }
    }

    private void initializeAI() {
        if (isEnemy()) {
            aiState = AIState.IDLE;
            aiTics = 70 + (int) (Math.random() * 140); // 2-6 seconds before first action

            // Set enemy-specific AI parameters
            switch (type) {
                case MT_POSSESSED: // Zombieman
                    moveSpeed = 1.5; // Slower, cautious
                    turnSpeed = 120.0;
                    break;
                case MT_SHOTGUY: // Shotgun Guy
                    moveSpeed = 1.8; // Slightly faster than zombieman
                    turnSpeed = 140.0;
                    break;
                case MT_CHAINGUY: // Chaingunner
                    moveSpeed = 1.6; // Similar to shotgun guy
                    turnSpeed = 130.0;
                    break;
                case MT_TROOP: // Imp
                    moveSpeed = 2.2; // Fast and agile
                    turnSpeed = 200.0;
                    break;
                case MT_SERGEANT: // Demon/Pinky
                    moveSpeed = 3.0; // Very fast, aggressive
                    turnSpeed = 180.0;
                    break;
                case MT_SHADOWS: // Spectre (invisible demon)
                    moveSpeed = 3.2; // Slightly faster than regular demon
                    turnSpeed = 190.0;
                    break;
                case MT_HEAD: // Cacodemon
                    moveSpeed = 1.8; // Floating, moderate speed
                    turnSpeed = 150.0;
                    break;
                case MT_SKULL: // Lost Soul
                    moveSpeed = 4.0; // Very fast, charging attacks
                    turnSpeed = 300.0;
                    break;
                case MT_KNIGHT: // Hell Knight
                    moveSpeed = 2.0; // Moderate speed, tough
                    turnSpeed = 160.0;
                    break;
                case MT_BRUISER: // Baron of Hell
                    moveSpeed = 1.8; // Slower but very tough
                    turnSpeed = 150.0;
                    break;
                case MT_FATSO: // Mancubus
                    moveSpeed = 1.2; // Slow and heavy
                    turnSpeed = 100.0;
                    break;
                case MT_BABY: // Arachnotron
                    moveSpeed = 2.5; // Fast spider-like movement
                    turnSpeed = 200.0;
                    break;
                case MT_PAIN: // Pain Elemental
                    moveSpeed = 2.0; // Floating, moderate speed
                    turnSpeed = 160.0;
                    break;
                case MT_SPIDER: // Spider Mastermind
                    moveSpeed = 2.8; // Fast boss movement
                    turnSpeed = 180.0;
                    break;
                case MT_CYBORG: // Cyberdemon
                    moveSpeed = 1.5; // Slow but devastating
                    turnSpeed = 120.0;
                    break;
                case MT_VILE: // Arch-Vile
                    moveSpeed = 2.5; // Fast and dangerous
                    turnSpeed = 220.0;
                    break;
                case MT_WOLFSS: // Wolfenstein SS
                    moveSpeed = 2.0; // Similar to imp
                    turnSpeed = 180.0;
                    break;
                default:
                    moveSpeed = 2.0; // Default
                    turnSpeed = 180.0;
                    break;
            }
        }
    }

    private void updateAI(Player player) {
        // Only process AI for living enemies
        if (health <= 0) {
            aiState = AIState.DEAD;
            return;
        }

        // Skip AI for non-enemies (decorations, items, etc.)
        if (!isEnemy()) {
            return;
        }

        // Get playerMapObject reference
        MapObject playerMapObject = player.getMapObject();
        if (playerMapObject == null) return;

        // Calculate distance to playerMapObject
        double distanceToPlayer = Vector2D.distance(this.pos, playerMapObject.pos);


        // Check if playerMapObject is in line of sight
        boolean canSeePlayer = hasLineOfSight(playerMapObject);

        // Update timers
        if (attackCooldown > 0) attackCooldown--;

        debugCounter++;

        // Update AI state machine
        switch (aiState) {
            case IDLE:
                updateIdleState(playerMapObject, distanceToPlayer, canSeePlayer);
                break;
            case WANDERING:
                updateWanderingState(playerMapObject, distanceToPlayer, canSeePlayer);
                break;
            case CHASING:
                updateChasingState(playerMapObject, distanceToPlayer, canSeePlayer);
                break;
            case ATTACKING:
                updateAttackingState(playerMapObject, distanceToPlayer, canSeePlayer);
                // Don't allow other state changes while attacking
                return;
            case SEARCHING:
                updateSearchingState(playerMapObject, distanceToPlayer, canSeePlayer);
                break;
        }
    }

    private boolean isEnemy() {
        // Check if this is an enemy type
        return type == MobjType.MT_POSSESSED ||  // Zombieman
                type == MobjType.MT_SHOTGUY ||    // Shotgun Guy
                type == MobjType.MT_TROOP ||      // Imp
                type == MobjType.MT_SERGEANT ||   // Demon/Pinky
                type == MobjType.MT_CHAINGUY ||   // Chaingunner
                type == MobjType.MT_SHADOWS ||    // Spectre
                type == MobjType.MT_HEAD ||       // Cacodemon
                type == MobjType.MT_BRUISER ||    // Baron of Hell
                type == MobjType.MT_KNIGHT ||     // Hell Knight
                type == MobjType.MT_SKULL ||      // Lost Soul
                type == MobjType.MT_FATSO ||      // Mancubus
                type == MobjType.MT_BABY ||       // Arachnotron
                type == MobjType.MT_PAIN ||       // Pain Elemental
                type == MobjType.MT_SPIDER ||     // Spider Mastermind
                type == MobjType.MT_CYBORG ||     // Cyberdemon
                type == MobjType.MT_VILE ||       // Arch-Vile
                type == MobjType.MT_WOLFSS;       // Wolfenstein SS
    }

    private boolean hasLineOfSight(MapObject target) {
        // Improved line of sight check using BSP tree
        Vector2D start = this.pos;
        Vector2D end = target.pos;

        // If very close, assume line of sight
        double distance = Vector2D.distance(start, end);
        if (distance < 32.0) return true;

        // Use BSP to check for wall intersections along the line
        return collisionService.hasLineOfSight(start, end);
    }

    private void updateIdleState(MapObject player, double distance, boolean canSeePlayer) {
        if (canSeePlayer && distance < 2048.0) { // Increased detection range to 2048 units
            aiState = AIState.CHASING;
            target = player;
            lastKnownPlayerPos = new Vector2D(player.pos.x, player.pos.y);
            alertTics = 350; // 10 seconds at 35 fps
        } else {
            // Occasionally start wandering
            aiTics--;
            if (aiTics <= 0) {
                aiState = AIState.WANDERING;
                aiTics = 105 + (int) (Math.random() * 210); // 3-9 seconds
            }
        }
    }

    private void updateWanderingState(MapObject player, double distance, boolean canSeePlayer) {
        if (canSeePlayer && distance < 2048.0) { // Same increased detection range
            aiState = AIState.CHASING;
            target = player;
            lastKnownPlayerPos = new Vector2D(player.pos.x, player.pos.y);
            alertTics = 350;
            return;
        }

        aiTics--;
        if (aiTics <= 0) {
            // Pick a random direction and move
            double randomAngle = Math.random() * 360.0;
            moveInDirection(randomAngle);
            aiTics = 35 + (int) (Math.random() * 70); // 1-3 seconds
        }

        // Occasionally go back to idle
        if (Math.random() < 0.02) { // 2% chance per frame
            aiState = AIState.IDLE;
            aiTics = 70 + (int) (Math.random() * 140); // 2-6 seconds
        }
    }

    private void updateChasingState(MapObject player, double distance, boolean canSeePlayer) {
        if (canSeePlayer) {
            lastKnownPlayerPos = new Vector2D(player.pos.x, player.pos.y);
            alertTics = 350; // Reset alert timer

            // Check attack range based on enemy type
            double attackRange = getAttackRange();
            boolean inMeleeRange = distance < 64.0; // Close combat range
            boolean inMissileRange = distance < attackRange;

            // Attack if in range and not on cooldown
            if ((inMeleeRange || inMissileRange) && attackCooldown <= 0) {
                // Higher attack chance and add some debug output
                if (Math.random() < 0.25) { // 25% chance per frame when in range
                    aiState = AIState.ATTACKING;
                    aiTics = 35; // 1 second attack duration
                    attackCooldown = 70 + (int) (Math.random() * 70); // 2-4 second cooldown

                    // Switch to appropriate attack state based on enemy type and range
                    StateNum attackState = getAttackState(inMeleeRange);
                    if (attackState != null) {
                        setState(attackState);
                    }
                    return; // Don't move when attacking
                }
            }

            // Move towards player if not attacking
            double angleToPlayer = Math.toDegrees(Math.atan2(player.pos.y - this.pos.y, player.pos.x - this.pos.x));
            moveInDirection(angleToPlayer);

        } else {
            // Lost sight of player
            alertTics--;
            if (alertTics <= 0) {
                aiState = AIState.SEARCHING;
                aiTics = 210; // 6 seconds to search
            } else if (lastKnownPlayerPos != null) {
                // Move towards last known position
                double angleToLastPos = Math.toDegrees(Math.atan2(lastKnownPlayerPos.y - this.pos.y, lastKnownPlayerPos.x - this.pos.x));
                moveInDirection(angleToLastPos);
            }
        }
    }

    private void updateAttackingState(MapObject player, double distance, boolean canSeePlayer) {
        aiTics--;


        // Check if attack animation is finished by looking at current state
        boolean attackFinished = aiTics <= 0 || isInNonAttackState();

        if (attackFinished) {
            // Attack finished, go back to chasing or searching
            if (canSeePlayer && distance < 2048.0) {
                aiState = AIState.CHASING;
                // Switch back to run state
                StateNum runState = getRunState();
                if (runState != null) {
                    setState(runState);
                }
            } else {
                aiState = AIState.SEARCHING;
                aiTics = 210;
                StateNum runState = getRunState();
                if (runState != null) {
                    setState(runState);
                }
            }
        }

        // Face the player during attack
        if (target != null) {
            double angleToTarget = Math.toDegrees(Math.atan2(target.pos.y - this.pos.y, target.pos.x - this.pos.x));
            this.angle = angleToTarget;
        }

        // Trigger attack effect at specific timing
        if (aiTics == 17) { // Halfway through attack
            // In a full game, this would deal damage to the player
        }
    }

    private boolean isInNonAttackState() {
        // Check if current state is not an attack state
        switch (type) {
            case MT_POSSESSED:
                return !isInRange(currentStateNum.ordinal(), StateNum.S_POSS_ATK1.ordinal(), StateNum.S_POSS_ATK3.ordinal());
            case MT_SHOTGUY:
                return !isInRange(currentStateNum.ordinal(), StateNum.S_SPOS_ATK1.ordinal(), StateNum.S_SPOS_ATK3.ordinal());
            case MT_TROOP:
                return !isInRange(currentStateNum.ordinal(), StateNum.S_TROO_ATK1.ordinal(), StateNum.S_TROO_ATK3.ordinal()) &&
                        !isInRange(currentStateNum.ordinal(), StateNum.S_TROO_MISS1.ordinal(), StateNum.S_TROO_MISS3.ordinal());
            case MT_SERGEANT:
                return !isInRange(currentStateNum.ordinal(), StateNum.S_SARG_ATK1.ordinal(), StateNum.S_SARG_ATK4.ordinal());
            default:
                return true;
        }
    }

    private boolean isInRange(int value, int min, int max) {
        return value >= min && value <= max;
    }

    private StateNum getRunState() {
        switch (type) {
            case MT_POSSESSED:
                return StateNum.S_POSS_RUN1;
            case MT_SHOTGUY:
                return StateNum.S_SPOS_RUN1;
            case MT_CHAINGUY:
                return StateNum.S_CPOS_RUN1;
            case MT_TROOP:
                return StateNum.S_TROO_RUN1;
            case MT_SERGEANT:
            case MT_SHADOWS:
                return StateNum.S_SARG_RUN1;
            case MT_HEAD:
                return StateNum.S_HEAD_RUN1;
            case MT_SKULL:
                return StateNum.S_SKULL_RUN1;
            case MT_KNIGHT:
                return StateNum.S_BOS2_RUN1;
            case MT_BRUISER:
                return StateNum.S_BOSS_RUN1;
            case MT_FATSO:
                return StateNum.S_BOSS_RUN1; // Use Baron for now
            case MT_BABY:
                return StateNum.S_HEAD_RUN1; // Use Cacodemon for now
            case MT_PAIN:
                return StateNum.S_HEAD_RUN1; // Use Cacodemon for now
            case MT_SPIDER:
                return StateNum.S_SPID_RUN1;
            case MT_CYBORG:
                return StateNum.S_CYBER_RUN1;
            case MT_VILE:
                return StateNum.S_HEAD_RUN1; // Use Cacodemon for now
            case MT_WOLFSS:
                return StateNum.S_POSS_RUN1; // Use zombieman
            default:
                return null;
        }
    }

    private void updateSearchingState(MapObject player, double distance, boolean canSeePlayer) {
        if (canSeePlayer && distance < 2048.0) { // Same increased range
            aiState = AIState.CHASING;
            target = player;
            lastKnownPlayerPos = new Vector2D(player.pos.x, player.pos.y);
            alertTics = 350;
            return;
        }

        aiTics--;
        if (aiTics <= 0) {
            // Give up searching, go back to wandering
            aiState = AIState.WANDERING;
            aiTics = 105 + (int) (Math.random() * 210);
            target = null;
            return;
        }

        // Move towards last known position or search randomly
        if (lastKnownPlayerPos != null) {
            double distanceToLastPos = Vector2D.distance(this.pos, lastKnownPlayerPos);
            if (distanceToLastPos > 32.0) {
                double angleToLastPos = Math.toDegrees(Math.atan2(lastKnownPlayerPos.y - this.pos.y, lastKnownPlayerPos.x - this.pos.x));
                moveInDirection(angleToLastPos);
            } else {
                // Reached last known position, search randomly
                double randomAngle = Math.random() * 360.0;
                moveInDirection(randomAngle);
            }
        }
    }

    private void moveInDirection(double angleInDegrees) {
        double deltaTime = engineTmp.getDeltaTime() / 1000.0; // Convert to seconds
        double moveDistance = moveSpeed * deltaTime * 35.0; // Adjust for 35 fps game logic

        double angleRad = Math.toRadians(angleInDegrees);
        Vector2D desiredPos = new Vector2D(
                this.pos.x + Math.cos(angleRad) * moveDistance,
                this.pos.y + Math.sin(angleRad) * moveDistance
        );

        // Use BSP collision detection to get safe movement position (no logging for AI)
        Vector2D safePos = collisionService.getSafeMovementPosition(this.pos, desiredPos, renderRadius, false);

        // Only move if we actually get closer to the desired position
        double currentDistance = Vector2D.distance(this.pos, desiredPos);
        double safeDistance = Vector2D.distance(safePos, desiredPos);

        if (safeDistance < currentDistance || Vector2D.distance(this.pos, safePos) > 1.0) {
            this.pos = safePos;

            // Turn towards movement direction
            turnTowardsAngle(angleInDegrees, deltaTime);
        }
    }

    private void turnTowardsAngle(double targetAngle, double deltaTime) {
        double angleDiff = normalizeAngle(targetAngle - this.angle);
        double maxTurn = turnSpeed * deltaTime * 35.0; // Adjust for 35 fps game logic

        if (Math.abs(angleDiff) <= maxTurn) {
            this.angle = targetAngle;
        } else {
            if (angleDiff > 0) {
                this.angle += maxTurn;
            } else {
                this.angle -= maxTurn;
            }
        }

        this.angle = normalizeAngle(this.angle);
    }

    private double normalizeAngle(double angle) {
        while (angle < 0) angle += 360;
        while (angle >= 360) angle -= 360;
        return angle;
    }

    private boolean isPositionBlocked(double x, double y, DoomEngine engine) {
        // Use new BSP collision detection system
        Vector2D position = new Vector2D(x, y);
        return !engine.getBsp().isPositionValid(position, renderRadius);
    }

    private double getAttackRange() {
        switch (type) {
            case MT_POSSESSED:  // Zombieman - pistol
                return 512.0;
            case MT_SHOTGUY:    // Shotgun Guy - shotgun
                return 320.0;
            case MT_CHAINGUY:   // Chaingunner - chaingun
                return 512.0;
            case MT_TROOP:      // Imp - fireball
                return 448.0;
            case MT_SERGEANT:   // Demon - melee only
            case MT_SHADOWS:    // Spectre - melee only
                return 64.0;
            case MT_HEAD:       // Cacodemon - fireball
                return 512.0;
            case MT_SKULL:      // Lost Soul - charge attack
                return 128.0;
            case MT_KNIGHT:     // Hell Knight - fireball + melee
                return 384.0;
            case MT_BRUISER:    // Baron of Hell - fireball + melee
                return 512.0;
            case MT_FATSO:      // Mancubus - multiple fireballs
                return 640.0;
            case MT_BABY:       // Arachnotron - plasma
                return 576.0;
            case MT_PAIN:       // Pain Elemental - spawn lost souls
                return 256.0;
            case MT_SPIDER:     // Spider Mastermind - chaingun
                return 768.0;
            case MT_CYBORG:     // Cyberdemon - rockets
                return 896.0;
            case MT_VILE:       // Arch-Vile - fire attack
                return 448.0;
            case MT_WOLFSS:     // Wolfenstein SS - pistol
                return 512.0;
            default:
                return 256.0;
        }
    }

    private StateNum getAttackState(boolean inMeleeRange) {
        switch (type) {
            case MT_POSSESSED:  // Zombieman - ranged attack only
                return StateNum.S_POSS_ATK1;
            case MT_SHOTGUY:    // Shotgun Guy - ranged attack only
                return StateNum.S_SPOS_ATK1;
            case MT_CHAINGUY:   // Chaingunner - ranged attack only
                return StateNum.S_CPOS_ATK1;
            case MT_TROOP:      // Imp - melee if close, fireball if far
                if (inMeleeRange) {
                    return StateNum.S_TROO_ATK1;  // Melee attack
                } else {
                    return StateNum.S_TROO_MISS1; // Missile attack
                }
            case MT_SERGEANT:   // Demon - melee only
            case MT_SHADOWS:    // Spectre - melee only
                return inMeleeRange ? StateNum.S_SARG_ATK1 : null;
            case MT_HEAD:       // Cacodemon - ranged fireball
                return StateNum.S_HEAD_ATK1;
            case MT_SKULL:      // Lost Soul - charge attack
                return StateNum.S_SKULL_ATK1;
            case MT_KNIGHT:     // Hell Knight - ranged fireball or melee
                if (inMeleeRange) {
                    return StateNum.S_BOS2_ATK1; // Melee
                } else {
                    return StateNum.S_BOS2_ATK1; // Ranged (same state)
                }
            case MT_BRUISER:    // Baron of Hell - ranged fireball or melee
                if (inMeleeRange) {
                    return StateNum.S_BOSS_ATK1; // Melee
                } else {
                    return StateNum.S_BOSS_ATK1; // Ranged (same state)
                }
            case MT_FATSO:      // Mancubus - multiple fireballs
                return StateNum.S_BOSS_ATK1; // Use Baron attack for now
            case MT_BABY:       // Arachnotron - plasma
                return StateNum.S_HEAD_ATK1; // Use Cacodemon attack for now
            case MT_PAIN:       // Pain Elemental - spawn lost souls
                return StateNum.S_HEAD_ATK1; // Use Cacodemon attack for now
            case MT_SPIDER:     // Spider Mastermind - chaingun
                return StateNum.S_SPID_ATK1;
            case MT_CYBORG:     // Cyberdemon - rockets
                return StateNum.S_CYBER_ATK1;
            case MT_VILE:       // Arch-Vile - fire attack
                return StateNum.S_HEAD_ATK1; // Use Cacodemon attack for now
            case MT_WOLFSS:     // Wolfenstein SS - pistol
                return StateNum.S_POSS_ATK1; // Use zombieman attack
            default:
                return null;
        }
    }

    public boolean isProjectile() {
        return (flags & MobjFlags.MF_MISSILE) != 0 ||
                type == MobjType.MT_TROOPSHOT ||
                type == MobjType.MT_PUFF ||
                type == MobjType.MT_BLOOD;
    }

    private void updateProjectileMovement() {
        double deltaTime = engineTmp.getDeltaTime() / 1000.0; // Convert to seconds

        // Move projectile
        Vector2D newPos = pos.add(velocity.scale(deltaTime));

        // Check for collisions
        if (type == MobjType.MT_TROOPSHOT) {
            // Check collision with target (player) first
            final MapObject target = this.getTarget();
            if (target != null) {
                double distanceToTarget = Vector2D.distance(newPos, target.pos);
                if (distanceToTarget < renderRadius + target.renderRadius) {
                    // Hit target!
                    explodeProjectile();
                    return;
                }
            }

            // Check collision with walls using BSP (no logging for projectiles)
            if (collisionService.isMovementBlocked(pos, newPos, renderRadius, false)) {
                // Hit wall
                explodeProjectile();
                return;
            }

            // No collision, move to new position
            pos = newPos;
        } else {
            // Effects (puff, blood) don't move, just update position
            pos = newPos;
        }
    }

    private void explodeProjectile() {
        if (info.deathState != StateNum.S_NULL) {
            setState(info.deathState);
            velocity = new Vector2D(0, 0); // Stop moving

            // Deal damage if it hit the target
            if (target != null) {
                double distanceToTarget = Vector2D.distance(pos, target.pos);
                if (distanceToTarget < renderRadius + target.renderRadius) {
                    target.health -= info.damage;
                    if (target.health <= 0) {
                        target.setState(target.info.deathState);
                    } else if (Math.random() < (target.info.painChance / 255.0)) {
                        target.setState(target.info.painState);
                    }
                }
            }

        }
    }

    public GameDefinitions getGameDefinitions() {
        return gameDefinitions;
    }
}

