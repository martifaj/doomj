package com.doomviewer.services;

import com.doomviewer.game.Player;

public interface GameEngineTmp {
    double getDeltaTime();
    int getCurrentSkillLevel();
    int[] getFramebuffer();
    double[] getDepthBuffer();
    boolean isRunning();
    Player getPlayer();
}