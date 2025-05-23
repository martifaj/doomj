package com.doomviewer.game;

public enum AmmoType {
    BULLETS(0, "Bullets", 200),
    SHELLS(1, "Shells", 50),
    ROCKETS(2, "Rockets", 50),
    CELLS(3, "Cells", 300);

    public final int id;
    public final String name;
    public final int maxAmmo;

    AmmoType(int id, String name, int maxAmmo) {
        this.id = id;
        this.name = name;
        this.maxAmmo = maxAmmo;
    }
}