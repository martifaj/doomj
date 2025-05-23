package com.doomviewer.game.objects;

import com.doomviewer.game.DoomEngine;
import com.doomviewer.game.Player;
import com.doomviewer.misc.math.Vector2D;
import com.doomviewer.wad.assets.AssetData;
import com.doomviewer.wad.datatypes.Thing;
import com.doomviewer.audio.SoundKey;

public class Projectile extends MapObject {
    private MapObject shooter;
    private double speed;
    private int damage;
    private boolean explosive;
    private double blastRadius;
    private int lifeTime; // in tics
    private int currentLifeTics;
    
    public Projectile(MobjType projectileType, Vector2D startPos, double angle, MapObject shooter, 
                     GameDefinitions gameDefinitions, AssetData assetData, DoomEngine engine) {
        super(createProjectileThing(projectileType, startPos, angle, gameDefinitions), gameDefinitions, assetData, engine);
        
        // Override the type and info after construction
        this.type = projectileType;
        this.info = gameDefinitions.mobjInfos.get(projectileType);
        if (this.info == null) {
            // Create a basic projectile info if not found
            this.info = createDefaultProjectileInfo(projectileType);
        }
        
        // Set proper sprite state for the projectile
        setProjectileSprite(projectileType);
        
        this.shooter = shooter;
        this.currentLifeTics = 0;
        
        // Set projectile-specific properties based on type
        switch (projectileType) {
            case MT_TROOPSHOT: // Imp fireball
                this.speed = 300.0; // units per second
                this.damage = 8 + (int)(Math.random() * 8); // 8-15 damage
                this.explosive = true;
                this.blastRadius = 128.0;
                this.lifeTime = 105; // 3 seconds at 35fps
                break;
                
            case MT_HEADSHOT: // Cacodemon fireball
                this.speed = 350.0;
                this.damage = 10 + (int)(Math.random() * 10); // 10-19 damage
                this.explosive = true;
                this.blastRadius = 128.0;
                this.lifeTime = 140; // 4 seconds
                break;
                
            case MT_BRUISERSHOT: // Baron/Knight fireball
                this.speed = 400.0;
                this.damage = 8 + (int)(Math.random() * 8); // 8-15 damage
                this.explosive = true;
                this.blastRadius = 128.0;
                this.lifeTime = 140;
                break;
                
            case MT_ROCKET: // Rocket launcher
                this.speed = 600.0;
                this.damage = 128 + (int)(Math.random() * 128); // 128-255 damage
                this.explosive = true;
                this.blastRadius = 256.0;
                this.lifeTime = 175; // 5 seconds
                break;
                
            case MT_PLASMA: // Plasma rifle
                this.speed = 800.0;
                this.damage = 8 + (int)(Math.random() * 8); // 8-15 damage
                this.explosive = false;
                this.blastRadius = 0;
                this.lifeTime = 70; // 2 seconds
                break;
                
            case MT_BFG: // BFG9000
                this.speed = 800.0;
                this.damage = 500 + (int)(Math.random() * 500); // 500-999 damage
                this.explosive = true;
                this.blastRadius = 512.0;
                this.lifeTime = 210; // 6 seconds
                break;
                
            case MT_FATSHOT: // Mancubus fireball
                this.speed = 350.0;
                this.damage = 8 + (int)(Math.random() * 8); // 8-15 damage
                this.explosive = true;
                this.blastRadius = 128.0;
                this.lifeTime = 140;
                break;
                
            case MT_ARACHPLAZ: // Arachnotron plasma
                this.speed = 700.0;
                this.damage = 5 + (int)(Math.random() * 5); // 5-9 damage
                this.explosive = false;
                this.blastRadius = 0;
                this.lifeTime = 105;
                break;
                
            default:
                this.speed = 400.0;
                this.damage = 10;
                this.explosive = false;
                this.blastRadius = 0;
                this.lifeTime = 105;
                break;
        }
        
        // Set velocity based on angle and speed
        double angleRad = Math.toRadians(angle);
        this.velocity = new Vector2D(
            Math.cos(angleRad) * speed,
            Math.sin(angleRad) * speed
        );
        
        // Set projectile flags
        this.flags |= MobjFlags.MF_MISSILE;
        this.flags |= MobjFlags.MF_DROPOFF;
        this.flags |= MobjFlags.MF_NOGRAVITY;
        
        // Position slightly in front of shooter to avoid collision
        double offsetDistance = (shooter != null) ? shooter.renderRadius + this.renderRadius + 8 : 32;
        this.pos = new Vector2D(
            startPos.x + Math.cos(angleRad) * offsetDistance,
            startPos.y + Math.sin(angleRad) * offsetDistance
        );
    }
    
    private static Thing createProjectileThing(MobjType type, Vector2D pos, double angle, GameDefinitions gameDefinitions) {
        Thing thing = new Thing();
        thing.pos = pos;
        thing.angle = (short)(angle * 65536.0 / 360.0); // Convert to BAMS
        
        // Use a valid thing type that has MobjInfoDef - we'll override the type after construction
        // Use player type (1) as a placeholder since it's guaranteed to exist
        thing.type = 1; 
        thing.flags = 0;
        return thing;
    }
    
