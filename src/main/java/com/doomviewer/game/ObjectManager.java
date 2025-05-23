package com.doomviewer.game;

import com.doomviewer.game.objects.GameDefinitions;
import com.doomviewer.game.objects.MapObject;
import com.doomviewer.game.objects.MobjInfoDef; // Added import
import com.doomviewer.game.objects.MobjType;
import com.doomviewer.game.objects.Projectile;
import com.doomviewer.misc.math.Vector2D;
import com.doomviewer.wad.WADData;
import com.doomviewer.wad.datatypes.Thing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ObjectManager {
    private DoomEngine engine;
    private List<MapObject> mapObjects;
    private List<MapObject> projectiles;
    private GameDefinitions gameDefinitions;

    public ObjectManager(DoomEngine engine, WADData wadData) {
        this.engine = engine;
        this.gameDefinitions = new GameDefinitions(); // This loads all state/mobj defs
        this.mapObjects = new ArrayList<>();
        this.projectiles = new ArrayList<>();
        int currentSkill = engine.getCurrentSkillLevel();

        for (Thing thing : wadData.things) {
            MobjInfoDef mobjInfo = gameDefinitions.getMobjInfoByDoomedNum(thing.type);

            if (mobjInfo == null) {
                continue; // Not a defined object type, skip.
            }

            // 1. Skip if this Thing is the main player (already handled by DoomEngine)
            Player mainPlayer = engine.getPlayer();
            if (mainPlayer != null && mainPlayer.info != null &&
                thing.type == mainPlayer.info.doomednum &&
                mainPlayer.pos.x == thing.pos.x &&
                mainPlayer.pos.y == thing.pos.y) {
                continue;
            }

            // 2. Skip other player starts (types 1-4) in single-player mode
            //    (Main player type 1 is already caught above, this catches types 2,3,4)
            if (thing.type >= 1 && thing.type <= 4) {
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

            if (shouldSpawn) {
                try {
                    MapObject mo = new MapObject(thing, gameDefinitions, wadData.assetData, engine);
                    mapObjects.add(mo);
                } catch (IllegalArgumentException e) {
                    // Silently skip objects that fail to spawn
                }
            }
        }
        // After all MapObjects are created, initialize their height and target
        if (engine.getBsp() != null) {
            for (MapObject mo : mapObjects) {
                mo.initializeHeightAndTarget(engine.getBsp());
            }
        }
    }

    public void update() {
        for (MapObject mo : mapObjects) {
            mo.update(engine); // Call update on each MapObject
        }
        updateProjectiles(); // Update projectiles
    }

    public List<MapObject> getMapObjects() {
        return mapObjects;
    }

    public List<MapObject> getVisibleSortedMapObjects() {
        Player player = engine.getPlayer();
        if (player == null) return Collections.emptyList();

        // Combine map objects and projectiles for rendering
        List<MapObject> allObjects = new ArrayList<>(mapObjects);
        allObjects.addAll(projectiles);

        // Filter (e.g. by MF_NOSECTOR or MF_NODRAW flags if implemented)
        // and sort by distance from player (farthest first for painter's algorithm)
        return allObjects.stream()
                .filter(mo -> mo.info != null) // Ensure it's a validly initialized object
                .sorted(Comparator.<MapObject>comparingDouble((mo) ->
                        mo.pos.subtract(player.pos).x * mo.pos.subtract(player.pos).x + // Use squared distance
                                mo.pos.subtract(player.pos).y * mo.pos.subtract(player.pos).y
                ).reversed()) // reversed for farthest first
                .collect(Collectors.toList());
    }

    public void addProjectile(MapObject projectile) {
        projectiles.add(projectile);
    }
    
    public Projectile createProjectile(MobjType projectileType, Vector2D startPos, double angle, MapObject shooter) {
        Projectile projectile = new Projectile(projectileType, startPos, angle, shooter, 
                                             gameDefinitions, engine.getWadData().assetData, engine);
        projectiles.add(projectile);
        return projectile;
    }
    
    public void updateProjectiles() {
        // Update all projectiles
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            MapObject projectile = projectiles.get(i);
            projectile.update(engine);
            
            // Remove projectiles that have finished their animation
            // Only remove actual projectiles (missiles, puffs, blood), not map objects that exploded
            if ((projectile.currentStateNum == null || projectile.currentStateNum.name().equals("S_NULL")) &&
                projectile.isProjectile()) {
                projectiles.remove(i);
            }
        }
    }
    
    public List<MapObject> getAllRenderableObjects() {
        // Combine map objects and projectiles for rendering
        List<MapObject> allObjects = new ArrayList<>(mapObjects);
        allObjects.addAll(projectiles);
        return allObjects;
    }

    public GameDefinitions getGameDefinitions() {
        return gameDefinitions;
    }
    
    public void removeObject(MapObject object) {
        mapObjects.remove(object);
    }
}
