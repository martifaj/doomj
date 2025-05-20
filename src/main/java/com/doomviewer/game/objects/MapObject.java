package com.doomviewer.game.objects;

import com.doomviewer.game.BSP;
import com.doomviewer.misc.math.Vector2D;
import com.doomviewer.game.DoomEngine;
import com.doomviewer.wad.assets.AssetData;
import com.doomviewer.wad.datatypes.Thing;

import java.awt.image.BufferedImage;

public class MapObject {
    public Vector2D pos;
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
    private GameDefinitions gameDefinitions;
    private AssetData assetData;
    private final DoomEngine engine;
    public String currentSpriteLumpName;
    public boolean currentSpriteFullBright;
    public double renderRadius;
    public double renderHeight; // For sprite scaling and clipping.
    public double spriteXOffset; // From patch header, for centering
    public double spriteYOffset; // From patch header, for vertical anchor

    private static final double GAME_TICS_PER_SECOND = 35.0;
    public MapObject target;
    
    // AI system
    public AIState aiState = AIState.IDLE;
    public int aiTics = 0; // Countdown for current AI action
    public Vector2D lastKnownPlayerPos; // Last position where player was seen
    public double moveSpeed = 1.0; // Units per second
    public double turnSpeed = 90.0; // Degrees per second
    public int alertTics = 0; // How long to stay alert after losing sight of player
    public int attackCooldown = 0; // Prevent attacking too frequently
    private int debugCounter = 0; // Per-object debug counter
    
    public enum AIState {
        IDLE,        // Standing still, not aware of player
        WANDERING,   // Moving randomly
        CHASING,     // Actively pursuing player
        ATTACKING,   // In attack animation
        SEARCHING,   // Looking for player after losing sight
        DEAD         // Dead, no AI processing
    }


    public MapObject(Thing thing, GameDefinitions gameDefinitions, AssetData assetData, DoomEngine engine) {
        this.gameDefinitions = gameDefinitions;
        this.assetData = assetData;
        this.engine = engine; // Store engine instance

        this.info = gameDefinitions.getMobjInfoByDoomedNum(thing.type);
        if (this.info == null) {
            throw new IllegalArgumentException("No MobjInfoDef found for Thing type: " + thing.type);
        }
        this.type = gameDefinitions.doomedNumToMobjType.get(thing.type);

        this.pos = new Vector2D(thing.pos.x, thing.pos.y);
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

        // Call initializeHeightAndTarget after engine and player are expected to be ready
        // This is now called from ObjectManager after all objects are created.
    }

    // New method to initialize positioning-dependent fields
    public void initializeHeightAndTarget(BSP bsp) { // Pass BSP for floor height lookup
        if (this.type == MobjType.MT_PLAYER) {
            // Player's own floorHeight will be set by its specific logic (e.g., BSP collision in updateHeightAndZ)
            // Player target is usually null or set based on game mode (e.g. another player in DM)
            this.floorHeight = bsp.getSubSectorHeightAt(this.pos.x, this.pos.y);
            this.z = this.floorHeight;
            this.target = null; // Player usually doesn't have a default AI target
        } else {
            // For non-player objects
            this.floorHeight = bsp.getSubSectorHeightAt(this.pos.x, this.pos.y);
            this.z = this.floorHeight;
            if (engine != null && engine.getPlayer() != null) {
                this.target = engine.getPlayer(); // Default target for monsters
            } else {
                this.target = null; // Fallback if player isn't ready
            }
        }
        // System.out.println("Initialized " + this.info.name + " at " + this.pos + " z: " + this.z + " floorH: " + this.floorHeight);
    }

