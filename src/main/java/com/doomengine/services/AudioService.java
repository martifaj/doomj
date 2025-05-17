package com.doomengine.services;

public interface AudioService {
    void playSound(String soundName);
    void playSound(String soundName, float volume, boolean loop);
    void stopSound(String soundName);
    void stopAllSounds();
    void setEnabled(boolean enabled);
    void setMasterVolume(float volume);
    boolean hasSound(String soundName);
    void loadSound(String soundName, byte[] soundData);
    void cleanup();
}