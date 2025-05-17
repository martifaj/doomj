package com.doomengine.game.objects;

public class MobjFlags {
    public static final int MF_SPECIAL = 1;         // Call P_SpecialThing when touched.
    public static final int MF_SOLID = 2;           // Blocks other objects.
    public static final int MF_SHOOTABLE = 4;       // Can be hit by bullets.
    public static final int MF_NOSECTOR = 8;        // Don't use sector links (invisible but touchable).
    public static final int MF_NOBLOCKMAP = 16;     // Don't block map (become missile).
    public static final int MF_AMBUSH = 32;         // Deaf monster.
    public static final int MF_JUSTHIT = 64;        // Will try to attack right back.
    public static final int MF_JUSTATTACKED = 128;  // Went into attack state.
    public static final int MF_SPAWNCEILING = 256;  // Hang from ceiling instead of stand on floor.
    public static final int MF_NOGRAVITY = 512;     // Don't apply gravity (float).
    public static final int MF_DROPOFF = 0x400;     // Movement will try to drop off edges.
    public static final int MF_PICKUP = 0x800;      // For players to pick up items.
    public static final int MF_NOCLIP = 0x1000;     // Not blocked by anything.
    public static final int MF_SLIDE = 0x2000;      // Player: keep info about sliding along walls.
    public static final int MF_FLOAT = 0x4000;      // Apply float bobbing effect.
    public static final int MF_TELEPORT = 0x8000;   // Don't apply normal physics after teleports.
    public static final int MF_MISSILE = 0x10000;   // Act as a missile.
    public static final int MF_DROPPED = 0x20000;   // Item dropped by player, can be picked up by others.
    public static final int MF_SHADOW = 0x40000;    // Apply a shadow effect (Spectre).
    public static final int MF_NOBLOOD = 0x80000;   // Don't bleed when shot (Barrel).
    public static final int MF_CORPSE = 0x100000;   // Is a corpse, can be exploded.
    public static final int MF_INFLOAT = 0x200000;  // Currently bobbing up (used with MF_FLOAT).
    public static final int MF_COUNTKILL = 0x400000;// Counts towards kill percentage.
    public static final int MF_COUNTITEM = 0x800000;// Counts towards item percentage.
    public static final int MF_SKULLFLY = 0x1000000;// Lost Soul attack logic.
    public static final int MF_NOTDMATCH = 0x2000000;// Don't spawn in Deathmatch.
    // Doom 2 flags (some might be above)
    public static final int MF_TRANSLATION = 0x4000000; // Used for color translation.
    public static final int MF_TRANSSHIFT = 28;         // Bits for translation index.
    public static final int MF_NOCOUNT = 0x40000000; // Example, ensure it doesn't clash, or use an existing one if suitable.
    // The original game might just not set MF_COUNTKILL for players.
    // The provided info.c for MT_PLAYER has MF_NOTDMATCH but not MF_COUNTKILL.
    // Let's use the flags from your provided info.c for MT_PLAYER:
    // MF_SOLID|MF_SHOOTABLE|MF_DROPOFF|MF_PICKUP|MF_NOTDMATCH
}