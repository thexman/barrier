package com.a9ski.barrier.orchestrator;

import java.nio.file.Path;
import java.util.List;

import com.a9ski.barrier.recognizer.PlateDetection;
import com.a9ski.barrier.recognizer.RecognitionResult;

/** Returns a fixed plate for every image — used with {@code --mock-plate}. */
public final class FixedPlateRecognizer implements PlateRecognizer {

    private final String text;
    private final double confidence;

    public FixedPlateRecognizer(String text) {
        this(text, 0.99);
    }

    public FixedPlateRecognizer(String text, double confidence) {
        this.text = text;
        this.confidence = confidence;
    }

    @Override
    public RecognitionResult recognize(Path imagePath) {
        PlateDetection plate = new PlateDetection(text, confidence, confidence, 0, 0, 100, 50);
        return new RecognitionResult(imagePath.toString(), List.of(plate));
    }
}
