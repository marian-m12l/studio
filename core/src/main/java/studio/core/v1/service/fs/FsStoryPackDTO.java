package studio.core.v1.service.fs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

public interface FsStoryPackDTO {

    @RequiredArgsConstructor
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

        // Compute .content folder with last 4 bytes of uuid.
        public Path getPackFolder(UUID uuid) {
            String fullUuid = uuid.toString().replace("-", "").toUpperCase();
            String shortUuid = fullUuid.substring(fullUuid.length() - 8);
            return getContentFolder().resolve(shortUuid);
        }
    }

    @RequiredArgsConstructor
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

        public UUID getUuid() {
            // Folder name is the uuid (minus the optional timestamp suffix)
            String s = fsPath.getFileName().toString().split("\\.", 2)[0];
            return (s == null) ? null : UUID.fromString(s);
        }

        public boolean isNightModeAvailable() {
            return Files.exists(getNightMode());
        }
    }
}
