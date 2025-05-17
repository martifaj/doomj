package com.doomengine.game;

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
        return switch (thingType) {
            case 5 -> BLUE_KEY;
            case 6 -> YELLOW_KEY;
            case 13 -> RED_KEY;
            case 39 -> BLUE_SKULL;
            case 40 -> YELLOW_SKULL;
            case 38 -> RED_SKULL;
            default -> null;
        };
    }
    
    // Check if a key type matches another (skull keys work as regular keys)
    public boolean matches(KeyType other) {
        if (this == other) return true;
        
        // Skull keys work as regular keys
        return switch (this) {
            case BLUE_KEY, BLUE_SKULL -> other == BLUE_KEY || other == BLUE_SKULL;
            case YELLOW_KEY, YELLOW_SKULL -> other == YELLOW_KEY || other == YELLOW_SKULL;
            case RED_KEY, RED_SKULL -> other == RED_KEY || other == RED_SKULL;
            default -> false;
        };
    }
}