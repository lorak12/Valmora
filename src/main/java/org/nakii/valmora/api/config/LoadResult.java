package org.nakii.valmora.api.config;

/**
 * A result of a loading operation, which can either be a success with a value or a failure with an error.
 * @param <T> the type of the successful result
 * @param <E> the type of the error message
 */
public class LoadResult<T, E> {
    private final T value;
    private final E error;
    private final boolean skipped;

    private LoadResult(T value, E error, boolean skipped) {
        this.value = value;
        this.error = error;
        this.skipped = skipped;
    }

    public static <T, E> LoadResult<T, E> success(T value) {
        return new LoadResult<>(value, null, false);
    }

    public static <T, E> LoadResult<T, E> failure(E error) {
        return new LoadResult<>(null, error, false);
    }

    /**
     * Neither a success nor an error: the section is deliberately not an entry of this type (e.g. a
     * shared-settings key living next to the entries). Loaders count it as neither loaded nor failed.
     */
    public static <T, E> LoadResult<T, E> skip() {
        return new LoadResult<>(null, null, true);
    }

    public boolean isSuccess() {
        return error == null && !skipped;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public T getValue() {
        return value;
    }

    public E getError() {
        return error;
    }
}
