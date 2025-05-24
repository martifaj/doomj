package com.doomviewer.game.objects;

import com.doomviewer.audio.SoundEngine;
import com.doomviewer.game.DoomEngine;
import com.doomviewer.game.Player;
import com.doomviewer.misc.math.Vector2D;

import java.util.Random;

public class Actions {
    private static final Random random = new Random();

    public static void A_Look(MapObject self, DoomEngine engine) {
        // Simple A_Look: if player is target, try to face player.
        // Actual A_Look would involve sight checks and potentially changing to CHASE state.
        if (self.target == null) {
            self.target = engine.getPlayer(); // Default target player
            
            // Play see sound when first spotting player
            if (self.info.seeSound != null && self.info.seeSound.getLumpName() != null && !self.info.seeSound.getLumpName().isEmpty()) {
                SoundEngine.getInstance().playSound(self.info.seeSound.getLumpName());
            }
        }

        if (self.target != null) {
            // Basic facing logic (can be improved)
            MapObject target = self.target;
            double angleToTarget = Math.toDegrees(Math.atan2(target.pos.y - self.pos.y, target.pos.x - self.pos.x));
            angleToTarget = (angleToTarget + 360) % 360;

            // Simple turning towards target - can be made smoother
            // self.angle = angleToTarget; // Instant face
            // For now, let's just let the state cycle. More complex AI later.
        }

        // Original A_Look sets monster angy, then calls A_Chase.
        // For now, this is a placeholder.
    }

    public static void A_Chase(MapObject self, DoomEngine engine) {
        if (self.target == null) {
            Actions.A_Look(self, engine); // Re-target if lost
            return;
        }

        // Calculate distance to target
        Vector2D dirToTarget = self.target.pos.subtract(self.pos);
        double distance = Math.sqrt(dirToTarget.x * dirToTarget.x + dirToTarget.y * dirToTarget.y);

        // Check if we should attack (basic range check)
        boolean shouldTryMeleeAttack = distance < 64.0 && self.info.meleeState != StateNum.S_NULL;
        boolean shouldTryMissileAttack = distance < getAttackRangeForMobjType(self.type) && 
                                        self.info.missileState != StateNum.S_NULL;

        // Try to attack if in range and random chance
        if ((shouldTryMeleeAttack || shouldTryMissileAttack) && Math.random() < 0.1) { // 10% chance per tic
            if (shouldTryMeleeAttack && (Math.random() < 0.5 || !shouldTryMissileAttack)) {
                // Prefer melee if close enough
                setMobjState(self, self.info.meleeState, engine);
                return;
            } else if (shouldTryMissileAttack) {
                // Use missile attack
                setMobjState(self, self.info.missileState, engine);
                return;
            }
        }

        // Move towards target if not attacking
        if (distance > 16.0) { // Don't move if very close
            dirToTarget = dirToTarget.scale(1.0 / distance); // Normalize

            // Face target
            self.angle = Math.toDegrees(Math.atan2(dirToTarget.y, dirToTarget.x));
            self.angle = (self.angle + 360) % 360;

            // Move towards target using BSP collision detection
            double speed = self.info.speed / 35.0 * (engine.getDeltaTime() / 1000.0); // Convert to game units per second
            Vector2D desiredPos = self.pos.add(dirToTarget.scale(speed));
            
            // Use BSP collision detection to get safe movement position (no logging for AI)
            Vector2D safePos = engine.getBsp().getSafeMovementPosition(self.pos, desiredPos, self.renderRadius, false);
            
            // Only update position if we're making progress towards target
            if (Vector2D.distance(self.pos, safePos) > 1.0) {
                self.pos = safePos;
            }
        }
    }

    private static double getAttackRangeForMobjType(MobjType type) {
        switch (type) {
            case MT_POSSESSED: return 512.0; // Zombieman
            case MT_SHOTGUY: return 320.0;   // Shotgun Guy
            case MT_TROOP: return 448.0;     // Imp
            case MT_SERGEANT: return 64.0;   // Demon
            default: return 256.0;
        }
    }


