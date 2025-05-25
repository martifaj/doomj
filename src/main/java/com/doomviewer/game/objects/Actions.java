package com.doomviewer.game.objects;

import com.doomviewer.audio.SoundEngine;
import com.doomviewer.game.ObjectManager;
import com.doomviewer.game.Player;
import com.doomviewer.misc.math.Vector2D;
import com.doomviewer.services.AudioService;
import com.doomviewer.services.CollisionService;
import com.doomviewer.services.GameEngineTmp;

public enum Actions implements MobjAction {
    // Define MobjAction constants for direct comparison
    A_LOOK {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // Simple A_Look: if player is target, try to face player.
            // Actual A_Look would involve sight checks and potentially changing to CHASE state.
            if (self.getTarget() == null) {
                self.setTarget(player); // Default target player

                // Play see sound when first spotting player
                if (self.info.seeSound != null && self.info.seeSound.getLumpName() != null && !self.info.seeSound.getLumpName().isEmpty()) {
                    audioService.playSound(self.info.seeSound.getLumpName());
                }
            }

            if (self.getTarget() != null) {
                // Basic facing logic (can be improved)
                MapObject target = self.getTarget();
                double angleToTarget = Math.toDegrees(Math.atan2(target.pos.y - self.pos.y, target.pos.x - self.pos.x));
                angleToTarget = (angleToTarget + 360) % 360;

                // Simple turning towards target - can be made smoother
                // self.angle = angleToTarget; // Instant face
                // For now, let's just let the state cycle. More complex AI later.
            }

            // Original A_Look sets monster angy, then calls A_Chase.
            // For now, this is a placeholder.

        }
    },
    A_CHASE {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            if (self.getTarget() == null) {
                A_LOOK.execute(self, objectManager, player, audioService, engineTmp, collisionService); // Re-target if lost
                return;
            }

            // Calculate distance to target
            Vector2D dirToTarget = self.getTarget().pos.subtract(self.pos);
            double distance = Math.sqrt(dirToTarget.x * dirToTarget.x + dirToTarget.y * dirToTarget.y);

            // Check if we should attack (basic range check)
            boolean shouldTryMeleeAttack = distance < 64.0 && self.info.meleeState != StateNum.S_NULL;
            boolean shouldTryMissileAttack = distance < getAttackRangeForMobjType(self.type) &&
                    self.info.missileState != StateNum.S_NULL;

            // Try to attack if in range and random chance
            if ((shouldTryMeleeAttack || shouldTryMissileAttack) && Math.random() < 0.1) { // 10% chance per tic
                if (shouldTryMeleeAttack && (Math.random() < 0.5 || !shouldTryMissileAttack)) {
                    // Prefer melee if close enough
                    setMobjState(self, self.info.meleeState);
                    return;
                } else if (shouldTryMissileAttack) {
                    // Use missile attack
                    setMobjState(self, self.info.missileState);
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
                double speed = self.info.speed / 35.0 * (engineTmp.getDeltaTime() / 1000.0); // Convert to game units per second
                Vector2D desiredPos = self.pos.add(dirToTarget.scale(speed));

                // Use BSP collision detection to get safe movement position (no logging for AI)
                Vector2D safePos = collisionService.getSafeMovementPosition(self.pos, desiredPos, self.renderRadius, false);

                // Only update position if we're making progress towards target
                if (Vector2D.distance(self.pos, safePos) > 1.0) {
                    self.pos = safePos;
                }
            }
        }

    },
    A_FACE_TARGET {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            if (self.getTarget() == null) return;
            double angleToTarget = Math.toDegrees(Math.atan2(self.getTarget().pos.y - self.pos.y, self.getTarget().pos.x - self.pos.x));
            self.angle = (angleToTarget + 360) % 360;
        }
    },
    A_POS_ATTACK {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            A_FACE_TARGET.execute(self, objectManager, player, audioService, engineTmp, collisionService);
            // Play sfx_pistol
            SoundEngine.getInstance().playSound("DSPISTOL");
            // Hitscan attack - instant bullet
            performHitscanAttack(self, 3, 5, 2048); // damage 3, spread 5 degrees, max range 2048 units
        }
    },
    A_SPOS_ATTACK {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            Actions.A_FACE_TARGET.execute(self, objectManager, player, audioService, engineTmp, collisionService);
            // Play sfx_shotgn
            SoundEngine.getInstance().playSound("DSSHOTGN");
            // Shotgun attack - multiple pellets in spread
            for (int i = 0; i < 3; i++) {
                performHitscanAttack(self, 3, 11.25, 1024); // damage 3, spread 11.25 degrees, range 1024
            }
        }
    },
    A_TROOP_ATTACK {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            Actions.A_FACE_TARGET.execute(self, objectManager, player, audioService, engineTmp, collisionService);
            // Play sfx_claw
            SoundEngine.getInstance().playSound("DSCLAW");
            // Melee attack - check range and deal damage
            performMeleeAttack(self, 10, 64); // damage 10, range 64 units
        }
    },
    A_TROOP_MISSILE {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            Actions.A_FACE_TARGET.execute(self, objectManager, player, audioService, engineTmp, collisionService);
            // Play sfx_imp
            SoundEngine.getInstance().playSound("DSFIRSHT");
            // Spawn MT_TROOPSHOT fireball projectile
            spawnMissile(self, MobjType.MT_TROOPSHOT, objectManager);
        }
    },
    A_SARG_ATTACK {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp
                engineTmp, CollisionService collisionService) {
            Actions.A_FACE_TARGET.execute(self, objectManager, player, audioService, engineTmp, collisionService);
            // Play sfx_sgtatk
            SoundEngine.getInstance().

                    playSound("DSSGTATK");

            // Strong melee attack - demon bite
            performMeleeAttack(self, 15, 64); // damage 15, range 64 units
        }
    },
    A_SCREAM {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // Play death sound (based on MobjInfo)
            if (self.info.deathSound != null && self.info.deathSound.getLumpName() != null && !self.info.deathSound.getLumpName().isEmpty()) {
                SoundEngine.getInstance().playSound(self.info.deathSound.getLumpName());
            }
            // System.out.println(self.info.name + " screams!");
        }
    },
    A_PAIN {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // Play pain sound
            if (self.info.painSound != null && self.info.painSound.getLumpName() != null && !self.info.painSound.getLumpName().isEmpty()) {
                SoundEngine.getInstance().playSound(self.info.painSound.getLumpName());
            }
            // Potentially interrupt current action
        }
    },
    A_FALL {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // Object becomes non-solid, non-shootable, but remains visible as a corpse.
            self.flags &= ~MobjFlags.MF_SOLID;      // Remove solid flag - corpses don't block movement
            self.flags &= ~MobjFlags.MF_SHOOTABLE;  // Remove shootable flag - corpses can't be shot
            self.flags |= MobjFlags.MF_CORPSE;      // Mark as corpse
            self.flags &= ~MobjFlags.MF_FLOAT;      // Remove float flag - corpses don't float
            self.flags &= ~MobjFlags.MF_NOGRAVITY;  // Remove nogravity flag - corpses are affected by gravity

            // Make sure the object stays at floor level
            self.floorHeight = collisionService.getSubSectorHeightAt(self.pos.x, self.pos.y);
            self.z = self.floorHeight;
        }
    },
    A_XSCREAM {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {

        }
    },
    A_HEAD_ATTACK {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            Actions.A_FACE_TARGET.execute(self, objectManager, player, audioService, engineTmp, collisionService);
            // Play cacodemon fireball sound
            SoundEngine.getInstance().playSound("DSFIRSHT");
            // Launch cacodemon fireball
            spawnMissile(self, MobjType.MT_HEADSHOT, objectManager);
        }
    },
    A_BRUIS_ATTACK {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            Actions.A_FACE_TARGET.execute(self, objectManager, player, audioService, engineTmp, collisionService);
            // Play baron fireball sound
            SoundEngine.getInstance().

                    playSound("DSFIRSHT");

            // Launch baron fireball
            spawnMissile(self, MobjType.MT_BRUISERSHOT, objectManager);
        }

    },
    A_FAT_ATTACK_1 {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            Actions.A_FACE_TARGET.execute(self, objectManager, player, audioService, engineTmp, collisionService);
            // Play mancubus sound
            SoundEngine.getInstance().playSound("DSMANATK");
            // Launch mancubus fireball (left)
            spawnMissileWithAngleOffset(self, MobjType.MT_FATSHOT, -15.0, objectManager);
        }
    },
    A_FAT_ATTACK_2 {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            Actions.A_FACE_TARGET.execute(self, objectManager, player, audioService, engineTmp, collisionService);
            // Launch mancubus fireball (center)
            spawnMissile(self, MobjType.MT_FATSHOT, objectManager);
        }
    },
    A_FAT_ATTACK_3 {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            Actions.A_FACE_TARGET.execute(self, objectManager, player, audioService, engineTmp, collisionService);
            // Launch mancubus fireball (right)
            spawnMissileWithAngleOffset(self, MobjType.MT_FATSHOT, 15.0, objectManager);
        }
    },
    A_EXPLODE {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // Play explosion sound
            SoundEngine.getInstance().playSound("DSBAREXP");

            // Deal blast damage in radius
            double blastRadius = 128.0;
            int damage = 128;

            // Check all nearby objects
            for (MapObject obj : objectManager.getMapObjects()) {
                if (obj == self) continue;

                double distance = Vector2D.distance(self.pos, obj.pos);
                if (distance < blastRadius) {
                    // Calculate damage based on distance
                    double damageRatio = 1.0 - (distance / blastRadius);
                    int blastDamage = (int) (damage * damageRatio);

                    if (blastDamage > 0 && (obj.flags & MobjFlags.MF_SHOOTABLE) != 0) {
                        obj.health -= blastDamage;
                        if (obj.health <= 0 && obj.info.deathState != StateNum.S_NULL) {
                            obj.setState(obj.info.deathState);
                        }
                    }
                }
            }

            // Check player too
            double distanceToPlayer = Vector2D.distance(self.pos, player.pos);
            if (distanceToPlayer < blastRadius) {
                double damageRatio = 1.0 - (distanceToPlayer / blastRadius);
                int blastDamage = (int) (damage * damageRatio);
                player.takeDamage(blastDamage);
            }
        }
    },
    A_WEAPON_READY {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // No action defined
        }
    },
    A_LOWER {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // No action defined
        }
    },
    A_RAISE {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // No action defined
        }
    },
    A_PUNCH {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // No action defined
        }
    },
    A_FIRE_PISTOL {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // Fire pistol
            SoundEngine.getInstance().playSound("DSPISTOL");
            performPlayerHitscanAttack(player, objectManager, 10, 1024.0, 5.0);
        }
    },
    A_FIRE_SHOTGUN {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // Fire shotgun with multiple pellets
            SoundEngine.getInstance().playSound("DSSHOTGN");
            for (int i = 0; i < 7; i++) {
                performPlayerHitscanAttack(player, objectManager, 5, 512.0, 11.2);
            }
        }
    },
    A_FIRE_CGUN {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {

            // Fire chaingun
            SoundEngine.getInstance().playSound("DSPISTOL");
            performPlayerHitscanAttack(player, objectManager, 10, 1024.0, 5.6);
        }
    },
    A_FIRE_MISSILE {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // Fire rocket launcher
            SoundEngine.getInstance().playSound("DSRLAUNC");
            performPlayerHitscanAttack(player, objectManager, 100, 2048.0, 0.0); // Simplified as hitscan for now
        }
    },
    A_FIRE_PLASMA {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // Fire plasma rifle
            SoundEngine.getInstance().playSound("DSPLASMA");
            performPlayerHitscanAttack(player, objectManager, 35, 1024.0, 5.6);
        }
    },
    A_FIRE_BFG {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // Fire BFG
            SoundEngine.getInstance().playSound("DSBFG");
            performPlayerHitscanAttack(player, objectManager, 500, 2048.0, 0.0);
        }
    },
    A_LIGHT_0 {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {

        }
    },
    A_LIGHT_1 {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {

        }
    },
    A_LIGHT_2 {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // No action defined
        }
    },
    A_RE_FIRE {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // No action defined
        }
    },
    A_FAT_ATTACK1 {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // No action defined
        }
    },
    A_FAT_ATTACK2 {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // No action defined
        }
    },
    A_FAT_ATTACK3 {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // No action defined
        }
    },
    NULL {
        @Override
        public void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService) {
            // No action defined
        }
    };

    private static double getAttackRangeForMobjType(MobjType type) {
        switch (type) {
            case MT_POSSESSED:
                return 512.0; // Zombieman
            case MT_SHOTGUY:
                return 320.0;   // Shotgun Guy
            case MT_TROOP:
                return 448.0;     // Imp
            case MT_SERGEANT:
                return 64.0;   // Demon
            default:
                return 256.0;
        }
    }


    private static void setMobjState(MapObject mobj, StateNum newState) {
        if (newState != null && newState != StateNum.S_NULL) {
            mobj.setState(newState);
        }
    }


    // Combat helper methods
    private static void spawnMissile(MapObject source, MobjType missileType, ObjectManager objectManager) {
        if (source.getTarget() == null) return;

        // Calculate angle to target
        double angleToTarget = Math.toDegrees(Math.atan2(
                source.getTarget().pos.y - source.pos.y,
                source.getTarget().pos.x - source.pos.x
        ));

        // Create projectile through ObjectManager
        objectManager.createProjectile(missileType, source.pos, angleToTarget, source);
    }

    private static void spawnMissileWithAngleOffset(MapObject source, MobjType missileType, double angleOffset, ObjectManager objectManager) {
        if (source.getTarget() == null) return;

        // Calculate angle to target with offset
        double angleToTarget = Math.toDegrees(Math.atan2(
                source.getTarget().pos.y - source.pos.y,
                source.getTarget().pos.x - source.pos.x
        )) + angleOffset;

        // Create projectile through ObjectManager
        objectManager.createProjectile(missileType, source.pos, angleToTarget, source);
    }

    private static void performHitscanAttack(MapObject source, int damage, double spread, double maxRange) {
        if (source.getTarget() == null) return;

        // Calculate attack angle with random spread
        double baseAngle = source.angle;
        double attackAngle = baseAngle + (Math.random() - 0.5) * spread;

        // Calculate end point
        double angleRad = Math.toRadians(attackAngle);
        double endX = source.pos.x + Math.cos(angleRad) * maxRange;
        double endY = source.pos.y + Math.sin(angleRad) * maxRange;

        // Check if we hit the target
        Vector2D targetPos = source.getTarget().pos;
        double distanceToTarget = Vector2D.distance(source.pos, targetPos);

        if (distanceToTarget <= maxRange) {
            // Simple hit test - check if target is within attack cone
            double angleToTarget = Math.toDegrees(Math.atan2(targetPos.y - source.pos.y, targetPos.x - source.pos.x));
            double angleDiff = Math.abs(normalizeAngle(angleToTarget - attackAngle));

            if (angleDiff <= spread / 2.0) {
                // Hit! Deal damage
                dealDamage(source.getTarget(), damage);
            } else {
                // Miss
            }
        } else {
            // Out of range
        }
    }

    private static void performMeleeAttack(MapObject source, int damage, double range) {
        if (source.getTarget() == null) return;

        double distanceToTarget = Vector2D.distance(source.pos, source.getTarget().pos);

        if (distanceToTarget <= range) {
            // Hit! Deal damage
            dealDamage(source.getTarget(), damage);
            System.out.println(source.info.name + " hits " + source.getTarget().info.name + " for " + damage + " damage!");
        } else {
        }
    }

    private static void dealDamage(MapObject target, int damage) {
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


    private static void performPlayerHitscanAttack(Player player, ObjectManager objectManager, int damage, double range, double spread) {
        // Calculate attack angle with spread
        double baseAngle = player.angle;
        double attackAngle = baseAngle + (Math.random() - 0.5) * spread;

        // Calculate end point
        double angleRad = Math.toRadians(attackAngle);
        double endX = player.pos.x + Math.cos(angleRad) * range;
        double endY = player.pos.y + Math.sin(angleRad) * range;


        // Check for shootable object hits (enemies, barrels, etc.)
        int objectCount = 0;
        for (MapObject obj : objectManager.getMapObjects()) {
            if (obj == player) continue; // Don't hit self

            // Only check shootable objects
            if ((obj.flags & MobjFlags.MF_SHOOTABLE) == 0) continue;

            objectCount++;

            double distanceToObject = com.doomviewer.misc.math.Vector2D.distance(player.pos, obj.pos);

            if (distanceToObject <= range) {
                // Simple hit test - check if object is in attack direction
                double angleToObject = Math.toDegrees(Math.atan2(obj.pos.y - player.pos.y, obj.pos.x - player.pos.x));
                double angleDiff = Math.abs(normalizeAngle(angleToObject - attackAngle));


                if (angleDiff <= 15.0) { // 30-degree cone
                    // Hit! Deal damage
                    dealDamage(obj, damage);
                    break; // Hit first object in line
                } else {
                }
            } else {
            }
        }

        if (objectCount == 0) {
        }
    }

}

