package studio.core.v1.utils.stream;

import java.util.function.Function;

import studio.core.v1.utils.exception.StoryTellerException;

/**
 * Handle checked exception in lambda function.
 * 
 * @see https://dzone.com/articles/how-to-handle-checked-exception-in-lambda-expressi
 *
 * @param <T> function input
 * @param <R> function output
 * @param <E> checked exception
 */
@FunctionalInterface
public interface ThrowingFunction<T, R, E extends Exception> {

    R apply(T t) throws E;

    static <T, R, E extends Exception> Function<T, R> unchecked(ThrowingFunction<T, R, E> f) {
        return t -> {
            try {
                return f.apply(t);
            } catch (Exception e) {
                // custom RuntimeException
                throw new StoryTellerException(e);
            }
        };
    }
}
