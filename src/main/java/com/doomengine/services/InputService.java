package com.doomengine.services;

public interface InputService {
    boolean isKeyPressed(int keyCode);
    void addKeyListener(java.awt.event.KeyListener listener);
    void removeKeyListener(java.awt.event.KeyListener listener);
}