    private static void setMobjState(MapObject mobj, StateNum newState, DoomEngine engine) {
        if (newState != null && newState != StateNum.S_NULL) {
            mobj.setState(newState);
        }
    }

    public static void A_FaceTarget(MapObject self, DoomEngine engine) {
        if (self.target == null) return;
        double angleToTarget = Math.toDegrees(Math.atan2(self.target.pos.y - self.pos.y, self.target.pos.x - self.pos.x));
        self.angle = (angleToTarget + 360) % 360;
    }

    public static void A_PosAttack(MapObject self, DoomEngine engine) {
        Actions.A_FaceTarget(self, engine);
        // Play sfx_pistol
        SoundEngine.getInstance().playSound("DSPISTOL");
        // Hitscan attack - instant bullet
        performHitscanAttack(self, engine, 3, 5, 2048); // damage 3, spread 5 degrees, max range 2048 units
    }

    public static void A_SPosAttack(MapObject self, DoomEngine engine) {
        Actions.A_FaceTarget(self, engine);
        // Play sfx_shotgn
        SoundEngine.getInstance().playSound("DSSHOTGN");
        // Shotgun attack - multiple pellets in spread
        for (int i = 0; i < 3; i++) {
            performHitscanAttack(self, engine, 3, 11.25, 1024); // damage 3, spread 11.25 degrees, range 1024
        }
    }

    public static void A_TroopAttack(MapObject self, DoomEngine engine) {
        Actions.A_FaceTarget(self, engine);
        // Play sfx_claw
        SoundEngine.getInstance().playSound("DSCLAW");
        // Melee attack - check range and deal damage
        performMeleeAttack(self, engine, 10, 64); // damage 10, range 64 units
    }

    public static void A_TroopMissile(MapObject self, DoomEngine engine) {
        Actions.A_FaceTarget(self, engine);
        // Play sfx_imp
        SoundEngine.getInstance().playSound("DSFIRSHT");
        // Spawn MT_TROOPSHOT fireball projectile
        spawnMissile(self, engine, MobjType.MT_TROOPSHOT);
    }

    public static void A_SargAttack(MapObject self, DoomEngine engine) {
        Actions.A_FaceTarget(self, engine);
        // Play sfx_sgtatk
        SoundEngine.getInstance().playSound("DSSGTATK");
        // Strong melee attack - demon bite
        performMeleeAttack(self, engine, 15, 64); // damage 15, range 64 units
    }

    public static void A_Scream(MapObject self, DoomEngine engine) {
        // Play death sound (based on MobjInfo)
        if (self.info.deathSound != null && self.info.deathSound.getLumpName() != null && !self.info.deathSound.getLumpName().isEmpty()) {
            SoundEngine.getInstance().playSound(self.info.deathSound.getLumpName());
        }
        // System.out.println(self.info.name + " screams!");
    }

    public static void A_Pain(MapObject self, DoomEngine engine) {
        // Play pain sound
        if (self.info.painSound != null && self.info.painSound.getLumpName() != null && !self.info.painSound.getLumpName().isEmpty()) {
            SoundEngine.getInstance().playSound(self.info.painSound.getLumpName());
        }
        // Potentially interrupt current action
    }

    public static void A_Fall(MapObject self, DoomEngine engine) {
        // Object becomes non-solid, non-shootable, but remains visible as a corpse.
        self.flags |= MobjFlags.MF_SOLID;
        self.flags |= MobjFlags.MF_SHOOTABLE;
        self.flags |= MobjFlags.MF_CORPSE; // Mark as corpse
        self.flags |= MobjFlags.MF_FLOAT; // Corpses don't float
        self.flags |= MobjFlags.MF_NOGRAVITY; // Corpses are affected by gravity

        // Make sure the object stays at floor level
        if (engine.getBsp() != null) {
            self.floorHeight = engine.getBsp().getSubSectorHeightAt(self.pos.x, self.pos.y);
            self.z = self.floorHeight;
        }
    }

    public static void A_XScream(MapObject self, DoomEngine engine) {
        // Play Xdeath sound
    }

