package com.a9ski.barrier.proximity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One proximity measurement plus its near/far classification.
 *
 * <p>Immutable record; safe to publish across threads and serialise to JSON.
 * The {@link #toJsonMap()} view is what the picocli subcommand hands to
 * Jackson so the emitted JSON stays stable even if we add more fields later.
 *
 * @param timestamp   wall-clock time the reading was captured
 * @param distanceCm  measured distance in centimetres, clamped to the sensor's
 *                    {@code maxDistanceCm} when the target is out of range
 * @param isNear      {@code true} iff {@code distanceCm <= thresholdCm} at
 *                    read time
 */
public record Reading(Instant timestamp, double distanceCm, boolean isNear) {

    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", timestamp.toString());
        m.put("distance_cm", distanceCm);
        m.put("is_near", isNear);
        return m;
    }
}
