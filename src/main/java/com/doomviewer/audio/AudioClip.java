package com.doomviewer.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class AudioClip {
    private final byte[] data;
    private final AudioFormat format;
    
    public AudioClip(byte[] rawData) throws UnsupportedAudioFileException, IOException {
        byte[] tempData;
        AudioFormat tempFormat;
        
        // Try to interpret as WAV first
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(rawData);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(bais);
            tempFormat = audioStream.getFormat();
            tempData = audioStream.readAllBytes();
            audioStream.close();
        } catch (UnsupportedAudioFileException | IOException e) {
            // If not WAV, assume it's raw DOOM sound data
            DoomSoundResult result = convertDoomSound(rawData);
            tempData = result.data;
            // Use the actual sample rate from DOOM header
            tempFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,  // Changed to PCM_SIGNED
                result.sampleRate,                // Use actual sample rate from header
                8,                                // Sample size in bits
                1,                                // Channels (mono)
                1,                                // Frame size
                result.sampleRate,                // Frame rate
                false                             // Little endian
            );
        }
        
        this.data = tempData;
        this.format = tempFormat;
    }
    
    private static class DoomSoundResult {
        final byte[] data;
        final float sampleRate;
        
        DoomSoundResult(byte[] data, float sampleRate) {
            this.data = data;
            this.sampleRate = sampleRate;
        }
    }
    
    private DoomSoundResult convertDoomSound(byte[] doomData) {
        if (doomData.length < 8) {
            throw new IllegalArgumentException("Invalid DOOM sound data - too short");
        }
        
        // DOOM sound format:
        // bytes 0-1: format (should be 3)
        // bytes 2-3: sample rate
        // bytes 4-7: number of samples
        // bytes 8+: sound data
        
        int format = (doomData[1] << 8) | (doomData[0] & 0xFF);
        int sampleRate = (doomData[3] << 8) | (doomData[2] & 0xFF);
        // Read as unsigned 32-bit integer
        long numSamplesLong = ((long)(doomData[7] & 0xFF) << 24) | ((long)(doomData[6] & 0xFF) << 16) | 
                             ((long)(doomData[5] & 0xFF) << 8) | (doomData[4] & 0xFF);
        int numSamples = (int) numSamplesLong;
        
        if (format != 3) {
            // If not standard DOOM format, return raw data as-is with default sample rate
            return new DoomSoundResult(doomData, 11025f);
        }
        
        if (numSamples < 0 || numSamplesLong > Integer.MAX_VALUE || doomData.length < 8 + numSamples) {
            throw new IllegalArgumentException("Invalid DOOM sound data - corrupted header or truncated");
        }
        
        // Extract sound data (skip header)
        byte[] soundData = new byte[numSamples];
        System.arraycopy(doomData, 8, soundData, 0, numSamples);
        
        // DOOM sounds are stored as unsigned 8-bit, centered around 128
        // Convert to signed 8-bit for Java audio system
        for (int i = 0; i < soundData.length; i++) {
            int unsigned = soundData[i] & 0xFF;  // Convert to unsigned int
            soundData[i] = (byte) (unsigned - 128);  // Convert to signed centered around 0
        }
        
        return new DoomSoundResult(soundData, (float) sampleRate);
    }
    
    public byte[] getData() {
        return data;
    }
    
    public AudioFormat getAudioFormat() {
        return format;
    }
}