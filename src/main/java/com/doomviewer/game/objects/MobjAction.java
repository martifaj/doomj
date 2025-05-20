package com.doomviewer.game.objects;

import com.doomviewer.game.DoomEngine;

@FunctionalInterface
public interface MobjAction {
    void execute(MapObject self, DoomEngine engine);
}