package org.nakii.valmora.item;

public class LoadResult<T, E> {
    private final T value;
    private final E error;

    private LoadResult(T value, E error) {
        this.value = value;
        this.error = error;
    }

    public static <T, E> LoadResult<T, E> success(T value) {
        return new LoadResult<>(value, null);
    }

    public static <T, E> LoadResult<T, E> failure(E error) {
        return new LoadResult<>(null, error);
    }

    public boolean isSuccess() {
        return error == null;
    }

    public T getValue() {
        return value;
    }

    public E getError() {
        return error;
    }
}