    public void update(DoomEngine engine) {
        // Update Z position based on current sector's floor height
        // This should ideally be done if the object moves to a new sector or if floor moves.
        // For simplicity, let's assume it's updated if it's not MF_NOGRAVITY
        if ((this.flags & MobjFlags.MF_NOGRAVITY) == 0 && engine.getBsp() != null) {
            this.floorHeight = engine.getBsp().getSubSectorHeightAt(this.pos.x, this.pos.y);
            this.z = this.floorHeight; // Stick to floor if gravity applies
        }

        if (ticsRemainingInState > 0 && ticsRemainingInState != Integer.MAX_VALUE) {
            ticsRemainingInState--;
        }

        if (ticsRemainingInState <= 0) {
            // Execute action of the *current* state before transitioning (Doom behavior often puts action on state entry/during)
            // But info.c suggests action applies *to* the state, and then nextstate is chosen.
            // For simplicity: action of current state (if any) might have already run or runs now.
            // Then transition.
            if (currentStateDef.action != null) {
                currentStateDef.action.execute(this, engine);
            }
            setState(currentStateDef.nextState);
        } else {
            // If state has an action that runs every tic (less common for monsters, more for projectiles/player anim)
            // For now, assume action is primarily on state entry/change.
            // Some actions like A_Chase should run every tic the monster is in a CHASE sub-state.
            // This is handled by the action itself if it's called every frame the state is active,
            // or if the state transitions to itself.
            if (currentStateDef.action != null) { // Could call A_Chase here if in run state
                // For example, if in a RUN state, call A_Chase
                String actionName = getActionName(currentStateDef.action);
                if ("A_Chase".equals(actionName) || "A_Look".equals(actionName)) { // Crude check
                    currentStateDef.action.execute(this, engine);
                }
            }
        }

        // Update floor/ceiling height (simplified for now)
        // In a full game, this would involve checking the sector the object is in.
        // For now, we'll use a placeholder or assume it's updated externally.
        // Update z based on gravity if MF_NOGRAVITY is not set.
        // if ((this.flags & MobjFlags.MF_NOGRAVITY) == 0) { // Logic moved up
            // Simplified: try to stay on floor. A full physics step is needed.
            // this.z = current_sector_floor_height
        // }
        
        // Update AI for enemies
        updateAI(engine);
    }

    private String getActionName(MobjAction action) {
        // This is a hacky way to get action names for logic.
        // A proper system would use an enum or map action objects to names.
        if (action == Actions.A_LOOK_ACTION) return "A_Look";
        if (action == Actions.A_CHASE_ACTION) return "A_Chase";
        // ... add others as needed, e.g.:
        // if (action == Actions.A_FACE_TARGET_ACTION) return "A_FaceTarget";
        // if (action == Actions.A_POS_ATTACK_ACTION) return "A_PosAttack";
        // if (action == Actions.A_SCREAM_ACTION) return "A_Scream";
        // if (action == Actions.A_PAIN_ACTION) return "A_Pain";
        // if (action == Actions.A_FALL_ACTION) return "A_Fall";
        // if (action == Actions.A_XSCREAM_ACTION) return "A_XScream";
        if (action == Actions.NULL_ACTION) return "NULL_ACTION";

        return "UNKNOWN";
    }


