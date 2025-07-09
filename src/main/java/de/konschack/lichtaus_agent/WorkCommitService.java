package de.konschack.lichtaus_agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.konschack.lichtaus_agent.config.GitHubProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Service
public class WorkCommitService {
    private static final Logger log = LoggerFactory.getLogger(WorkCommitService.class);
    private final GitHubProperties properties;
    private final RestClient restClient;

    public WorkCommitService(GitHubProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "token " + properties.getToken())
                .defaultHeader("Accept", "application/vnd.github+json")
                .build();
        log.info("WorkCommitService created with GitHub token: {}", properties.getToken());
    }

    /**
     * Get all events for a specific user.
     * This includes all types of events, not just commits.
     */
    @Tool(description = "Get all events for a specific user")
    public CommitEvent[] getAllUserEvents(
            @ToolParam(description = "GitHub user") String user,
            @ToolParam(description = "Number of days to look back from", required = false) Integer daysOffsetStart,
            @ToolParam(description = "Number of days to look back until, should be zero if today should be included", required = false) Integer daysOffsetEnd
    ) {
        CommitEvent[] events = restClient
                .get()
                .uri("/users/{username}/events", user).retrieve()
                .toEntity(CommitEvent[].class).getBody();

        if (daysOffsetStart != null && daysOffsetStart > 0) {
            LocalDateTime end = daysOffsetEnd!=null? LocalDate.now().minusDays(daysOffsetEnd).atTime(LocalTime.MAX) : LocalDate.now().atTime(LocalTime.MAX);
            int interval = daysOffsetEnd!=null? daysOffsetStart-daysOffsetEnd : daysOffsetStart;
            LocalDateTime cutoffDate = end.minusDays(interval);
            return Arrays.stream(events)
                    .filter(e -> {
                        LocalDateTime eventDate = LocalDateTime.parse(e.createdAt(),
                                DateTimeFormatter.ISO_DATE_TIME);
                        return eventDate.isAfter(cutoffDate);
                    })
                    .toArray(CommitEvent[]::new);
        }

        return events;
    }

    /*
     * Get commits for a specific repository.
     * Use this method when you want to get commits for a specific repository.
     */
    @Tool(description = "Get commits for a specific repository of which the user is the owner")
    public CommitInfo[] getRepositoryOwnerCommits(
            @ToolParam(description = "Name of the GitHub repository") String repo,
            @ToolParam(description = "Owner of the repository") String owner
    ) {
        return restClient
                .get()
                .uri("/repos/{owner}/{repo}/commits", owner, repo).retrieve()
                .toEntity(CommitInfo[].class).getBody();
    }
}

record CommitInfo(
        String sha,
        @JsonProperty("commit") CommitDetail commitDetail,
        @JsonProperty("html_url") String url,
        @JsonProperty("author") CommitUser author) {
}

record CommitDetail(
        String message,
        CommitAuthor author,
        CommitAuthor committer) {
}

record CommitAuthor(
        String name,
        String email,
        String date) {
}

record CommitUser(
        @JsonProperty("login") String username,
        @JsonProperty("id") Long id,
        @JsonProperty("avatar_url") String avatarUrl) {
}

record CommitEvent(
        String id,
        String type,
        @JsonProperty("actor") CommitUser actor,
        @JsonProperty("repo") Repository repo,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("payload") EventPayload payload) {
}

record Repository(
        Long id,
        String name,
        String url) {
}

record EventPayload(
        @JsonProperty("push_id") Long pushId,
        Integer size,
        @JsonProperty("distinct_size") Integer distinctSize,
        @JsonProperty("ref") String ref,
        @JsonProperty("head") String head,
        @JsonProperty("before") String before,
        @JsonProperty("commits") CommitEventDetail[] commits) {
}

record CommitEventDetail(
        String sha,
        String message,
        @JsonProperty("distinct") boolean distinct,
        String url,
        CommitUser author) {
}

record SearchResult(
        @JsonProperty("total_count") int totalCount,
        @JsonProperty("incomplete_results") boolean incompleteResults,
        @JsonProperty("items") CommitInfo[] items) {
}

//    /**
//     * Get only push events (containing commits) for a specific user.
//     * Use this method when you want to get only events that contain commits.
//     */
//    @Tool(description = "Get only push events (containing commits) for a specific user")
//    public CommitEvent[] getUserPushEvents(
//            @ToolParam(description = "GitHub username") String username
//    ) {
//        CommitEvent[] events = getAllUserEvents(username);
//        if (events == null) {
//            return new CommitEvent[0];
//        }
//
//        log.info("WorkCommitService getUserPushEvents returning events: {}", Arrays.toString(events));
//
//        return java.util.Arrays.stream(events)
//                .filter(event -> "PushEvent".equals(event.type()))
//                .toArray(CommitEvent[]::new);
//    }

//    /**
//     * Get all commits for a specific user (extracted from push events).
//     * Use this method when you want to get just the commit details from push events.
//     */
//    @Tool(description = "Get all commits for a specific user (extracted from push events)")
//    public CommitEventDetail[] getUserCommitDetails(
//            @ToolParam(description = "GitHub username") String username
//    ) {
//        CommitEvent[] pushEvents = getUserPushEvents(username);
//        if (pushEvents == null || pushEvents.length == 0) {
//            return new CommitEventDetail[0];
//        }
//
//        return java.util.Arrays.stream(pushEvents)
//                .filter(event -> event.payload() != null && event.payload().commits() != null)
//                .flatMap(event -> java.util.Arrays.stream(event.payload().commits()))
//                .toArray(CommitEventDetail[]::new);
//    }
//
/**
 * Search for all commits by a specific author across all repositories.
 * This is the most comprehensive way to get all commits by a user.
 * Use this method when you want to search for all commits by a specific author.
 */
//    @Tool(description = "Search for all commits by a specific author across all repositories")
//    public SearchResult searchUserCommits(
//            @ToolParam(description = "GitHub username or email") String author,
//            @ToolParam(description = "Search term to search commits for") String searchTerm
//    ) {
//        return RestClient.builder()
//                .baseUrl(properties.getBaseUrl())
//                .defaultHeader("Authorization", "token " + properties.getToken())
//                .defaultHeader("Accept", "application/vnd.github.cloak-preview+json")
//                .build()
//                .get()
//                .uri("/search/commits?q={searchTerm}+author:{author}", searchTerm, author).retrieve()
//                .toEntity(SearchResult.class).getBody();
//    }


//    @Tool(description = "Search for all commits including a special search term by a specific author across all repositories within a specific timeframe (days)")
//    public SearchResult getRecentCommitsForAuthor(
//            @ToolParam(description = "GitHub username or email") String author,
//            @ToolParam(description = "Term to search in the commit message for") String term,
//            @ToolParam(description = "Number of days to look back") int days
//    ) {
//        LocalDateTime fromDate = LocalDateTime.now().minusDays(days);
//        String dateQuery = fromDate.format(DateTimeFormatter.ISO_DATE);
//
//        String query = String.format("author:%s+committer-date:>%s", author, dateQuery);
//
//        return RestClient.builder()
//                .baseUrl(properties.getBaseUrl())
//                .defaultHeader("Authorization", "token " + properties.getToken())
//                .defaultHeader("Accept", "application/vnd.github.cloak-preview+json")
//                .build()
//                .get()
//                .uri("/search/commits?q={term}+{query}",term, query)
//                .retrieve()
//                .toEntity(SearchResult.class)
//                .getBody();
//    }

