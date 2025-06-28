package com.doomengine.services;

import com.doomengine.game.Player;
import com.doomengine.geometry.Vector2D;

public interface DoorService {
    boolean tryUseDoor(Player player, Vector2D position, double radius);

    boolean isDoorBlocking(int linedefId);
}