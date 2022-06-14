package studio.webui.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.Data;
import lombok.Value;

public interface EvergreenDTOs {

    @Value
    public static class AnnounceDTO {
        private String date;
        private String content;
    }

    @Value
    public static class VersionDTO {
        private String version;
        private String timestamp;
    }

    @Value
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class LatestVersionDTO {
        private String name;
        private String publishedAt;
        private String htmlUrl;
    }

    @Data
    public static class CommitDto {
        private Commit commit;

        @Data
        public static class Commit {
            private Committer committer;
        }

        @Value
        public static class Committer {
            private String name;
            private String date;
        }
    }

}
