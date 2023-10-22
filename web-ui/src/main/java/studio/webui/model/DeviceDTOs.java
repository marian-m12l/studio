package studio.webui.model;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

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
        private UUID uuid;
        private String path;
        private String driver; // PackFormat (in lowercase)
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    final class UuidsDTO {
        private List<UUID> uuids;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    final class OutputDTO {
        private Path outputPath;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    final class TransferDTO {
        private UUID transferId;
    }
}
