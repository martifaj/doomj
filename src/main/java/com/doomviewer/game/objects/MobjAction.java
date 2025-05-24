package com.doomviewer.game.objects;

import com.doomviewer.game.ObjectManager;
import com.doomviewer.game.Player;
import com.doomviewer.services.AudioService;
import com.doomviewer.services.CollisionService;
import com.doomviewer.services.GameEngineTmp;

@FunctionalInterface
public interface MobjAction {
    void execute(MapObject self, ObjectManager objectManager, Player player, AudioService audioService, GameEngineTmp engineTmp, CollisionService collisionService);
}