package studio.core.v1.service.fs;

import java.nio.file.Files;
import java.nio.file.Path;

import lombok.AllArgsConstructor;

public interface FsStoryPackDTO {

    @AllArgsConstructor
    final class SdPartition {
        private final Path partitionPath;

        public static boolean isValid(Path path) {
            return Files.isRegularFile(path.resolve(".md"));
        }

        public Path getDeviceMetada() {
            return partitionPath.resolve(".md");
        }

        public Path getPackIndex() {
            return partitionPath.resolve(".pi");
        }

        public Path getContentFolder() {
            return partitionPath.resolve(".content");
        }
    }

    @AllArgsConstructor
    final class FsStoryPack {
        private final Path fsPath;

        public static boolean isValid(Path path) {
            return Files.isRegularFile(path.resolve("ni"));
        }

        public Path getNodeIndex() {
            return fsPath.resolve("ni");
        }

        public Path getListIndex() {
            return fsPath.resolve("li");
        }

        public Path getImageIndex() {
            return fsPath.resolve("ri");
        }

        public Path getImageFolder() {
            return fsPath.resolve("rf");
        }

        public Path getSoundIndex() {
            return fsPath.resolve("si");
        }

        public Path getSoundFolder() {
            return fsPath.resolve("sf");
        }

        public Path getNightMode() {
            return fsPath.resolve("nm");
        }

        public Path getBoot() {
            return fsPath.resolve("bt");
        }

        public boolean isNightModeAvailable() {
            return Files.exists(getNightMode());
        }
    }
}
