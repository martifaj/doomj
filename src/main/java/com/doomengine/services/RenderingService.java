package com.doomengine.services;

public interface RenderingService {
    int[] getFramebuffer();
    double[] getDepthBuffer();
    void clearFramebuffer();
    void clearDepthBuffer();
}