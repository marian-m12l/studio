package studio.core.v1.model;

import static studio.core.v1.utils.io.FileUtils.readableByteSize;

import java.util.UUID;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

public interface TransferListener {

    @FunctionalInterface
    public interface TransferProgressListener {
        void onProgress(TransferStatus status);
    }

    @Getter
    @RequiredArgsConstructor
    @Slf4j
    final class TransferStatus {

        private final long startTime = System.currentTimeMillis();
        private final UUID uuid;
        private final long total;

        private long transferred = 0;
        private double speed;

        /** Check if transfer is complete. */
        public boolean isDone() {
            return transferred == total;
        }

        /** Transfer percent. */
        public double getPercent() {
            return transferred / (double) total;
        }

        public void update(long delta) {
            transferred += delta;
            long elapsed = System.currentTimeMillis() - startTime;
            speed = transferred / (elapsed / 1000.0);
            if (log.isTraceEnabled()) {
                log.trace("Transferred {} in {}ms, avg speed {}/sec", //
                   readableByteSize(transferred), elapsed, readableByteSize((long) speed));
            }
        }
    }
}