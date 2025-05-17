package com.doomengine.services;

import com.doomengine.game.Player;

public interface GameEngineTmp {
    double getDeltaTime();
    int getCurrentSkillLevel();
    int[] getFramebuffer();
    double[] getDepthBuffer();
    boolean isRunning();
    Player getPlayer();
}