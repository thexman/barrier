package com.a9ski.barrier.recognizer;

/** One detected license plate on a single frame. */
public record PlateDetection(
        String text,
        double detectionConfidence,
        double ocrConfidence,
        int x1,
        int y1,
        int x2,
        int y2) {}
