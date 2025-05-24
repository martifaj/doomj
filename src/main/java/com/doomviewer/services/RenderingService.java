package com.doomviewer.services;

public interface RenderingService {
    int[] getFramebuffer();
    double[] getDepthBuffer();
    void clearFramebuffer();
    void clearDepthBuffer();
}