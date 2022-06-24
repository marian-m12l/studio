package studio.webui.model;

import java.nio.file.Path;
import java.util.List;

import lombok.Data;
import lombok.Value;

public interface DeviceDTOs {

    @Data
    static final class UuidDTO {
        private String uuid;
        private String path;
        private String driver; // PackFormat (in lowercase)
    }

    @Data
    static final class UuidsDTO {
        private List<String> uuids;
    }

    @Value
    static class OutputDTO {
        private Path outputPath;
    }

    @Data
    static final class DeviceInfosDTO {
        private String uuid;
        private String serial;
        private String firmware;
        private String driver; // PackFormat
        private boolean error;
        private boolean plugged;
        private StorageDTO storage;

        @Value
        public static class StorageDTO {
            private long size;
            private long free;
            private long taken;
        }
    }
}
