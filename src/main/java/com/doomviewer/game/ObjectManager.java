package com.doomviewer.game;

import com.doomviewer.game.objects.GameDefinitions;
import com.doomviewer.game.objects.MapObject;
import com.doomviewer.game.objects.MobjInfoDef; // Added import
import com.doomviewer.wad.WADData;
import com.doomviewer.wad.datatypes.Thing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.logging.Logger; // Import Logger

public class ObjectManager {
    private DoomEngine engine;
    private List<MapObject> mapObjects;
    private GameDefinitions gameDefinitions;
    private static final Logger LOGGER = Logger.getLogger(ObjectManager.class.getName()); // Add Logger instance

    public ObjectManager(DoomEngine engine, WADData wadData) {
        this.engine = engine;
        this.gameDefinitions = new GameDefinitions(); // This loads all state/mobj defs
        this.mapObjects = new ArrayList<>();
        int currentSkill = engine.getCurrentSkillLevel();

        LOGGER.info("Initializing ObjectManager with skill level: " + currentSkill);
        int thingCounter = 0;

        for (Thing thing : wadData.things) {
            thingCounter++;
            MobjInfoDef mobjInfo = gameDefinitions.getMobjInfoByDoomedNum(thing.type);
            // Log every 10th thing or if it's a Zombieman (type 3004)
            boolean logThisThing = (thingCounter % 10 == 0) || (thing.type == 3004);

            if (logThisThing) {
                LOGGER.info("Processing Thing #" + thingCounter + ": Type=" + thing.type + ", Flags=" + thing.flags + " at (" + thing.pos.x + "," + thing.pos.y + ")");
            }

            if (mobjInfo == null) {
                if (logThisThing) LOGGER.info("  -> No MobjInfoDef. Skipping.");
                continue; // Not a defined object type, skip.
            }

            // 1. Skip if this Thing is the main player (already handled by DoomEngine)
            Player mainPlayer = engine.getPlayer();
            if (mainPlayer != null && mainPlayer.info != null &&
                thing.type == mainPlayer.info.doomednum &&
                mainPlayer.pos.x == thing.pos.x &&
                mainPlayer.pos.y == thing.pos.y) {
                if (logThisThing) LOGGER.info("  -> Is Main Player. Skipping.");
                continue;
            }

            // 2. Skip other player starts (types 1-4) in single-player mode
            //    (Main player type 1 is already caught above, this catches types 2,3,4)
            if (thing.type >= 1 && thing.type <= 4) {
                 if (logThisThing) LOGGER.info("  -> Is other Player Start (2-4). Skipping in SP.");
                continue;
            }

            // 3. Apply skill filtering for all other objects
            boolean shouldSpawn = false;
            int thingFlags = thing.flags;
            int skillBits = thingFlags & 0x0007; // Skill flags: bit 0 (1) for Easy, bit 1 (2) for Medium, bit 2 (4) for Hard

            if ((thingFlags & 16) != 0) { // Multiplayer-only flag (bit 4, value 16)
                shouldSpawn = false; // Assuming single player context
            } else {
                switch (currentSkill) {
                    case 1: // Skill 1 (Baby)
                    case 2: // Skill 2 (Easy)
                        // Spawn if "Easy" flag (bit 0) is set, OR if no skill flags (bits 0,1,2) are set.
                        if ((skillBits & 1) != 0 || skillBits == 0) shouldSpawn = true;
                        break;
                    case 3: // Skill 3 (Medium / Hurt Me Plenty)
                        // Spawn if "Easy" flag (bit 0), OR "Medium" flag (bit 1), OR no skill flags are set.
                        if (((skillBits & 1) != 0) || ((skillBits & 2) != 0) || skillBits == 0) shouldSpawn = true;
                        break;
                    case 4: // Skill 4 (Hard / Ultra-Violence)
                        // Spawn if "Easy" (bit 0), "Medium" (bit 1), "Hard" (bit 2, value 4), OR no skill flags.
                        if (((skillBits & 1) != 0) || ((skillBits & 2) != 0) || ((skillBits & 4) != 0) || skillBits == 0) shouldSpawn = true;
                        break;
                    case 5: // Skill 5 (Nightmare)
                        // All non-multiplayer things spawn (fast monsters/respawn handled by other game logic, not just spawn flags)
                        shouldSpawn = true;
                        break;
                    default: // Should ideally not happen if skill is 1-5
                        // Fallback: try to spawn if skill level is unexpected.
                        // Spawn if "Easy" (bit 0), "Medium" (bit 1), "Hard" (bit 2, value 4), OR no skill flags,
                        // effectively making it like "Hard" as a safe default if skill is out of 1-5 range.
                        if (((skillBits & 1) != 0) || ((skillBits & 2) != 0) || ((skillBits & 4) != 0) || skillBits == 0) shouldSpawn = true;
                        break;
                }
            }

            if (logThisThing) {
                LOGGER.info("  -> Skill filter result: shouldSpawn = " + shouldSpawn + " (mobjInfo: " + mobjInfo.name + ")");
            }

            if (shouldSpawn) {
                try {
                    MapObject mo = new MapObject(thing, gameDefinitions, wadData.assetData, engine);
                    mapObjects.add(mo);
                    if (logThisThing || thing.type == 3004) { // Log all Zombiemen creations
                        LOGGER.info("  -> SPAWNED: " + mobjInfo.name + " (Type: " + thing.type + ")");
                    }
                } catch (IllegalArgumentException e) {
                    LOGGER.warning("  -> FAILED TO SPAWN (Exception): " + mobjInfo.name + " (Type: " + thing.type + "). Error: " + e.getMessage());
                }
            } else {
                 if (logThisThing && thing.type == 3004) { // Log Zombiemen that didn't spawn
                     LOGGER.info("  -> NOT SPAWNED (failed skill/other filter): Zombieman (Type: " + thing.type + ")");
                 }
            }
        }
        // After all MapObjects are created, initialize their height and target
        if (engine.getBsp() != null) {
            for (MapObject mo : mapObjects) {
                mo.initializeHeightAndTarget(engine.getBsp());
            }
        }
        System.out.println("ObjectManager initialized with " + mapObjects.size() + " map objects.");
    }

    public void update() {
        for (MapObject mo : mapObjects) {
            mo.update(engine); // Call update on each MapObject
        }
    }

    public List<MapObject> getVisibleSortedMapObjects() {
        Player player = engine.getPlayer();
        if (player == null) return Collections.emptyList();

        // Filter (e.g. by MF_NOSECTOR or MF_NODRAW flags if implemented)
        // and sort by distance from player (farthest first for painter's algorithm)
        return mapObjects.stream()
                .filter(mo -> mo.info != null) // Ensure it's a validly initialized object
                .sorted(Comparator.<MapObject>comparingDouble((mo) ->
                        mo.pos.subtract(player.pos).x * mo.pos.subtract(player.pos).x + // Use squared distance
                                mo.pos.subtract(player.pos).y * mo.pos.subtract(player.pos).y
                ).reversed()) // reversed for farthest first
                .collect(Collectors.toList());
    }

    public GameDefinitions getGameDefinitions() {
        return gameDefinitions;
    }
}