    // Define MobjAction constants for direct comparison
    public static final MobjAction A_LOOK_ACTION = Actions::A_Look;
    public static final MobjAction A_CHASE_ACTION = Actions::A_Chase;
    public static final MobjAction A_FACE_TARGET_ACTION = Actions::A_FaceTarget;
    public static final MobjAction A_POS_ATTACK_ACTION = Actions::A_PosAttack;
    public static final MobjAction A_SPOS_ATTACK_ACTION = Actions::A_SPosAttack;
    public static final MobjAction A_TROOP_ATTACK_ACTION = Actions::A_TroopAttack;
    public static final MobjAction A_TROOP_MISSILE_ACTION = Actions::A_TroopMissile;
    public static final MobjAction A_HEAD_ATTACK_ACTION = Actions::A_HeadAttack;
    public static final MobjAction A_BRUIS_ATTACK_ACTION = Actions::A_BruisAttack;
    public static final MobjAction A_FAT_ATTACK1_ACTION = Actions::A_FatAttack1;
    public static final MobjAction A_FAT_ATTACK2_ACTION = Actions::A_FatAttack2;
    public static final MobjAction A_FAT_ATTACK3_ACTION = Actions::A_FatAttack3;
    public static final MobjAction A_SCREAM_ACTION = Actions::A_Scream;
    public static final MobjAction A_PAIN_ACTION = Actions::A_Pain;
    public static final MobjAction A_FALL_ACTION = Actions::A_Fall;
    public static final MobjAction A_XSCREAM_ACTION = Actions::A_XScream;
    public static final MobjAction A_EXPLODE_ACTION = Actions::A_Explode;

    // Placeholder for other actions
    public static final MobjAction NULL_ACTION = (s, e) -> {};

    // Additional projectile attack actions for enemies not yet implemented
    public static void A_HeadAttack(MapObject self, DoomEngine engine) {
        Actions.A_FaceTarget(self, engine);
        // Play cacodemon fireball sound
        SoundEngine.getInstance().playSound("DSFIRSHT");
        // Launch cacodemon fireball
        spawnMissile(self, engine, MobjType.MT_HEADSHOT);
    }

    public static void A_BruisAttack(MapObject self, DoomEngine engine) {
        Actions.A_FaceTarget(self, engine);
        // Play baron fireball sound
        SoundEngine.getInstance().playSound("DSFIRSHT");
        // Launch baron fireball
        spawnMissile(self, engine, MobjType.MT_BRUISERSHOT);
    }
    
    public static void A_FatAttack1(MapObject self, DoomEngine engine) {
        Actions.A_FaceTarget(self, engine);
        // Play mancubus sound
        SoundEngine.getInstance().playSound("DSMANATK");
        // Launch mancubus fireball (left)
        spawnMissileWithAngleOffset(self, engine, MobjType.MT_FATSHOT, -15.0);
    }
    
    public static void A_FatAttack2(MapObject self, DoomEngine engine) {
        Actions.A_FaceTarget(self, engine);
        // Launch mancubus fireball (center)
        spawnMissile(self, engine, MobjType.MT_FATSHOT);
    }
    
    public static void A_FatAttack3(MapObject self, DoomEngine engine) {
        Actions.A_FaceTarget(self, engine);
        // Launch mancubus fireball (right)
        spawnMissileWithAngleOffset(self, engine, MobjType.MT_FATSHOT, 15.0);
    }

