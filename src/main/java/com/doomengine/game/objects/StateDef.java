package com.doomengine.game.objects;

public class StateDef {
    public final SpriteNames spriteName; // From SPR_XXX
    public final int frameData;        // Raw frame data from info.c (includes index and fullbright)
    public final int tics;             // Duration in game tics (1 tic = 1/35th second)
    public final MobjAction action;    // Action function to call
    public final StateNum nextState;   // Next state to transition to

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
        int rotation = getRotationForSprite(spriteName, getFrameIndex());
        return String.format("%s%c%d", spriteName.getName(), frameChar, rotation);
    }
    
    private int getRotationForSprite(SpriteNames spriteName, int frameIndex) {
        // Death frames (H and beyond, frameIndex >= 7) always use rotation 0
        // This is because dead enemies only have one rotation in WAD files
        if (frameIndex >= 7) {
            switch (spriteName) {
                case POSS:    // Zombieman
                case SPOS:    // Shotgun guy
                case TROO:    // Imp
                case SARG:    // Demon
                case HEAD:    // Cacodemon
                case BOSS:    // Baron of Hell
                case SKUL:    // Lost Soul
                case PAIN:    // Pain Elemental
                case FATB:    // Mancubus
                case SKEL:    // Revenant
                case CPOS:    // Chaingunner
                case VILE:    // Archvile
                case SPID:    // Spider Mastermind
                case BSPI:    // Arachnotron
                case CYBR:    // Cyberdemon
                    return 0; // Death frames use rotation 0
            }
        }

        // Living frames: Different sprite types use different rotations
        return switch (spriteName) {
            // Monsters/enemies use rotation 1 for living frames
            // Zombieman
            // Shotgun guy
            // Imp
            // Demon
            // Cacodemon
            // Baron of Hell
            // Lost Soul
            // Pain Elemental
            // Mancubus
            // Revenant
            // Chaingunner
            // Archvile
            // Spider Mastermind
            // Arachnotron
            case POSS, SPOS, TROO, SARG, HEAD, BOSS, SKUL, PAIN, FATB, SKEL, CPOS, VILE, SPID, BSPI,
                 CYBR ->    // Cyberdemon
                    1;

            // Items, decorations, and barrels use rotation 0
            // Barrels
            // Barrel explosions
            // Ammo
            // Shells
            // Rockets
            // Cells
            // Stimpak
            // Medikit
            // Green armor
            // Blue armor
            // Health bonus
            // Armor bonus
            // Blue key
            // Red key
            // Yellow key
            // Blue skull key
            // Red skull key
            // Yellow skull key
            // Soulsphere
            // Megaarmor
            // Invulnerability
            // Berserk
            // Invisibility
            // Radiation suit
            // Computer map
            // Light amplification
            case BAR1, BEXP, CLIP, SHEL, ROCK, CELL, STIM, MEDI, ARM1, ARM2, BON1, BON2, BKEY, RKEY, YKEY, BSKU, RSKU,
                 YSKU, SOUL, MEGA, PINV, PSTR, PINS, SUIT, PMAP, PVIS, ELEC ->    // Tech pillar
                    0;

            // Weapons use rotation 0
            // Pistol
            // Pistol flash
            // Shotgun
            // Shotgun flash
            // Chaingun
            // Chaingun flash
            // Rocket launcher
            // Rocket launcher flash
            // Plasma rifle
            // Plasma rifle flash
            // BFG
            case PISG, PISF, SHTG, SHTF, CHGG, CHGF, MISG, MISF, PLSG, PLSF, BFGG, BFGF ->    // BFG flash
                    0;

            // Default to rotation 1 for unknown sprites (most monsters)
            default -> 1;
        };
    }
}