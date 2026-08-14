package com.a9ski.barrier.recognizer;

import java.util.List;

/** All plates detected on a single image. */
public record RecognitionResult(String source, List<PlateDetection> plates) {}