    public static void A_Explode(MapObject self, DoomEngine engine) {
        // Play explosion sound
        SoundEngine.getInstance().playSound("DSBAREXP");

        // Deal blast damage in radius
        double blastRadius = 128.0;
        int damage = 128;

        // Check all nearby objects
        for (MapObject obj : engine.getObjectManager().getMapObjects()) {
            if (obj == self) continue;

            double distance = Vector2D.distance(self.pos, obj.pos);
            if (distance < blastRadius) {
                // Calculate damage based on distance
                double damageRatio = 1.0 - (distance / blastRadius);
                int blastDamage = (int)(damage * damageRatio);

                if (blastDamage > 0 && (obj.flags & MobjFlags.MF_SHOOTABLE) != 0) {
                    obj.health -= blastDamage;
                    if (obj.health <= 0 && obj.info.deathState != StateNum.S_NULL) {
                        obj.setState(obj.info.deathState);
                    }
                }
            }
        }

        // Check player too
        Player player = engine.getPlayer();
        if (player != null) {
            double distanceToPlayer = Vector2D.distance(self.pos, player.pos);
            if (distanceToPlayer < blastRadius) {
                double damageRatio = 1.0 - (distanceToPlayer / blastRadius);
                int blastDamage = (int)(damage * damageRatio);
                player.takeDamage(blastDamage);
            }
        }
    }

    // Combat helper methods
    private static void spawnMissile(MapObject source, DoomEngine engine, MobjType missileType) {
        if (source.target == null) return;
        
        // Calculate angle to target
        double angleToTarget = Math.toDegrees(Math.atan2(
            source.target.pos.y - source.pos.y, 
            source.target.pos.x - source.pos.x
        ));
        
        // Create projectile through ObjectManager
        engine.getObjectManager().createProjectile(missileType, source.pos, angleToTarget, source);
    }
    
    private static void spawnMissileWithAngleOffset(MapObject source, DoomEngine engine, MobjType missileType, double angleOffset) {
        if (source.target == null) return;
        
        // Calculate angle to target with offset
        double angleToTarget = Math.toDegrees(Math.atan2(
            source.target.pos.y - source.pos.y, 
            source.target.pos.x - source.pos.x
        )) + angleOffset;
        
        // Create projectile through ObjectManager
        engine.getObjectManager().createProjectile(missileType, source.pos, angleToTarget, source);
    }
    
    private static void performHitscanAttack(MapObject source, DoomEngine engine, int damage, double spread, double maxRange) {
        if (source.target == null) return;
        
        // Calculate attack angle with random spread
        double baseAngle = source.angle;
        double attackAngle = baseAngle + (Math.random() - 0.5) * spread;
        
        // Calculate end point
        double angleRad = Math.toRadians(attackAngle);
        double endX = source.pos.x + Math.cos(angleRad) * maxRange;
        double endY = source.pos.y + Math.sin(angleRad) * maxRange;
        
        // Check if we hit the target
        Vector2D targetPos = source.target.pos;
        double distanceToTarget = Vector2D.distance(source.pos, targetPos);
        
        if (distanceToTarget <= maxRange) {
            // Simple hit test - check if target is within attack cone
            double angleToTarget = Math.toDegrees(Math.atan2(targetPos.y - source.pos.y, targetPos.x - source.pos.x));
            double angleDiff = Math.abs(normalizeAngle(angleToTarget - attackAngle));
            
            if (angleDiff <= spread / 2.0) {
                // Hit! Deal damage
                dealDamage(source.target, damage, engine);
            } else {
                // Miss
            }
        } else {
            // Out of range
        }
    }
    
    private static void performMeleeAttack(MapObject source, DoomEngine engine, int damage, double range) {
        if (source.target == null) return;
        
        double distanceToTarget = Vector2D.distance(source.pos, source.target.pos);
        
        if (distanceToTarget <= range) {
            // Hit! Deal damage
            dealDamage(source.target, damage, engine);
            System.out.println(source.info.name + " hits " + source.target.info.name + " for " + damage + " damage!");
        } else {
        }
    }

    private static void dealDamage(MapObject target, int damage, DoomEngine engine) {
        if (target == null || (target.flags & MobjFlags.MF_SHOOTABLE) == 0) {
            return;
        }

        target.takeDamage(damage, null);
    }

    private static double normalizeAngle(double angle) {
        while (angle < -180) angle += 360;
        while (angle > 180) angle -= 360;
        return angle;
    }
    
    // Weapon actions (following original Doom pattern)
    public static void A_WeaponReady(MapObject self, DoomEngine engine) {
        // Weapon is ready - handle input and bobbing
    }
    
