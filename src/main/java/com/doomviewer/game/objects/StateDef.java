package com.doomviewer.game.objects;

public class StateDef {
    public SpriteNames spriteName; // From SPR_XXX
    public int frameData;        // Raw frame data from info.c (includes index and fullbright)
    public int tics;             // Duration in game tics (1 tic = 1/35th second)
    public MobjAction action;    // Action function to call
    public StateNum nextState;   // Next state to transition to

    public StateDef(SpriteNames spriteName, int frameData, int tics, MobjAction action, StateNum nextState) {
        this.spriteName = spriteName;
        this.frameData = frameData;
        this.tics = tics;
        this.action = (action != null) ? action : Actions.NULL;
        this.nextState = nextState;
    }

    public int getFrameIndex() {
        return frameData & 0x7FFF; // Lower 15 bits for frame character index (A=0, B=1, etc.)
        // Original Doom uses fewer bits, often 0-4 for A-Z,
        // but 0x7FFF is safe for masking fullbright.
        // The actual number of frames for a sprite varies.
        // The character is 'A' + (frame & 0x1F) usually for 32 frames.
        // info.c has integer frame numbers.
    }

    public boolean isFullBright() {
        return (frameData & 0x8000) != 0;
    }

    public String getSpriteLumpName() {
        // Check for invalid frame (used for invisible objects)
        if (frameData < 0) {
            return null; // No sprite to render
        }
        
        // Sprite lump names are typically XXXXYR, e.g., POSSA1
        // X = Sprite Name (e.g., POSS)
        // Y = Frame Character (A-Z) based on frame index
        // R = Rotation (0-8, different for different sprite types)
        char frameChar = (char) ('A' + getFrameIndex());
        int rotation = getRotationForSprite(spriteName.getName());
        return String.format("%s%c%d", spriteName.getName(), frameChar, rotation);
    }
    
    private int getRotationForSprite(String spriteName) {
        // Different sprite types use different rotations
        switch (spriteName) {
            // Monsters/enemies use rotation 1
            case "POSS":    // Zombieman
            case "SPOS":    // Shotgun guy
            case "TROO":    // Imp
            case "SARG":    // Demon
            case "HEAD":    // Cacodemon
            case "BOSS":    // Baron of Hell
            case "SKUL":    // Lost Soul
            case "PAIN":    // Pain Elemental
            case "FATB":    // Mancubus
            case "SKEL":    // Revenant
            case "CPOS":    // Chaingunner
            case "VILE":    // Archvile
            case "SPID":    // Spider Mastermind
            case "BSPI":    // Arachnotron
            case "CYBR":    // Cyberdemon
                return 1;
                
            // Items, decorations, and barrels use rotation 0
            case "BAR1":    // Barrels
            case "BEXP":    // Barrel explosions
            case "CLIP":    // Ammo
            case "SHEL":    // Shells
            case "ROCK":    // Rockets
            case "CELL":    // Cells
            case "STIM":    // Stimpak
            case "MEDI":    // Medikit
            case "ARM1":    // Green armor
            case "ARM2":    // Blue armor
            case "BON1":    // Health bonus
            case "BON2":    // Armor bonus
            case "BKEY":    // Blue key
            case "RKEY":    // Red key
            case "YKEY":    // Yellow key
            case "BSKU":    // Blue skull key
            case "RSKU":    // Red skull key
            case "YSKU":    // Yellow skull key
            case "SOUL":    // Soulsphere
            case "MEGA":    // Megaarmor
            case "PINV":    // Invulnerability
            case "PSTR":    // Berserk
            case "PINS":    // Invisibility
            case "SUIT":    // Radiation suit
            case "PMAP":    // Computer map
            case "PVIS":    // Light amplification
            case "ELEC":    // Tech pillar
                return 0;
                
            // Weapons use rotation 0
            case "PISG":    // Pistol
            case "PISF":    // Pistol flash
            case "SHTG":    // Shotgun
            case "SHTF":    // Shotgun flash
            case "CHGG":    // Chaingun
            case "CHGF":    // Chaingun flash
            case "MISG":    // Rocket launcher
            case "MISF":    // Rocket launcher flash
            case "PLSG":    // Plasma rifle
            case "PLSF":    // Plasma rifle flash
            case "BFGG":    // BFG
            case "BFGF":    // BFG flash
                return 0;
                
            // Default to rotation 1 for unknown sprites (most monsters)
            default:
                return 1;
        }
    }
}