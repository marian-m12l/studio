package studio.core.v1.utils.stream;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import studio.core.v1.utils.exception.StoryTellerException;

/**
 * Handle checked exception in lambda consumer.
 * 
 * @see https://dzone.com/articles/how-to-handle-checked-exception-in-lambda-expressi
 *
 * @param <T> function consumer
 * @param <E> checked exception
 */
public interface ThrowingConsumer<T, E extends Exception> {

    void accept(T t) throws E;

    static <T, E extends Exception> Consumer<T> unchecked(ThrowingConsumer<T, E> consumer) {
        return t -> {
            try {
                consumer.accept(t);
            } catch (Exception e) {
                // Stop current parallel stream (if any)
                if(!ForkJoinPool.commonPool().isQuiescent()) {
                    ForkJoinPool.commonPool().awaitQuiescence(10, TimeUnit.SECONDS);
                }
                // custom RuntimeException
                throw new StoryTellerException(e);
            }
        };
    }
}
