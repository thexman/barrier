package com.a9ski.barrier.recognizer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HTTP client for the Python {@code barrier-recognizer-service} sidecar.
 *
 * <p>Posts local image paths to {@code POST /recognize/path} and parses the
 * JSON response into {@link RecognitionResult}.
 */
public final class RecognizerClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final URI recognizePathUrl;
    private final Duration timeout;

    public RecognizerClient(String baseUrl) {
        this(baseUrl, Duration.ofSeconds(60));
    }

    public RecognizerClient(String baseUrl, Duration timeout) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.recognizePathUrl = URI.create(normalized + "/recognize/path");
        this.timeout = timeout;
        // Uvicorn/FastAPI do not handle Java's default h2c Upgrade handshake;
        // the body is dropped and FastAPI returns 422. Stay on HTTP/1.1.
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
    }

    public RecognitionResult recognize(Path imagePath) {
        try {
            String body = JSON.writeValueAsString(Map.of("path", imagePath.toAbsolutePath().toString()));
            HttpRequest request = HttpRequest.newBuilder(recognizePathUrl)
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(timeout)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RecognizerException(
                        "recognizer returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return parseResult(response.body());
        } catch (RecognizerException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RecognizerException("recognizer request interrupted", e);
        } catch (IOException e) {
            throw new RecognizerException("recognizer request failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    static RecognitionResult parseResult(String json) throws IOException {
        Map<String, Object> root = JSON.readValue(json, Map.class);
        String source = String.valueOf(root.getOrDefault("source", ""));
        List<Map<String, Object>> rawPlates = (List<Map<String, Object>>) root.get("plates");
        List<PlateDetection> plates = rawPlates == null
                ? List.of()
                : rawPlates.stream().map(RecognizerClient::parsePlate).toList();
        return new RecognitionResult(source, plates);
    }

    @SuppressWarnings("unchecked")
    private static PlateDetection parsePlate(Map<String, Object> raw) {
        String text = String.valueOf(raw.getOrDefault("text", ""));
        double detConf = toDouble(raw.get("detection_confidence"));
        double ocrConf = toDouble(raw.get("ocr_confidence"));
        List<Number> bboxNums = (List<Number>) raw.get("bbox");
        int x1 = 0, y1 = 0, x2 = 0, y2 = 0;
        if (bboxNums != null && bboxNums.size() >= 4) {
            x1 = bboxNums.get(0).intValue();
            y1 = bboxNums.get(1).intValue();
            x2 = bboxNums.get(2).intValue();
            y2 = bboxNums.get(3).intValue();
        }
        return new PlateDetection(text, detConf, ocrConf, x1, y1, x2, y2);
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }
}
