package studio.core.v1.service.fs;

import java.nio.file.Files;
import java.nio.file.Path;

import lombok.AllArgsConstructor;

public interface FsStoryPackDTO {

    @AllArgsConstructor
    final class SdPartition {
        private final Path sdPath;

        public static boolean isValid(Path path) {
            return Files.isRegularFile(path.resolve(".md"));
        }

        public Path getDeviceMetada() {
            return sdPath.resolve(".md");
        }

        public Path getPackIndex() {
            return sdPath.resolve(".pi");
        }

        public Path getContentFolder() {
            return sdPath.resolve(".content");
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

        public String getUuid() {
            // Folder name is the uuid (minus the eventual timestamp suffix)
            return fsPath.getFileName().toString().split("\\.", 2)[0];
        }

        public boolean isNightModeAvailable() {
            return Files.exists(getNightMode());
        }
    }
}
