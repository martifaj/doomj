package com.doomviewer.game.objects;

import com.doomviewer.game.DoomEngine;
import com.doomviewer.misc.math.Vector2D;

import java.util.Random;

public class Actions {
    private static final Random random = new Random();

    public static void A_Look(MapObject self, DoomEngine engine) {
        // Simple A_Look: if player is target, try to face player.
        // Actual A_Look would involve sight checks and potentially changing to CHASE state.
        if (self.target == null) {
            self.target = engine.getPlayer(); // Default target player
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
            
            // Use BSP collision detection to get safe movement position
            Vector2D safePos = engine.getBsp().getSafeMovementPosition(self.pos, desiredPos, self.renderRadius);
            
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
        // Hitscan attack - instant bullet
        performHitscanAttack(self, engine, 3, 5, 2048); // damage 3, spread 5 degrees, max range 2048 units
        System.out.println("*** " + self.info.name + " fires pistol! ***");
    }

    public static void A_SPosAttack(MapObject self, DoomEngine engine) {
        Actions.A_FaceTarget(self, engine);
        // Play sfx_shotgn
        // Shotgun attack - multiple pellets in spread
        for (int i = 0; i < 3; i++) {
            performHitscanAttack(self, engine, 3, 11.25, 1024); // damage 3, spread 11.25 degrees, range 1024
        }
        System.out.println("*** " + self.info.name + " fires shotgun! ***");
    }

    public static void A_TroopAttack(MapObject self, DoomEngine engine) {
        Actions.A_FaceTarget(self, engine);
        // Play sfx_claw
        // Melee attack - check range and deal damage
        performMeleeAttack(self, engine, 10, 64); // damage 10, range 64 units
        System.out.println("*** " + self.info.name + " melee attack! ***");
    }

    public static void A_TroopMissile(MapObject self, DoomEngine engine) {
        Actions.A_FaceTarget(self, engine);
        // Play sfx_imp
        // Spawn MT_TROOPSHOT fireball projectile
        spawnMissile(self, engine, MobjType.MT_TROOPSHOT);
        System.out.println("*** " + self.info.name + " fires fireball! ***");
    }

    public static void A_SargAttack(MapObject self, DoomEngine engine) {
        Actions.A_FaceTarget(self, engine);
        // Play sfx_sgtatk
        // Strong melee attack - demon bite
        performMeleeAttack(self, engine, 15, 64); // damage 15, range 64 units
        System.out.println("*** " + self.info.name + " bites! ***");
    }

    public static void A_Scream(MapObject self, DoomEngine engine) {
        // Play death sound (based on MobjInfo)
        // System.out.println(self.info.name + " screams!");
    }

    public static void A_Pain(MapObject self, DoomEngine engine) {
        // Play pain sound
        // Potentially interrupt current action
    }

    public static void A_Fall(MapObject self, DoomEngine engine) {
        // Object becomes non-solid, non-shootable.
        self.flags &= ~MobjFlags.MF_SOLID;
        self.flags &= ~MobjFlags.MF_SHOOTABLE;
        // System.out.println(self.info.name + " falls!");
    }

    public static void A_XScream(MapObject self, DoomEngine engine) {
        // Play Xdeath sound
    }

    // Define MobjAction constants for direct comparison
    public static final MobjAction A_LOOK_ACTION = Actions::A_Look;
    public static final MobjAction A_CHASE_ACTION = Actions::A_Chase;
    public static final MobjAction A_FACE_TARGET_ACTION = Actions::A_FaceTarget;
    public static final MobjAction A_POS_ATTACK_ACTION = Actions::A_PosAttack;
    public static final MobjAction A_SCREAM_ACTION = Actions::A_Scream;
    public static final MobjAction A_PAIN_ACTION = Actions::A_Pain;
    public static final MobjAction A_FALL_ACTION = Actions::A_Fall;
    public static final MobjAction A_XSCREAM_ACTION = Actions::A_XScream;

    // Placeholder for other actions
    public static final MobjAction NULL_ACTION = (s, e) -> {};

    // Combat helper methods
    private static void spawnMissile(MapObject source, DoomEngine engine, MobjType missileType) {
        if (source.target == null) return;
        
        // For now, just create a simple effect - full projectile system will need DoomEngine methods
        System.out.println("*** " + source.info.name + " would spawn " + missileType + " projectile! ***");
        
        // Simplified damage - just hit the target directly for now
        if (source.target != null) {
            dealDamage(source.target, 8, engine); // Fireball damage
        }
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
                System.out.println(source.info.name + " hits " + source.target.info.name + " for " + damage + " damage!");
            } else {
                // Miss
                System.out.println(source.info.name + " misses!");
            }
        } else {
            // Out of range
            System.out.println(source.info.name + " fires but target out of range!");
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
            System.out.println(source.info.name + " swings but misses!");
        }
    }
    
    private static void dealDamage(MapObject target, int damage, DoomEngine engine) {
        target.health -= damage;
        System.out.println(target.info.name + " takes " + damage + " damage! Health: " + target.health + "/" + target.info.spawnHealth);
        
        if (target.health <= 0) {
            // Target killed
            System.out.println(target.info.name + " is killed!");
            target.setState(target.info.deathState);
        } else if (Math.random() < (target.info.painChance / 255.0)) {
            // Pain chance
            target.setState(target.info.painState);
            System.out.println(target.info.name + " is in pain!");
        }
    }
    
    
    private static double normalizeAngle(double angle) {
        while (angle < -180) angle += 360;
        while (angle > 180) angle -= 360;
        return angle;
    }

}

