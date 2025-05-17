package com.doomengine.game;

import com.doomengine.misc.Constants;
import com.doomengine.geometry.Vector2D;
import com.doomengine.services.CollisionService;
import com.doomengine.wad.WADDataService;
import com.doomengine.wad.datatypes.Linedef;
import com.doomengine.wad.datatypes.Sector;

import java.util.logging.Logger;

public class Door {
    private static final Logger LOGGER = Logger.getLogger(Door.class.getName());

    public enum DoorType {
        NORMAL,         // Opens when used, closes after delay
        KEY_LOCKED,     // Requires a key to open
        TRIGGERED,      // Opens via switch/trigger
        AUTOMATIC       // Opens when approached
    }

    public enum DoorState {
        CLOSED,
        OPENING,
        OPEN,
        CLOSING,
        BLOCKED        // Something is blocking the door
    }

    private final Linedef linedef;
    private final Sector sector;
    private final DoorType doorType;
    private final KeyType requiredKey; // null if no key required
    private final WADDataService wadDataService;
    private final CollisionService collisionService;

    private DoorState state;
    private double currentCeilHeight;
    private double targetCeilHeight;
    private final double openCeilHeight;   // Ceiling height when fully open
    private final double closedCeilHeight; // Ceiling height when fully closed
    private final double speed;
    private long openTime;
    private final long stayOpenDuration;

    // Door timing constants
    private static final double DOOR_SPEED = 8.0; // Units per frame (increased for faster doors)
    private static final long STAY_OPEN_TIME = 8000; // 8 seconds

    public Door(Linedef linedef, Sector sector, DoorType doorType, KeyType requiredKey, WADDataService wadDataService, CollisionService collisionService) {
        this.linedef = linedef;
        this.sector = sector;
        this.doorType = doorType;
        this.requiredKey = requiredKey;
        this.wadDataService = wadDataService;
        this.collisionService = collisionService;

        this.state = DoorState.CLOSED;

        // Store original heights for proper door behavior
        double originalCeilHeight = sector.ceilHeight;
        double originalFloorHeight = sector.floorHeight;

        // Determine if door starts open or closed based on original heights
        if (Math.abs(originalCeilHeight - originalFloorHeight) <= 4) {
            // Door starts closed (ceiling near floor)
            this.closedCeilHeight = originalCeilHeight;
            this.openCeilHeight = originalFloorHeight + 128.0; // Standard door height
            this.currentCeilHeight = originalCeilHeight;
            this.targetCeilHeight = originalCeilHeight;
        } else {
            // Door starts open (ceiling above floor)
            this.openCeilHeight = originalCeilHeight;
            this.closedCeilHeight = originalFloorHeight + 4.0;
            this.currentCeilHeight = originalCeilHeight;  // Keep it at original (open) height
            this.targetCeilHeight = originalCeilHeight;
            this.state = DoorState.OPEN;
            this.openTime = System.currentTimeMillis(); // Set open time so it can auto-close
        }

        sector.ceilHeight = (short) currentCeilHeight;
        this.speed = DOOR_SPEED;
        this.stayOpenDuration = STAY_OPEN_TIME;
    }

    public boolean canOpen(Player player) {
        // Key doors require the key only when closed
        if (doorType == DoorType.KEY_LOCKED && requiredKey != null && state == DoorState.CLOSED) {
            return player.hasKey(requiredKey);
        }

        return true; // Can always interact with doors (open/close)
    }

    public boolean tryOpen(Player player) {
        if (!canOpen(player)) {
            if (doorType == DoorType.KEY_LOCKED && requiredKey != null && !player.hasKey(requiredKey)) {
                LOGGER.info("You need the " + requiredKey.name + " to open this door!");
                // TODO: Play "oof" sound
            }
            return false;
        }

        // Handle door interaction based on current state
        switch (state) {
            case CLOSED:
            case CLOSING:
                open();
                break;
            case OPEN:
                // In classic DOOM, using an open door resets the timer to keep it open longer
                openTime = System.currentTimeMillis();
                LOGGER.info("Door use - resetting open timer");
                break;
            case OPENING:
                // Do nothing while door is still opening
                break;
            case BLOCKED:
                // Try to open blocked door
                open();
                break;
        }
        return true;
    }

    public void open() {
        if (state == DoorState.CLOSED || state == DoorState.CLOSING) {
            state = DoorState.OPENING;
            targetCeilHeight = openCeilHeight;
            LOGGER.info("Door opening from " + currentCeilHeight + " to " + targetCeilHeight + " (speed: " + speed + ")");
            // TODO: Play door opening sound
        }
    }

