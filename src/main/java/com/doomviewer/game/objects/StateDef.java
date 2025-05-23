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
        this.action = (action != null) ? action : Actions.NULL_ACTION;
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
        // R = Rotation (0-8, 1 for monsters with single facing sprite)
        char frameChar = (char) ('A' + getFrameIndex());
        int rotation = 1; // Changed to 1 to match available sprite files (POSSA1, POSSB1, etc.)
        return String.format("%s%c%d", spriteName.getName(), frameChar, rotation);
    }
}