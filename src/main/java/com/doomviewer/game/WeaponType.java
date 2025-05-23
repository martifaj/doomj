package com.doomviewer.game;

public enum WeaponType {
    PISTOL(0, "Pistol", 1, "PISGA0", 12, 4),
    SHOTGUN(1, "Shotgun", 1, "SHTGA0", 8, 7),
    CHAINGUN(2, "Chaingun", 1, "MGUNA0", 4, 4),
    ROCKET_LAUNCHER(3, "Rocket Launcher", 1, "LAUNA0", 20, 2),
    PLASMA_RIFLE(4, "Plasma Rifle", 1, "PLASA0", 5, 2),
    BFG(5, "BFG9000", 40, "BFUGA0", 200, 1);

    public final int id;
    public final String name;
    public final int ammoPerShot;
    public final String spriteName;
    public final int damage;
    public final int fireRate; // Lower = faster

    WeaponType(int id, String name, int ammoPerShot, String spriteName, int damage, int fireRate) {
        this.id = id;
        this.name = name;
        this.ammoPerShot = ammoPerShot;
        this.spriteName = spriteName;
        this.damage = damage;
        this.fireRate = fireRate;
    }
}