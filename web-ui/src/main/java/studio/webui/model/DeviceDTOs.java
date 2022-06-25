package studio.webui.model;

import java.nio.file.Path;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

public interface DeviceDTOs {

    @Getter
    @AllArgsConstructor
    final class UuidDTO {
        private String uuid;
        private String path;
        private String driver; // PackFormat (in lowercase)
    }

    @Getter
    @AllArgsConstructor
    final class UuidsDTO {
        private List<String> uuids;
    }

    @Getter
    @AllArgsConstructor
    final class OutputDTO {
        private Path outputPath;
    }

    @Getter
    @Setter
    final class DeviceInfosDTO {
        private String uuid;
        private String serial;
        private String firmware;
        private String driver; // PackFormat
        private boolean error;
        private boolean plugged;
        private StorageDTO storage;

        @Getter
        @AllArgsConstructor
        public static final class StorageDTO {
            private long size;
            private long free;
            private long taken;
        }
    }
}
