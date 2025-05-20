package com.doomviewer.game.objects;

// Concordance with info.c MT_XXX indices
public enum MobjType {
    MT_PLAYER, MT_POSSESSED, MT_SHOTGUY, MT_VILE, MT_FIRE, MT_UNDEAD, MT_TRACER, MT_SMOKE, MT_FATSO, MT_FATSHOT,
    MT_CHAINGUY, MT_TROOP, MT_SERGEANT, MT_SHADOWS, MT_HEAD, MT_BRUISER, MT_BRUISERSHOT, MT_KNIGHT, MT_SKULL,
    MT_SPIDER, MT_BABY, MT_CYBORG, MT_PAIN, MT_WOLFSS, MT_KEEN, MT_BOSSBRAIN, MT_BOSSSPIT, MT_BOSSTARGET,
    MT_SPAWNSHOT, MT_SPAWNFIRE, MT_BARREL, MT_TROOPSHOT, MT_HEADSHOT, MT_ROCKET, MT_PLASMA, MT_BFG, MT_ARACHPLAZ,
    MT_PUFF, MT_BLOOD, MT_TFOG, MT_IFOG, MT_TELEPORTMAN, MT_EXTRABFG,
    // Item and decoration types follow
    MT_MISC0, MT_MISC1, // ... up to MT_MISC86
    // Need to fill this enum completely to match NUMMOBJTYPES if we want a full mapping by ordinal.
    // For now, these are enough for E1M1 enemies.
    MT_ITEM_HELMET, // Example for ARM1 (MT_MISC0)
    MT_ITEM_ARMOR,  // Example for ARM2 (MT_MISC1)
    // ... other MT_MISCXX types
    MT_MAX // Placeholder for array sizing if needed
}