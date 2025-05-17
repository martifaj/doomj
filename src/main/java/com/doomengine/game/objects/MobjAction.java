package com.doomengine.game.objects;

import com.doomengine.game.ObjectManager;
import com.doomengine.game.Player;
import com.doomengine.services.AudioService;
import com.doomengine.services.CollisionService;
import com.doomengine.services.GameEngineTmp;

@FunctionalInterface
public interface MobjAction {
    void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService);
}