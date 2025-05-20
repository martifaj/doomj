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

            // Move towards target (using proper speed scaling)
            double speed = self.info.speed / 35.0 * (engine.getDeltaTime() / 1000.0); // Convert to game units per second
            Vector2D newPos = self.pos.add(dirToTarget.scale(speed));
            
            // Simple collision check
            if (!isPositionBlocked(newPos.x, newPos.y, engine)) {
                self.pos = newPos;
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

    private static boolean isPositionBlocked(double x, double y, DoomEngine engine) {
        try {
            engine.getBsp().getSubSectorHeightAt(x, y);
            return false;
        } catch (Exception e) {
            return true;
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
        // Spawn MT_TROOPSHOT projectile (not implemented yet)
        // For now, just a placeholder.
        // System.out.println(self.info.name + " attacks!");
    }

    public static void A_SPosAttack(MapObject self, DoomEngine engine) {
        Actions.A_FaceTarget(self, engine);
        // Play sfx_shotgn
        // Spawn multiple pellets in a spread pattern (shotgun attack)
        // For now, just a placeholder.
        // System.out.println(self.info.name + " shotgun attack!");
    }

    public static void A_TroopAttack(MapObject self, DoomEngine engine) {
        Actions.A_FaceTarget(self, engine);
        // Play sfx_claw
        // Check melee range and deal damage if in range
        // For now, just a placeholder.
        System.out.println("*** " + self.info.name + " melee attack! Current state: " + self.currentStateNum + " Frame: " + self.currentStateDef.frameData + " ***");
    }

    public static void A_TroopMissile(MapObject self, DoomEngine engine) {
        Actions.A_FaceTarget(self, engine);
        // Play sfx_imp
        // Spawn MT_TROOPSHOT fireball projectile
        // For now, just a placeholder.
        System.out.println("*** " + self.info.name + " fires fireball! Current state: " + self.currentStateNum + " Frame: " + self.currentStateDef.frameData + " Sprite: " + self.currentSpriteLumpName + " ***");
    }

    public static void A_SargAttack(MapObject self, DoomEngine engine) {
        Actions.A_FaceTarget(self, engine);
        // Play sfx_sgtatk
        // Check melee range and deal strong bite damage if in range
        // For now, just a placeholder.
        // System.out.println(self.info.name + " bites!");
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

}

