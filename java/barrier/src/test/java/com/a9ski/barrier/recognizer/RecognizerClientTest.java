package com.a9ski.barrier.recognizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RecognizerClientTest {

    @Test
    void parseResultMapsPlates() throws Exception {
        String json = """
                {
                  "source": "/tmp/frame.jpg",
                  "plates": [
                    {
                      "text": "AB1234",
                      "detection_confidence": 0.91,
                      "ocr_confidence": 0.88,
                      "bbox": [10, 20, 110, 70]
                    }
                  ]
                }
                """;
        RecognitionResult result = RecognizerClient.parseResult(json);
        assertEquals("/tmp/frame.jpg", result.source());
        assertEquals(1, result.plates().size());
        PlateDetection plate = result.plates().get(0);
        assertEquals("AB1234", plate.text());
        assertEquals(0.88, plate.ocrConfidence(), 1e-9);
        assertEquals(10, plate.x1());
        assertEquals(70, plate.y2());
    }
}
