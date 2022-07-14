package studio.webui.model;

import java.nio.file.Path;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

public interface DeviceDTOs {

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    final class UuidDTO {
        private String uuid;
        private String path;
        private String driver; // PackFormat (in lowercase)
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    final class UuidsDTO {
        private List<String> uuids;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    final class OutputDTO {
        private Path outputPath;
    }
}