    private MobjInfoDef createDefaultProjectileInfo(MobjType projectileType) {
        // Create a basic projectile info with all required parameters
        int flags = MobjFlags.MF_MISSILE | MobjFlags.MF_DROPOFF | MobjFlags.MF_NOGRAVITY;
        
        // Set specific properties based on projectile type
        switch (projectileType) {
            case MT_TROOPSHOT:
                return new MobjInfoDef(
                    projectileType.name(), // name
                    -1, // doomednum (internal projectile)
                    StateNum.S_NULL, // spawnState
                    1000, // spawnHealth
                    StateNum.S_NULL, // seeState
                    null, // seeSound
                    8, // reactionTime
                    null, // attackSound
                    StateNum.S_NULL, // painState
                    0, // painChance
                    null, // painSound
                    StateNum.S_NULL, // meleeState
                    StateNum.S_NULL, // missileState
                    StateNum.S_NULL, // deathState
                    StateNum.S_NULL, // xDeathState
                    null, // deathSound
                    10, // speed
                    6.0, // radius
                    8.0, // height
                    100, // mass
                    8, // damage
                    null, // activeSound
                    flags, // flags
                    StateNum.S_NULL // raiseState
                );
            case MT_ROCKET:
                return new MobjInfoDef(
                    projectileType.name(), -1, StateNum.S_NULL, 1000, StateNum.S_NULL,
                    null, 8, null, StateNum.S_NULL, 0, null, StateNum.S_NULL, StateNum.S_NULL,
                    StateNum.S_NULL, StateNum.S_NULL, null, 20, 11.0, 8.0, 100, 128,
                    null, flags, StateNum.S_NULL
                );
            case MT_PLASMA:
                return new MobjInfoDef(
                    projectileType.name(), -1, StateNum.S_NULL, 1000, StateNum.S_NULL,
                    null, 8, null, StateNum.S_NULL, 0, null, StateNum.S_NULL, StateNum.S_NULL,
                    StateNum.S_NULL, StateNum.S_NULL, null, 25, 13.0, 8.0, 100, 8,
                    null, flags, StateNum.S_NULL
                );
            case MT_BFG:
                return new MobjInfoDef(
                    projectileType.name(), -1, StateNum.S_NULL, 1000, StateNum.S_NULL,
                    null, 8, null, StateNum.S_NULL, 0, null, StateNum.S_NULL, StateNum.S_NULL,
                    StateNum.S_NULL, StateNum.S_NULL, null, 25, 13.0, 16.0, 100, 500,
                    null, flags, StateNum.S_NULL
                );
            default:
                return new MobjInfoDef(
                    projectileType.name(), -1, StateNum.S_NULL, 1000, StateNum.S_NULL,
                    null, 8, null, StateNum.S_NULL, 0, null, StateNum.S_NULL, StateNum.S_NULL,
                    StateNum.S_NULL, StateNum.S_NULL, null, 20, 8.0, 8.0, 100, 10,
                    null, flags, StateNum.S_NULL
                );
        }
    }
    
    private void setProjectileSprite(MobjType projectileType) {
        // Set the appropriate sprite lump name for each projectile type
        switch (projectileType) {
            case MT_TROOPSHOT:
                this.currentSpriteLumpName = "BAL1A0"; // Imp fireball sprite
                break;
            case MT_HEADSHOT:
                this.currentSpriteLumpName = "BAL2A0"; // Cacodemon fireball sprite
                break;
            case MT_BRUISERSHOT:
                this.currentSpriteLumpName = "BAL7A0"; // Baron fireball sprite
                break;
            case MT_ROCKET:
                this.currentSpriteLumpName = "MISSA0"; // Rocket sprite
                break;
            case MT_PLASMA:
                this.currentSpriteLumpName = "PLASA0"; // Plasma sprite
                break;
            case MT_BFG:
                this.currentSpriteLumpName = "BFGFA0"; // BFG sprite
                break;
            case MT_FATSHOT:
                this.currentSpriteLumpName = "MANCB0"; // Mancubus fireball sprite
                break;
            case MT_ARACHPLAZ:
                this.currentSpriteLumpName = "APLSA0"; // Arachnotron plasma sprite
                break;
            default:
                this.currentSpriteLumpName = "BAL1A0"; // Default to imp fireball
                break;
        }
        
        // Set sprite properties
        this.currentSpriteFullBright = false; // Most projectiles aren't fullbright
        
        // Override radius and height based on sprite
        switch (projectileType) {
            case MT_TROOPSHOT:
                this.renderRadius = 6.0;
                this.renderHeight = 8.0;
                break;
            case MT_ROCKET:
                this.renderRadius = 11.0;
                this.renderHeight = 8.0;
                break;
            case MT_PLASMA:
                this.renderRadius = 13.0;
                this.renderHeight = 8.0;
                this.currentSpriteFullBright = true; // Plasma is bright
                break;
            case MT_BFG:
                this.renderRadius = 13.0;
                this.renderHeight = 16.0;
                this.currentSpriteFullBright = true; // BFG is bright
                break;
            default:
                this.renderRadius = 8.0;
                this.renderHeight = 8.0;
                break;
        }
    }
    
