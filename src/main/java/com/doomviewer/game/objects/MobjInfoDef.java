package com.doomviewer.game.objects;

// Simplified, add more fields as needed from info.c
public class MobjInfoDef {
    public String name; // For debugging, e.g., "MT_POSSESSED"
    public int doomednum;
    public StateNum spawnState;
    public int spawnHealth;
    public StateNum seeState;
    // public SoundKey seeSound;
    public int reactionTime;
    // public SoundKey attackSound;
    public StateNum painState;
    public int painChance;
    // public SoundKey painSound;
    public StateNum meleeState;
    public StateNum missileState;
    public StateNum deathState;
    public StateNum xDeathState;
    // public SoundKey deathSound;
    public int speed; // pixels per tic
    public double radius; // world units
    public double height; // world units
    public int mass;
    public int damage; // For projectiles or direct touch
    // public SoundKey activeSound;
    public int flags;
    public StateNum raiseState;

    public MobjInfoDef(String name, int doomednum, StateNum spawnState, int spawnHealth, StateNum seeState,
                       int reactionTime, StateNum painState, int painChance, StateNum meleeState,
                       StateNum missileState, StateNum deathState, StateNum xDeathState,
                       int speed, double radius, double height, int mass, int damage, int flags, StateNum raiseState) {
        this.name = name;
        this.doomednum = doomednum;
        this.spawnState = spawnState;
        this.spawnHealth = spawnHealth;
        this.seeState = seeState;
        this.reactionTime = reactionTime;
        this.painState = painState;
        this.painChance = painChance;
        this.meleeState = meleeState;
        this.missileState = missileState;
        this.deathState = deathState;
        this.xDeathState = xDeathState;
        this.speed = speed;
        this.radius = radius;
        this.height = height;
        this.mass = mass;
        this.damage = damage;
        this.flags = flags;
        this.raiseState = raiseState;
    }
}