    public static void A_Lower(MapObject self, DoomEngine engine) {
        // Lower weapon during weapon switch
    }
    
    public static void A_Raise(MapObject self, DoomEngine engine) {
        // Raise weapon during weapon switch
    }
    
    public static void A_Punch(MapObject self, DoomEngine engine) {
        // Fist attack (if implementing fist)
    }
    
    public static void A_FirePistol(MapObject self, DoomEngine engine) {
        // Fire pistol
        SoundEngine.getInstance().playSound("DSPISTOL");
        performPlayerHitscanAttack(engine, 10, 1024.0, 5.0);
    }
    
    public static void A_FireShotgun(MapObject self, DoomEngine engine) {
        // Fire shotgun with multiple pellets
        SoundEngine.getInstance().playSound("DSSHOTGN");
        for (int i = 0; i < 7; i++) {
            performPlayerHitscanAttack(engine, 5, 512.0, 11.2);
        }
    }
    
    public static void A_FireCGun(MapObject self, DoomEngine engine) {
        // Fire chaingun
        SoundEngine.getInstance().playSound("DSPISTOL");
        performPlayerHitscanAttack(engine, 10, 1024.0, 5.6);
    }
    
    public static void A_FireMissile(MapObject self, DoomEngine engine) {
        // Fire rocket launcher
        SoundEngine.getInstance().playSound("DSRLAUNC");
        performPlayerHitscanAttack(engine, 100, 2048.0, 0.0); // Simplified as hitscan for now
    }
    
    public static void A_FirePlasma(MapObject self, DoomEngine engine) {
        // Fire plasma rifle
        SoundEngine.getInstance().playSound("DSPLASMA");
        performPlayerHitscanAttack(engine, 35, 1024.0, 5.6);
    }
    
    public static void A_FireBFG(MapObject self, DoomEngine engine) {
        // Fire BFG
        SoundEngine.getInstance().playSound("DSBFG");
        performPlayerHitscanAttack(engine, 500, 2048.0, 0.0);
    }
    
    public static void A_Light0(MapObject self, DoomEngine engine) {
        // Turn off muzzle flash
    }
    
    public static void A_Light1(MapObject self, DoomEngine engine) {
        // Muzzle flash level 1
    }
    
    public static void A_Light2(MapObject self, DoomEngine engine) {
        // Muzzle flash level 2
    }
    
    public static void A_ReFire(MapObject self, DoomEngine engine) {
        // Check if player is still holding fire button for continuous weapons
    }
    
    private static void performPlayerHitscanAttack(DoomEngine engine, int damage, double range, double spread) {
        com.doomviewer.game.Player player = engine.getPlayer();
        
        // Calculate attack angle with spread
        double baseAngle = player.angle;
        double attackAngle = baseAngle + (Math.random() - 0.5) * spread;
        
        // Calculate end point
        double angleRad = Math.toRadians(attackAngle);
        double endX = player.pos.x + Math.cos(angleRad) * range;
        double endY = player.pos.y + Math.sin(angleRad) * range;
        
        
        // Check for enemy hits
        int enemyCount = 0;
        for (MapObject enemy : engine.getObjectManager().getMapObjects()) {
            if (enemy == player) continue; // Don't hit self
            enemyCount++;
            
            double distanceToEnemy = com.doomviewer.misc.math.Vector2D.distance(player.pos, enemy.pos);
            
            if (distanceToEnemy <= range) {
                // Simple hit test - check if enemy is in attack direction
                double angleToEnemy = Math.toDegrees(Math.atan2(enemy.pos.y - player.pos.y, enemy.pos.x - player.pos.x));
                double angleDiff = Math.abs(normalizeAngle(angleToEnemy - attackAngle));
                
                
                if (angleDiff <= 15.0) { // 30-degree cone
                    // Hit! Deal damage
                    dealDamage(enemy, damage, engine);
                    break; // Hit first enemy in line
                } else {
                }
            } else {
            }
        }
        
        if (enemyCount == 0) {
        }
    }

}

