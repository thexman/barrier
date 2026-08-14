package com.a9ski.barrier.recognizer;

/** Thrown when the recognizer sidecar is unreachable or returns an error. */
public final class RecognizerException extends RuntimeException {

    public RecognizerException(String message) {
        super(message);
    }

    public RecognizerException(String message, Throwable cause) {
        super(message, cause);
    }
}