    public void setState(StateNum newStateNum) {
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
            this.currentStateDef.action.execute(this, engine);
        }
    }

    // Helper to convert BAMS from Thing to degrees
    private double convertAngleFromThing(int thingAngle) {
        // Thing angle: 0=East, 90=North, 180=West, 270=South (clockwise from East in BAMS)
        // Standard math angle: 0=East, 90=North (counter-clockwise from East)
        // So, they are compatible if BAMS is scaled.
        return ((thingAngle & 0xFFFF) / 65536.0) * 360.0;
    }
    
    private void initializeAI() {
        if (isEnemy()) {
            aiState = AIState.IDLE;
            aiTics = 70 + (int)(Math.random() * 140); // 2-6 seconds before first action
            
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
                case MT_TROOP: // Imp
                    moveSpeed = 2.2; // Fast and agile
                    turnSpeed = 200.0;
                    break;
                case MT_SERGEANT: // Demon
                    moveSpeed = 3.0; // Very fast, aggressive
                    turnSpeed = 180.0;
                    break;
                default:
                    moveSpeed = 2.0; // Default
                    turnSpeed = 180.0;
                    break;
            }
        }
    }

    private void updateAI(DoomEngine engine) {
        // Only process AI for living enemies
        if (health <= 0) {
            aiState = AIState.DEAD;
            return;
        }
        
        // Skip AI for non-enemies (decorations, items, etc.)
        if (!isEnemy()) {
            return;
        }
        
        // Get player reference
        MapObject player = engine.getPlayer().getMapObject();
        if (player == null) return;
        
        // Calculate distance to player
        double distanceToPlayer = Vector2D.distance(this.pos, player.pos);
        
        // Debug player position tracking
        if (type == MobjType.MT_POSSESSED && debugCounter % 120 == 0) { // Every ~4 seconds
            System.out.println("Zombieman[" + hashCode() + "] tracking player at (" + (int)player.pos.x + "," + (int)player.pos.y + ")");
        }
        
        // Check if player is in line of sight
        boolean canSeePlayer = hasLineOfSight(player, engine);
        
        // Update timers
        if (attackCooldown > 0) attackCooldown--;
        
        // Debug output for zombieman (per-object counter)
        if (type == MobjType.MT_POSSESSED && ++debugCounter % 60 == 0) { // Every ~2 seconds
            System.out.println("Zombieman[" + hashCode() + "] AI: State=" + aiState + ", Distance=" + (int)distanceToPlayer + 
                             ", CanSee=" + canSeePlayer + ", AttackCD=" + attackCooldown);
        }
        
        // Update AI state machine
        switch (aiState) {
            case IDLE:
                updateIdleState(player, distanceToPlayer, canSeePlayer);
                break;
            case WANDERING:
                updateWanderingState(player, distanceToPlayer, canSeePlayer, engine);
                break;
            case CHASING:
                updateChasingState(player, distanceToPlayer, canSeePlayer, engine);
                break;
            case ATTACKING:
                updateAttackingState(player, distanceToPlayer, canSeePlayer);
                // Don't allow other state changes while attacking
                return;
            case SEARCHING:
                updateSearchingState(player, distanceToPlayer, canSeePlayer, engine);
                break;
        }
    }
    
    private boolean isEnemy() {
        // Check if this is an enemy type (simplified check)
        return type == MobjType.MT_POSSESSED || // Zombieman
               type == MobjType.MT_SHOTGUY ||   // Sergeant
               type == MobjType.MT_TROOP ||     // Imp
               type == MobjType.MT_SERGEANT;    // Demon
    }
    
    private boolean hasLineOfSight(MapObject target, DoomEngine engine) {
        // Improved line of sight check using BSP tree
        Vector2D start = this.pos;
        Vector2D end = target.pos;
        
        // If very close, assume line of sight
        double distance = Vector2D.distance(start, end);
        if (distance < 32.0) return true;
        
        // Use BSP to check for wall intersections along the line
        return engine.getBsp().hasLineOfSight(start, end);
    }
    
    private void updateIdleState(MapObject player, double distance, boolean canSeePlayer) {
        if (canSeePlayer && distance < 2048.0) { // Increased detection range to 2048 units
            aiState = AIState.CHASING;
            target = player;
            lastKnownPlayerPos = new Vector2D(player.pos.x, player.pos.y);
            alertTics = 350; // 10 seconds at 35 fps
            System.out.println("Zombieman detected player! Switching to CHASING. Distance: " + (int)distance);
        } else {
            // Occasionally start wandering
            aiTics--;
            if (aiTics <= 0) {
                aiState = AIState.WANDERING;
                aiTics = 105 + (int)(Math.random() * 210); // 3-9 seconds
            }
        }
    }
    
    private void updateWanderingState(MapObject player, double distance, boolean canSeePlayer, DoomEngine engine) {
        if (canSeePlayer && distance < 2048.0) { // Same increased detection range
            aiState = AIState.CHASING;
            target = player;
            lastKnownPlayerPos = new Vector2D(player.pos.x, player.pos.y);
            alertTics = 350;
            System.out.println("Zombieman spotted player while wandering! Switching to CHASING. Distance: " + (int)distance);
            return;
        }
        
        aiTics--;
        if (aiTics <= 0) {
            // Pick a random direction and move
            double randomAngle = Math.random() * 360.0;
            moveInDirection(randomAngle, engine);
            aiTics = 35 + (int)(Math.random() * 70); // 1-3 seconds
        }
        
        // Occasionally go back to idle
        if (Math.random() < 0.02) { // 2% chance per frame
            aiState = AIState.IDLE;
            aiTics = 70 + (int)(Math.random() * 140); // 2-6 seconds
        }
    }
    
    private void updateChasingState(MapObject player, double distance, boolean canSeePlayer, DoomEngine engine) {
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
                    attackCooldown = 70 + (int)(Math.random() * 70); // 2-4 second cooldown
                    
                    // Switch to appropriate attack state based on enemy type and range
                    StateNum attackState = getAttackState(inMeleeRange);
                    if (attackState != null) {
                        setState(attackState);
                        System.out.println(info.name + " attacking! Distance: " + (int)distance + 
                                         " (Melee: " + inMeleeRange + ", Range: " + inMissileRange + ") -> State: " + attackState);
                    }
                    return; // Don't move when attacking
                }
            }
            
            // Move towards player if not attacking
            double angleToPlayer = Math.toDegrees(Math.atan2(player.pos.y - this.pos.y, player.pos.x - this.pos.x));
            moveInDirection(angleToPlayer, engine);
            
        } else {
            // Lost sight of player
            alertTics--;
            if (alertTics <= 0) {
                aiState = AIState.SEARCHING;
                aiTics = 210; // 6 seconds to search
            } else if (lastKnownPlayerPos != null) {
                // Move towards last known position
                double angleToLastPos = Math.toDegrees(Math.atan2(lastKnownPlayerPos.y - this.pos.y, lastKnownPlayerPos.x - this.pos.x));
                moveInDirection(angleToLastPos, engine);
            }
        }
    }
    
    private void updateAttackingState(MapObject player, double distance, boolean canSeePlayer) {
        aiTics--;
        
        // Debug attack state (less spam)
        if (aiTics % 15 == 0) {
            System.out.println(info.name + "[" + hashCode() + "] ATTACKING! Tics remaining: " + aiTics);
        }
        
        // Check if attack animation is finished by looking at current state
        boolean attackFinished = aiTics <= 0 || isInNonAttackState();
        
        if (attackFinished) {
            // Attack finished, go back to chasing or searching
            System.out.println(info.name + "[" + hashCode() + "] attack finished!");
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
            System.out.println("BOOM! " + info.name + "[" + hashCode() + "] attacks player!");
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
            case MT_POSSESSED: return StateNum.S_POSS_RUN1;
            case MT_SHOTGUY: return StateNum.S_SPOS_RUN1;
            case MT_TROOP: return StateNum.S_TROO_RUN1;
            case MT_SERGEANT: return StateNum.S_SARG_RUN1;
            default: return null;
        }
    }
    
    private void updateSearchingState(MapObject player, double distance, boolean canSeePlayer, DoomEngine engine) {
        if (canSeePlayer && distance < 2048.0) { // Same increased range
            aiState = AIState.CHASING;
            target = player;
            lastKnownPlayerPos = new Vector2D(player.pos.x, player.pos.y);
            alertTics = 350;
            System.out.println("Zombieman found player while searching! Switching to CHASING. Distance: " + (int)distance);
            return;
        }
        
        aiTics--;
        if (aiTics <= 0) {
            // Give up searching, go back to wandering
            aiState = AIState.WANDERING;
            aiTics = 105 + (int)(Math.random() * 210);
            target = null;
            return;
        }
        
        // Move towards last known position or search randomly
        if (lastKnownPlayerPos != null) {
            double distanceToLastPos = Vector2D.distance(this.pos, lastKnownPlayerPos);
            if (distanceToLastPos > 32.0) {
                double angleToLastPos = Math.toDegrees(Math.atan2(lastKnownPlayerPos.y - this.pos.y, lastKnownPlayerPos.x - this.pos.x));
                moveInDirection(angleToLastPos, engine);
            } else {
                // Reached last known position, search randomly
                double randomAngle = Math.random() * 360.0;
                moveInDirection(randomAngle, engine);
            }
        }
    }
    
    private void moveInDirection(double angleInDegrees, DoomEngine engine) {
        double deltaTime = engine.getDeltaTime() / 1000.0; // Convert to seconds
        double moveDistance = moveSpeed * deltaTime * 35.0; // Adjust for 35 fps game logic
        
        double angleRad = Math.toRadians(angleInDegrees);
        double newX = this.pos.x + Math.cos(angleRad) * moveDistance;
        double newY = this.pos.y + Math.sin(angleRad) * moveDistance;
        
        // Simple collision check - don't move into walls
        if (!isPositionBlocked(newX, newY, engine)) {
            this.pos.x = newX;
            this.pos.y = newY;
            
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
        // Simple collision check using BSP tree
        // For now, just check if the position is in a valid sector
        // A more sophisticated implementation would check for wall collisions
        try {
            double floorHeight = engine.getBsp().getSubSectorHeightAt(x, y);
            return false; // If we can get floor height, position is valid
        } catch (Exception e) {
            return true; // If we can't get floor height, position is likely blocked
        }
    }
    
    private double getAttackRange() {
        switch (type) {
            case MT_POSSESSED:  // Zombieman - pistol
                return 512.0;
            case MT_SHOTGUY:    // Shotgun Guy - shotgun
                return 320.0;
            case MT_TROOP:      // Imp - fireball
                return 448.0;
            case MT_SERGEANT:   // Demon - melee only
                return 64.0;
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
            case MT_TROOP:      // Imp - melee if close, fireball if far
                if (inMeleeRange) {
                    return StateNum.S_TROO_ATK1;  // Melee attack
                } else {
                    return StateNum.S_TROO_MISS1; // Missile attack
                }
            case MT_SERGEANT:   // Demon - melee only
                return inMeleeRange ? StateNum.S_SARG_ATK1 : null;
            default:
                return null;
        }
    }
}

