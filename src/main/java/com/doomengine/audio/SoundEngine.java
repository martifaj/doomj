package com.doomengine.audio;

import com.doomengine.services.AudioService;
import javax.sound.sampled.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SoundEngine implements AudioService {
    private static final Logger LOGGER = Logger.getLogger(SoundEngine.class.getName());
    private static SoundEngine instance;
    
    private final Map<String, AudioClip> soundClips = new ConcurrentHashMap<>();
    private final Map<String, Clip> activeSounds = new HashMap<>();
    private boolean enabled = true;
    private float masterVolume;
    
    public SoundEngine() {
        // Start with a lower master volume to prevent distortion
        this.masterVolume = 0.3f;
    }
    
    private SoundEngine(boolean unused) {
        // Private constructor for singleton pattern (deprecated)
        this.masterVolume = 0.3f;
    }
    
    public static synchronized SoundEngine getInstance() {
        if (instance == null) {
            instance = new SoundEngine();
        }
        return instance;
    }
    
    public void loadSound(String soundName, byte[] soundData) {
        if (soundData == null || soundData.length == 0) {
            LOGGER.warning("Empty sound data for: " + soundName);
            return;
        }
        
        try {
            AudioClip clip = new AudioClip(soundData);
            soundClips.put(soundName.toUpperCase(), clip);
            LOGGER.info("Loaded sound: " + soundName);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load sound: " + soundName, e);
        }
    }
    
    public void playSound(String soundName) {
        playSound(soundName, 1.0f, false);
    }
    
    public void playSound(String soundName, float volume, boolean loop) {
        if (!enabled) return;
        
        String upperName = soundName.toUpperCase();
        AudioClip audioClip = soundClips.get(upperName);
        
        if (audioClip == null) {
            LOGGER.warning("Sound not found: " + soundName);
            return;
        }
        
        try {
            Clip clip = AudioSystem.getClip();
            clip.open(audioClip.getAudioFormat(), audioClip.getData(), 0, audioClip.getData().length);
            
            // Set volume - use a more conservative approach to prevent distortion
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float finalVolume = Math.max(0.0001f, Math.min(1.0f, volume * masterVolume));
                float gain = 20f * (float) Math.log10(finalVolume);
                
                // Add some headroom to prevent clipping
                gain = Math.max(gain - 6f, volumeControl.getMinimum()); // -6dB headroom
                gain = Math.min(gain, volumeControl.getMaximum());
                volumeControl.setValue(gain);
            }
            
            if (loop) {
                clip.loop(Clip.LOOP_CONTINUOUSLY);
            } else {
                clip.start();
            }
            
            // Store for potential stopping
            activeSounds.put(upperName, clip);
            
            // Auto-cleanup when done
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                    activeSounds.remove(upperName);
                }
            });
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to play sound: " + soundName, e);
        }
    }
    
    public void stopSound(String soundName) {
        String upperName = soundName.toUpperCase();
        Clip clip = activeSounds.get(upperName);
        if (clip != null && clip.isRunning()) {
            clip.stop();
        }
    }
    
    public void stopAllSounds() {
        for (Clip clip : activeSounds.values()) {
            if (clip.isRunning()) {
                clip.stop();
            }
        }
        activeSounds.clear();
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            stopAllSounds();
        }
    }
    
    public void setMasterVolume(float volume) {
        this.masterVolume = Math.max(0.0f, Math.min(1.0f, volume));
    }
    
    public boolean hasSound(String soundName) {
        return soundClips.containsKey(soundName.toUpperCase());
    }
    
    public void cleanup() {
        stopAllSounds();
        soundClips.clear();
    }
    
    // Debug method to test sound quality
    public void testSound(String soundName) {
        System.out.println("Testing sound: " + soundName);
        AudioClip clip = soundClips.get(soundName.toUpperCase());
        if (clip != null) {
            System.out.println("  Format: " + clip.getAudioFormat());
            System.out.println("  Data length: " + clip.getData().length + " bytes");
            playSound(soundName, 0.5f, false);
        } else {
            System.out.println("  Sound not found!");
        }
    }
}