    @Override
    public void update(DoomEngine engine) {
        currentLifeTics++;
        
        // Check for lifetime expiration
        if (currentLifeTics >= lifeTime) {
            explode(engine);
            return;
        }
        
        // Handle projectile movement instead of calling super
        updateProjectileMovement(engine);
        
        // Update state animation
        if (ticsRemainingInState > 0 && ticsRemainingInState != Integer.MAX_VALUE) {
            ticsRemainingInState--;
        }

        if (ticsRemainingInState <= 0) {
            if (currentStateDef.action != null) {
                currentStateDef.action.execute(this, engine);
            }
            setState(currentStateDef.nextState);
        }
    }
    
    private void updateProjectileMovement(DoomEngine engine) {
        double deltaTime = engine.getDeltaTime() / 1000.0;
        Vector2D newPos = pos.add(velocity.scale(deltaTime));
        
        // Check collision with walls
        if (engine.getBsp().isMovementBlocked(pos, newPos, renderRadius, false)) {
            explode(engine);
            return;
        }
        
        // Check collision with objects (enemies, player)
        for (MapObject obj : engine.getObjectManager().getMapObjects()) {
            if (obj == shooter || obj == this) continue; // Skip shooter and self
            
            double distance = Vector2D.distance(newPos, obj.pos);
            if (distance < this.renderRadius + obj.renderRadius) {
                // Hit target
                hitTarget(obj, engine);
                explode(engine);
                return;
            }
        }
        
        // Check collision with player if shooter is not player
        Player player = engine.getPlayer();
        if (shooter != player && player != null) {
            double distanceToPlayer = Vector2D.distance(newPos, player.pos);
            if (distanceToPlayer < this.renderRadius + player.renderRadius) {
                // Hit player
                hitTarget(player, engine);
                explode(engine);
                return;
            }
        }
        
        // No collision, update position
        pos = newPos;
        
        // Update Z position for flying projectiles
        if (engine.getBsp() != null) {
            this.floorHeight = engine.getBsp().getSubSectorHeightAt(pos.x, pos.y);
            this.z = this.floorHeight + 32.0; // Fly at head height
        }
    }
    
    private void hitTarget(MapObject target, DoomEngine engine) {
        if (target == null) return;
        
        // Apply damage
        target.health -= damage;
        
        // Handle target death
        if (target.health <= 0) {
            if (target.info.deathState != StateNum.S_NULL) {
                target.setState(target.info.deathState);
            }
        } else {
            // Apply pain state if target has one
            if (target.info.painState != StateNum.S_NULL && 
                Math.random() < (target.info.painChance / 255.0)) {
                target.setState(target.info.painState);
            }
        }
    }
    
    private void explode(DoomEngine engine) {
        // Create explosion effect
        if (explosive && info.deathState != StateNum.S_NULL) {
            setState(info.deathState);
            velocity = new Vector2D(0, 0); // Stop moving
            
            // Apply blast damage in radius
            if (blastRadius > 0) {
                applyBlastDamage(engine);
            }
        } else {
            // Non-explosive projectiles just disappear
            setState(StateNum.S_NULL);
        }
    }
    
    private void applyBlastDamage(DoomEngine engine) {
        // Check all objects in blast radius
        for (MapObject obj : engine.getObjectManager().getMapObjects()) {
            if (obj == shooter || obj == this) continue;
            
            double distance = Vector2D.distance(pos, obj.pos);
            if (distance < blastRadius) {
                // Calculate blast damage based on distance
                double damageRatio = 1.0 - (distance / blastRadius);
                int blastDamage = (int)(damage * damageRatio * 0.5); // 50% of direct damage
                
                if (blastDamage > 0) {
                    obj.health -= blastDamage;
                    
                    if (obj.health <= 0 && obj.info.deathState != StateNum.S_NULL) {
                        obj.setState(obj.info.deathState);
                    } else if (obj.info.painState != StateNum.S_NULL && 
                              Math.random() < (obj.info.painChance / 255.0)) {
                        obj.setState(obj.info.painState);
                    }
                }
            }
        }
        
        // Check player in blast radius
        Player player = engine.getPlayer();
        if (shooter != player && player != null) {
            double distanceToPlayer = Vector2D.distance(pos, player.pos);
            if (distanceToPlayer < blastRadius) {
                double damageRatio = 1.0 - (distanceToPlayer / blastRadius);
                int blastDamage = (int)(damage * damageRatio * 0.5);
                
                if (blastDamage > 0) {
                    // Apply damage to player (this would integrate with player health system)
                    // For now, just log the damage
                    System.out.println("Player hit by blast for " + blastDamage + " damage");
                }
            }
        }
    }
    
    public MapObject getShooter() {
        return shooter;
    }
    
    public boolean isExplosive() {
        return explosive;
    }
    
    public double getBlastRadius() {
        return blastRadius;
    }
    
    public int getDamage() {
        return damage;
    }
}