package com.doomviewer.game;

public enum KeyType {
    BLUE_KEY(1, "Blue Key"),
    YELLOW_KEY(2, "Yellow Key"), 
    RED_KEY(3, "Red Key"),
    BLUE_SKULL(4, "Blue Skull Key"),
    YELLOW_SKULL(5, "Yellow Skull Key"),
    RED_SKULL(6, "Red Skull Key");
    
    public final int id;
    public final String name;
    
    KeyType(int id, String name) {
        this.id = id;
        this.name = name;
    }
    
    public static KeyType fromThingType(int thingType) {
        switch (thingType) {
            case 5: return BLUE_KEY;
            case 6: return YELLOW_KEY;
            case 13: return RED_KEY;
            case 39: return BLUE_SKULL;
            case 40: return YELLOW_SKULL;
            case 38: return RED_SKULL;
            default: return null;
        }
    }
    
    // Check if a key type matches another (skull keys work as regular keys)
    public boolean matches(KeyType other) {
        if (this == other) return true;
        
        // Skull keys work as regular keys
        switch (this) {
            case BLUE_KEY:
            case BLUE_SKULL:
                return other == BLUE_KEY || other == BLUE_SKULL;
            case YELLOW_KEY:
            case YELLOW_SKULL:
                return other == YELLOW_KEY || other == YELLOW_SKULL;
            case RED_KEY:
            case RED_SKULL:
                return other == RED_KEY || other == RED_SKULL;
            default:
                return false;
        }
    }
}