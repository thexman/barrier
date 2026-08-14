package com.a9ski.barrier.orchestrator;

import java.nio.file.Path;

import com.a9ski.barrier.recognizer.RecognitionResult;

/** Recognizer backend used by the orchestrator (HTTP sidecar or mock stub). */
public interface PlateRecognizer {

    RecognitionResult recognize(Path imagePath);
}
