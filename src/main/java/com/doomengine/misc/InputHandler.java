package com.doomengine.misc;

import com.doomengine.services.InputService;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class InputHandler implements InputService, KeyListener {
    private final Set<Integer> pressedKeys = new HashSet<>();
    private final CopyOnWriteArrayList<KeyListener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void keyPressed(KeyEvent e) {
        pressedKeys.add(e.getKeyCode());
        // Forward to registered listeners
        for (KeyListener listener : listeners) {
            listener.keyPressed(e);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        pressedKeys.remove(e.getKeyCode());
        // Forward to registered listeners
        for (KeyListener listener : listeners) {
            listener.keyReleased(e);
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Forward to registered listeners
        for (KeyListener listener : listeners) {
            listener.keyTyped(e);
        }
    }

    @Override
    public boolean isKeyPressed(int keyCode) {
        return pressedKeys.contains(keyCode);
    }
    
    @Override
    public void addKeyListener(KeyListener listener) {
        listeners.add(listener);
    }
    
    @Override
    public void removeKeyListener(KeyListener listener) {
        listeners.remove(listener);
    }
}