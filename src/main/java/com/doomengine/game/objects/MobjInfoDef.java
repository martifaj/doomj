package com.doomengine.game.objects;

import com.doomengine.audio.SoundKey;

// Simplified, add more fields as needed from info.c
public class MobjInfoDef {
    public final String name; // For debugging, e.g., "MT_POSSESSED"
    public final int doomednum;
    public final StateNum spawnState;
    public final int spawnHealth;
    public final StateNum seeState;
    public final SoundKey seeSound;
    public final int reactionTime;
    public final SoundKey attackSound;
    public final StateNum painState;
    public final int painChance;
    public final SoundKey painSound;
    public final StateNum meleeState;
    public final StateNum missileState;
    public final StateNum deathState;
    public final StateNum xDeathState;
    public final SoundKey deathSound;
    public final int speed; // pixels per tic
    public final double radius; // world units
    public final double height; // world units
    public final int mass;
    public final int damage; // For projectiles or direct touch
    public final SoundKey activeSound;
    public final int flags;
    public final StateNum raiseState;

    public MobjInfoDef(String name, int doomednum, StateNum spawnState, int spawnHealth, StateNum seeState,
                       SoundKey seeSound, int reactionTime, SoundKey attackSound, StateNum painState, int painChance, 
                       SoundKey painSound, StateNum meleeState, StateNum missileState, StateNum deathState, 
                       StateNum xDeathState, SoundKey deathSound, int speed, double radius, double height, 
                       int mass, int damage, SoundKey activeSound, int flags, StateNum raiseState) {
        this.name = name;
        this.doomednum = doomednum;
        this.spawnState = spawnState;
        this.spawnHealth = spawnHealth;
        this.seeState = seeState;
        this.seeSound = seeSound;
        this.reactionTime = reactionTime;
        this.attackSound = attackSound;
        this.painState = painState;
        this.painChance = painChance;
        this.painSound = painSound;
        this.meleeState = meleeState;
        this.missileState = missileState;
        this.deathState = deathState;
        this.xDeathState = xDeathState;
        this.deathSound = deathSound;
        this.speed = speed;
        this.radius = radius;
        this.height = height;
        this.mass = mass;
        this.damage = damage;
        this.activeSound = activeSound;
        this.flags = flags;
        this.raiseState = raiseState;
    }
}