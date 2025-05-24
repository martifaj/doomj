package com.doomviewer.game;

import com.doomviewer.misc.math.Vector2D;
import com.doomviewer.services.CollisionService;
import com.doomviewer.services.DoorService;
import com.doomviewer.wad.WADDataService;
import com.doomviewer.wad.datatypes.Linedef;
import com.doomviewer.wad.datatypes.Sector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class DoorManager implements DoorService {
    private static final Logger LOGGER = Logger.getLogger(DoorManager.class.getName());
    private final List<Door> doors;
    private final Map<Integer, Door> linedefToDoor; // Map linedef index to door
    private Map<Sector, Door> sectorToDoor; // Map sector to its door
    private final WADDataService wadDataService;
    private final CollisionService collisionService;

    public DoorManager(WADDataService wadDataService, CollisionService collisionService) {
        this.wadDataService = wadDataService;
        this.collisionService = collisionService;
        this.doors = new ArrayList<>();
        this.linedefToDoor = new HashMap<>();
        this.sectorToDoor = new HashMap<>();

        initializeDoors();
    }

    private void initializeDoors() {
        Map<Sector, List<Linedef>> sectorToLinedefs = new HashMap<>();

        // First pass: group linedefs by their door sector
        for (int i = 0; i < wadDataService.linedefs.size(); i++) {
            Linedef linedef = wadDataService.linedefs.get(i);
            if (!isDoorType(linedef.lineType)) continue;

            Sector doorSector = getDoorSector(linedef);
            if (doorSector != null) {
                sectorToLinedefs.computeIfAbsent(doorSector, k -> new ArrayList<>()).add(linedef);
            }
        }

        // Second pass: create one Door object per sector, tracking all its linedefs
        for (Map.Entry<Sector, List<Linedef>> entry : sectorToLinedefs.entrySet()) {
            Sector sector = entry.getKey();
            List<Linedef> linedefs = entry.getValue();

            // Use the first linedef to determine door type
            Linedef primaryLinedef = linedefs.get(0);
            Door door = createDoorFromLinedef(primaryLinedef, getLinedefIndex(primaryLinedef));

            if (door != null) {
                doors.add(door);
                // Map ALL linedefs of this door to the same Door object
                for (Linedef linedef : linedefs) {
                    int linedefIndex = getLinedefIndex(linedef);
                    linedefToDoor.put(linedefIndex, door);
                    LOGGER.info("Mapped linedef " + linedefIndex + " to door for sector with ceiling " +
                            sector.ceilHeight + " and floor " + sector.floorHeight);
                }
            }
        }

        LOGGER.info("Initialized " + doors.size() + " doors");
    }

    private boolean isDoorType(int lineType) {
        switch (lineType) {
            case 1:   // Door: open, wait, close
            case 2:   // Door: open, stay open
            case 3:   // Door: close
            case 4:   // Door: open, wait, close (fast)
            case 26:  // Door: blue key
            case 27:  // Door: yellow key
            case 28:  // Door: red key
            case 32:  // Door: blue skull key
            case 33:  // Door: red skull key
            case 34:  // Door: yellow skull key
                return true;
            default:
                return false;
        }
    }

    public boolean isDoorSector(Sector sector) {
        return sectorToDoor.containsKey(sector);
    }

    public Door getDoorBySector(Sector sector) {
        return sectorToDoor.get(sector);
    }

    private Door createDoorFromLinedef(Linedef linedef, int linedefIndex) {
        // DOOM linedef special values for doors
        switch (linedef.lineType) {
            case 1:   // Door: open, wait, close
                return createNormalDoor(linedef);
            case 26:  // Door: blue key
                return createKeyDoor(linedef, KeyType.BLUE_KEY);
            case 27:  // Door: yellow key  
                return createKeyDoor(linedef, KeyType.YELLOW_KEY);
            case 28:  // Door: red key
                return createKeyDoor(linedef, KeyType.RED_KEY);
            case 32:  // Door: blue skull key
                return createKeyDoor(linedef, KeyType.BLUE_SKULL);
            case 33:  // Door: red skull key
                return createKeyDoor(linedef, KeyType.RED_SKULL);
            case 34:  // Door: yellow skull key
                return createKeyDoor(linedef, KeyType.YELLOW_SKULL);
            case 2:   // Door: open, stay open
            case 3:   // Door: close
            case 4:   // Door: open, wait, close (fast)
                return createNormalDoor(linedef);
            default:
                return null; // Not a door
        }
    }

    private Door createNormalDoor(Linedef linedef) {
        Sector sector = getDoorSector(linedef);
        if (sector != null) {
            LOGGER.info("Creating door with sector - floor: " + sector.floorHeight + ", ceiling: " + sector.ceilHeight + ", floor texture: " + sector.floorTexture + ", ceiling texture: " + sector.ceilTexture);
            return new Door(linedef, sector, Door.DoorType.NORMAL, null, wadDataService, collisionService);
        }
        return null;
    }

    private Door createKeyDoor(Linedef linedef, KeyType keyType) {
        Sector sector = getDoorSector(linedef);
        if (sector != null) {
            return new Door(linedef, sector, Door.DoorType.KEY_LOCKED, keyType, wadDataService, collisionService);
        }
        return null;
    }

    private Sector getDoorSector(Linedef linedef) {
        LOGGER.info("Getting door sector for linedef with tag " + linedef.sectorTag +
                ", frontSidedef: " + linedef.frontSidedefId + ", backSidedef: " + linedef.backSidedefId);

        if (linedef.sectorTag != 0) {
            // Tagged door - find sector with matching tag
            for (Sector sector : wadDataService.sectors) {
                if (sector.tag == linedef.sectorTag) {
                    return sector;
                }
            }
        }

        // For immediate doors (tag 0), we need the actual door sector
        // The door sector is the one with ceiling close to floor
        Sector frontSector = null;
        Sector backSector = null;

        if (linedef.frontSidedefId != -1 && linedef.frontSidedefId < wadDataService.sidedefs.size()) {
            int frontSectorIndex = wadDataService.sidedefs.get(linedef.frontSidedefId).sectorId;
            if (frontSectorIndex >= 0 && frontSectorIndex < wadDataService.sectors.size()) {
                frontSector = wadDataService.sectors.get(frontSectorIndex);
            }
        }

        if (linedef.backSidedefId != -1 && linedef.backSidedefId < wadDataService.sidedefs.size()) {
            int backSectorIndex = wadDataService.sidedefs.get(linedef.backSidedefId).sectorId;
            if (backSectorIndex >= 0 && backSectorIndex < wadDataService.sectors.size()) {
                backSector = wadDataService.sectors.get(backSectorIndex);
            }
        }

        // The door sector is the one where ceiling is at or near floor level
        if (frontSector != null && backSector != null) {
            double frontGap = Math.abs(frontSector.ceilHeight - frontSector.floorHeight);
            double backGap = Math.abs(backSector.ceilHeight - backSector.floorHeight);

            // Door sector has smaller gap (ceiling close to floor when closed)
            if (frontGap < 8.0) { // Less than 8 units means it's likely the door
                LOGGER.info("Using front sector as door (gap: " + frontGap + ")");
                return frontSector;
            } else if (backGap < 8.0) {
                LOGGER.info("Using back sector as door (gap: " + backGap + ")");
                return backSector;
            } else {
                // Neither looks like a closed door - might be open, use smaller gap
                LOGGER.info("Door might be open, using sector with smaller gap");
                return (frontGap < backGap) ? frontSector : backSector;
            }
        }

        // Fallback
        return (backSector != null) ? backSector : frontSector;
    }

    public void update(Player player) {
        for (Door door : doors) {
            door.update(player);
        }
    }

    public boolean tryUseDoor(Player player, Vector2D position, double useRange) {
        LOGGER.info("Checking " + doors.size() + " doors for use action at position " + position);
        // Find closest door within use range
        Door closestDoor = null;
        double closestDistance = Double.MAX_VALUE;

        for (Door door : doors) {
            double distance = getDistanceToLinedef(position, door.getLinedef());
            LOGGER.fine("Door distance: " + distance + " (range: " + useRange + ")");
            if (distance <= useRange && distance < closestDistance) {
                closestDistance = distance;
                closestDoor = door;
                LOGGER.fine("Found closer door at distance: " + distance);
            }
        }

        if (closestDoor != null) {
            LOGGER.fine("Attempting to open closest door");
            return closestDoor.tryOpen(player);
        }

        LOGGER.fine("No doors in range");
        return false;
    }

    /**
     * Check if a linedef ID corresponds to a door
     */
    public boolean isDoorLinedef(int linedefId) {
        return linedefToDoor.containsKey(linedefId);
    }

    /**
     * Check if two linedefs form a paired door (share endpoints)
     */
    private boolean areLineddefsPaired(Linedef linedef1, Linedef linedef2) {
        // Paired door linedefs typically share the same endpoints (but in opposite directions)
        return (linedef1.startVertexId == linedef2.endVertexId && linedef1.endVertexId == linedef2.startVertexId) ||
                (linedef1.startVertexId == linedef2.startVertexId && linedef1.endVertexId == linedef2.endVertexId);
    }

    /**
     * Get the index of a linedef in the WAD data
     */
    private int getLinedefIndex(Linedef linedef) {
        for (int i = 0; i < wadDataService.linedefs.size(); i++) {
            if (wadDataService.linedefs.get(i) == linedef) {
                return i;
            }
        }
        return -1;
    }

    private double getDistanceToLinedef(Vector2D point, Linedef linedef) {
        // Get the linedef endpoints
        Vector2D start = wadDataService.vertexes.get(linedef.startVertexId);
        Vector2D end = wadDataService.vertexes.get(linedef.endVertexId);

        // Calculate distance from point to line segment
        double lineLength = Vector2D.distance(start, end);
        if (lineLength == 0) return Vector2D.distance(point, start);

        // Project point onto line
        double t = Math.max(0, Math.min(1,
                ((point.x - start.x) * (end.x - start.x) + (point.y - start.y) * (end.y - start.y)) / (lineLength * lineLength)));

        Vector2D projection = new Vector2D(
                start.x + t * (end.x - start.x),
                start.y + t * (end.y - start.y)
        );

        return Vector2D.distance(point, projection);
    }

    public List<Door> getDoors() {
        return doors;
    }

    public Door getDoorByLinedef(int linedefIndex) {
        return linedefToDoor.get(linedefIndex);
    }
}