    public void close() {
        if (state == DoorState.OPEN || state == DoorState.OPENING) {
            state = DoorState.CLOSING;
            targetCeilHeight = closedCeilHeight;
            // TODO: Play door closing sound
            LOGGER.fine("Door closing...");
        }
    }

    public void update(Player player) {
        // Reduced debug logging
        if (state == DoorState.OPENING || state == DoorState.CLOSING) {
            // Only log occasionally to reduce spam
            if (Math.random() < 0.1) { // 10% chance to log
                LOGGER.info("Door update: state=" + state + ", current=" + currentCeilHeight + ", target=" + targetCeilHeight);
            }
        }
        switch (state) {
            case OPENING:
                currentCeilHeight += speed;
                if (currentCeilHeight >= targetCeilHeight) {
                    currentCeilHeight = targetCeilHeight;
                    state = DoorState.OPEN;
                    openTime = System.currentTimeMillis();
                    LOGGER.info("Door fully open at height " + currentCeilHeight + " at time " + openTime);
                }
                break;

            case OPEN:
                // Check if door should close automatically
                if (doorType == DoorType.NORMAL || doorType == DoorType.AUTOMATIC) {
                    long timeOpen = System.currentTimeMillis() - openTime;
                    if (timeOpen > stayOpenDuration) {
                        LOGGER.info("Door auto-closing after " + timeOpen + "ms (stay open duration: " + stayOpenDuration + "ms)");
                        close();
                    }
                }
                break;

            case CLOSING:
                if (isPlayerInDoorSector(player)) { // Now calls the new implementation
                    state = DoorState.OPENING;
                    targetCeilHeight = openCeilHeight;
                    LOGGER.info("Door blocked by player, reopening");
                    break;
                }

                currentCeilHeight -= speed;
                if (currentCeilHeight <= targetCeilHeight) {
                    currentCeilHeight = targetCeilHeight;
                    state = DoorState.CLOSED;
                }
                break;

            case CLOSED:
            case BLOCKED:
                // No update needed
                break;
        }

        // Update the sector's ceiling height to match door position
        sector.ceilHeight = (short) currentCeilHeight;
    }

    // Getters
    public DoorState getState() {
        return state;
    }

    public DoorType getDoorType() {
        return doorType;
    }

    public KeyType getRequiredKey() {
        return requiredKey;
    }

    public Linedef getLinedef() {
        return linedef;
    }

    public Sector getSector() {
        return sector;
    }

    public double getCurrentCeilHeight() {
        return currentCeilHeight;
    }

    public boolean isBlocking() {
        // Door blocks when ceiling is low (near floor level)
        double passageHeight = currentCeilHeight - sector.floorHeight;
        boolean blocking = passageHeight < 56.0; // DOOM player height is ~56 units

        if ((state == DoorState.OPEN || state == DoorState.OPENING) && Math.random() < 0.1) {
            LOGGER.info("Door blocking check: passageHeight=" + passageHeight + ", blocking=" + blocking + ", state=" + state + ", currentCeil=" + currentCeilHeight + ", floorHeight=" + sector.floorHeight);
        }

        return blocking;
    }

    public boolean isPlayerInDoorSector(Player player) {
        if (player == null) {
            return false;
        }

        // 1. Check for horizontal overlap between player's collision cylinder and this door's linedef.
        Vector2D doorStart = wadDataService.vertexes.get(linedef.startVertexId);
        Vector2D doorEnd = wadDataService.vertexes.get(linedef.endVertexId);
        boolean playerOverlapDoorLinedefHorizontally = collisionService.circleIntersectsLineSegment(
                player.pos, player.renderRadius, doorStart, doorEnd
        );

        // 2. If horizontally overlapping, check if the door's current ceiling is lower than the player's head.
        // This means the door is physically trying to close into the player's space.
        // Add a small tolerance (+1.0) to prevent numerical precision issues leading to jiggling.
        double playerTopZ = player.z + Constants.PLAYER_HEIGHT;
        boolean doorCurrentCeilingIsLowerThanPlayerHead = currentCeilHeight < playerTopZ + 1.0;

        // Player is "in the way" if they overlap horizontally AND the door's current ceiling would hit their head.
        // This check is specific to the "reopen" logic for a closing door.
        boolean playerInTheWay = playerOverlapDoorLinedefHorizontally && doorCurrentCeilingIsLowerThanPlayerHead;

        if (playerInTheWay) {
            LOGGER.info("Player blocking " + this.linedef.lineType + " door (id " + this.linedef.startVertexId + "-" + this.linedef.endVertexId + ") reopening. " +
                    "Player pos: " + player.pos + ", Player top Z: " + playerTopZ + ", Door ceil: " + currentCeilHeight);
            return true;
        }
        return false;
    }
}