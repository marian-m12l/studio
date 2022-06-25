package studio.webui.model;

import java.util.List;
import java.util.concurrent.CompletionStage;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Getter;

public interface EvergreenDTOs {

    String ANNOUNCE_EN = "### Good news, everyone!\n\nSTUdio is improving, at a slow but steady pace, and that's primarily thanks to your feedback. Thanks for letting me know what's missing or broken (and also what's not \uD83D\uDE01)!\n\nTo better benefit from this feedback, I wanted a way to communicate directly to you and let you know how STUdio is evolving. So here it is: **a brand new announce mechanism!**\n\nI plan to use it sparsely, to announce major features and occasionally request your feedback (a beta version will be released soon). These announces **are only displayed once**, and **you can opt-out if you feel like it**.\n\nHere's to all the great story packs you're building! \uD83C\uDF7B";
    String ANNOUNCE_FR = "### Good news, everyone!\n\nSTUdio s'améliore, lentement mais sûrement, et le mérite en revient grandement à tous vos retours. Merci de me faire savoir ce qu'il manque ou ce qui est cassé (et aussi ce qui ne l'est pas \uD83D\uDE01) !\n\nPour tirer avantage au mieux de vos retours, je souhaitais pouvoir communiquer directement avec vous pour vous faire part des évolutions de STUdio. Alors le voici : **le tout nouveau mécanisme d'annonces !**\n\nJe prévois de l'utiliser avec parcimonie, pour annoncer les fonctionnalités majeures et faire appel à vous occasionnellement (une version bêta va bientôt voir le jour). Ces annonces **ne s'afficheront qu'une fois**, et **vous pouvez les désactiver si vous le souhaitez**.\n\nÀ tous les packs d'histoires que vous créerez ! \uD83C\uDF7B";
    String DEFAULT_ANNOUNCE_CONTENT = ANNOUNCE_EN + "\n\n-----\n\n" + ANNOUNCE_FR;
    String DEFAULT_ANNOUNCE_DATE = "2020-05-12T00:00:00.000Z";

    @RegisterRestClient(baseUri = "https://api.github.com/repos/marian-m12l/studio")
    interface GithubClient {
        @GET
        @Path("/releases/latest")
        CompletionStage<LatestVersionDTO> latestRelease();

        @GET
        @Path("/commits")
        CompletionStage<List<CommitDto>> commits(@QueryParam("path") String path);
    }

    @RegisterRestClient(baseUri = "https://raw.githubusercontent.com/marian-m12l/studio")
    interface GithubRawClient {
        @GET
        @Path("/master/ANNOUNCE.md")
        CompletionStage<String> announce();
    }

    @Getter
    @AllArgsConstructor
    final class AnnounceDTO {
        private String date;
        private String content;
    }

    @Getter
    @AllArgsConstructor
    final class VersionDTO {
        private String version;
        private String timestamp;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    final class LatestVersionDTO {
        private String name;
        private String publishedAt;
        private String htmlUrl;
    }

    @Getter
    @AllArgsConstructor
    final class CommitDto {
        private Commit commit;

        @Getter
        @AllArgsConstructor
        public static final class Commit {
            private Committer committer;
        }

        @Getter
        @AllArgsConstructor
        public static final class Committer {
            private String name;
            private String date;
        }
    }

}
