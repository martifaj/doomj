package com.doomviewer.services;

import com.doomviewer.game.Player;
import com.doomviewer.misc.math.Vector2D;

public interface DoorService {
    boolean tryUseDoor(Player player, Vector2D position, double radius);
}