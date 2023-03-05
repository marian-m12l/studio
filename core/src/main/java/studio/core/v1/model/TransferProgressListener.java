package studio.core.v1.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@FunctionalInterface
public interface TransferProgressListener {

    void onProgress(TransferStatus status);

    @Getter
    @Setter
    @AllArgsConstructor
    final class TransferStatus {
        private long transferred;
        private long total;
        private double speed;

        /** Check if transfer is complete. */
        public boolean isDone() {
            return transferred == total;
        }

        /** Transfer percent. */
        public double getPercent() {
            return transferred / (double) total;
        }
    }
}