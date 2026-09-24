package org.nakii.valmora.database;

/**
 * Thrown (wrapped in a {@link java.util.concurrent.CompletionException}) when stored data exists
 * but could not be read. Distinct from "no data" — which the load methods report as {@code null} —
 * so callers never mistake a transient failure for a first join and overwrite the real data.
 */
public class DataLoadException extends RuntimeException {
    public DataLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
