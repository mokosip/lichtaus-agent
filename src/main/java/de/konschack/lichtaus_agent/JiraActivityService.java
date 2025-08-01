package de.konschack.lichtaus_agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.konschack.lichtaus_agent.config.JiraProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class JiraActivityService {
    private static final Logger log = LoggerFactory.getLogger(JiraActivityService.class);
    private final JiraProperties properties;
    private final RestClient restClient;

    public JiraActivityService(JiraProperties properties) {
        this.properties = properties;


        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Basic " + properties.getApiKey())
                .defaultHeader("Accept", "application/json")
                .build();
        log.info("JiraActivityService created with Jira API key for user: {}", properties.getEmail());
    }

    /**
     * Get recent activity for a user within a specified number of days.
     */
    @Tool(description = "Get recent Jira activity for a user")
    public UserActivity getRecentJiraActivity(
            @ToolParam(description = "Number of days to look back") int days,
            @ToolParam(description = "Jira username or email", required = false) String username
    ) {
        // If username is not provided, use the email from properties
        String user = username != null ? username : properties.getEmail();

        String jqlQuery = String.format("worklogAuthor = '%s' AND (worklogDate >= '%s' OR updated >= '%s')",
                user, "-"+days+"d", "-"+days+"d");
        String dateRange = "for the past " + days + " days.";

        log.info("Fetching recent Jira activity for user {} in the last {} days", user, days);

        // Get issues with worklogs for the user in the specified date range
        String rawResponse = restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/rest/api/3/search")
                        .queryParam("jql", jqlQuery)
                        .queryParam("fields", "summary,worklog,statusCategory,labels,timetracking")
                        .build())
                .retrieve()
                .body(String.class);

        SearchResponse response;
        try {
            ObjectMapper mapper = new ObjectMapper();
            response = mapper.readValue(rawResponse, SearchResponse.class);
        } catch (Exception e) {
            log.error("Error parsing JSON response: ", e);
            throw new RuntimeException("Failed to parse Jira response", e);
        }

        assert response != null;
        log.info("response from search api {} {}",response, Arrays.stream(response.issues().clone()));

        // Calculate total time spent
        int totalTimeSpentSeconds = Arrays.stream(response.issues())
                .filter(issue -> issue.fields() != null && issue.fields().timetracking() != null)
                .mapToInt(issue -> issue.fields().timetracking().timeSpentSeconds())
                .sum();

        log.info("Found {} issues with activity for user {} in the last {} days",
                response.issues().length, user, days);

        return new UserActivity(user, dateRange, response.issues(), totalTimeSpentSeconds);
    }
    @Tool(description = "Get todays Jira activity")
    public UserActivity getTodaysJiraActivity(
            @ToolParam(description = "Jira username or email", required = false) String username
    ) {
        // If username is not provided, use the email from properties
        String user = username != null ? username : properties.getEmail();
        LocalDate today = LocalDate.now();

        String jqlQuery = String.format("worklogAuthor = '%s' AND updated >= startOfDay() AND updated < endOfDay()",
                user);

        log.info("Fetching Jira activity for user {} for today {} with query {}", user, today, jqlQuery);

        // Get issues with worklogs for the user in the specified date range
        SearchResponse response = restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/rest/api/3/search")
                        .queryParam("jql", jqlQuery)
                        .queryParam("fields", "summary, worklog, statusCategory, labels, subtasks, timetracking")
                        .build())
                .retrieve()
                .toEntity(SearchResponse.class)
                .getBody();

        assert response != null;
        log.info("response from search api {} {}", response, Arrays.stream(response.issues().clone()).findFirst());

        // Calculate total time spent
        int totalTimeSpentSeconds = Arrays.stream(response.issues())
                .filter(issue -> issue.fields() != null && issue.fields().timetracking() != null)
                .mapToInt(issue -> issue.fields().timetracking().timeSpentSeconds())
                .sum();

        log.info("Found {} issues with activity for user {} for today {}",
                response.issues().length, user,today);

        return new UserActivity(user,  today.toString(), response.issues(), totalTimeSpentSeconds);
    }
}

//summary,worklog,statusCategory,labels,timeestimate,status,timetracking
@JsonIgnoreProperties(ignoreUnknown = true)
record UserActivity(
        String user,
        String dateRange,
        Issue[] issues,
        int totalTimeSpentSeconds) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record SearchResponse(
        int total,
        Issue[] issues) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record Issue(
        String id,
        String key,
        @JsonProperty("self") String url,
        Fields fields) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record Fields(
        String summary,
        TimeTracking timetracking,
        Worklog worklog,
        Status status,
        String[] labels,
        Issue[] subtasks) {
    @Override
    public String toString() {
        return "\n****Fields[summary=" + summary +
                ", timetracking=" + timetracking +
                ", worklog=" + worklog +
                ", labels=" + Arrays.toString(labels) +
                ", subtasks=" + Arrays.toString(subtasks) + "]****\n";
    }

}

@JsonIgnoreProperties(ignoreUnknown = true)
record Status(
        String name,
        String description,
        StatusCategory statusCategory) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record StatusCategory(String name) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record TimeTracking(
        @JsonProperty("originalEstimateSeconds") int originalEstimateSeconds,
        @JsonProperty("remainingEstimateSeconds") int remainingEstimateSeconds,
        @JsonProperty("timeSpentSeconds") int timeSpentSeconds) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record Worklog(
        int total,
        WorklogEntry[] worklogs) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record ContentBlock(
    String type,
    String text,
    Object content  // This could be further structured if needed
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record Comment(
    ContentBlock[] content,
    String type
) {
    public String getPlainText() {
        if (content == null) return "";
        return Arrays.stream(content)
            .filter(block -> block.text() != null)
            .map(ContentBlock::text)
            .collect(Collectors.joining(" "));
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
record WorklogEntry(
        String id,
        @JsonProperty("self") String url,
        Author author,
        @JsonProperty("timeSpentSeconds") int timeSpentSeconds,
        String started,
        String updated,
        Comment comment) {
    
    public String getCommentText() {
        return comment != null ? comment.getPlainText() : "";
    }
}
@JsonIgnoreProperties(ignoreUnknown = true)
record Author(
        @JsonProperty("accountId") String accountId,
        @JsonProperty("emailAddress") String email,
        @JsonProperty("displayName") String displayName) {}
//    /**
//     * Get user activity for a specific date.
//     * This includes issues created, updated, and commented on by the user.
//     */
//    @Tool(description = "Get Jira user activity for a specific date")
//    public UserActivity getUserActivityForDate(
//            @ToolParam(description = "Date to check activity for (YYYY-MM-DD)") String date,
//            @ToolParam(description = "Jira username or email", required = false) String username
//    ) {
//        // If username is not provided, use the email from properties
//        String user = username != null ? username : properties.getEmail();
//
//        // Parse the date
//        LocalDate activityDate = LocalDate.parse(date, DateTimeFormatter.ISO_DATE);
//        String jqlQuery = String.format("worklogAuthor = '%s' AND worklogDate = '%s'",
//                user, activityDate.format(DateTimeFormatter.ISO_DATE));
//
//        log.info("Fetching Jira activity for user {} on date {}", user, date);
//
//        // Get issues with worklogs for the user on the specified date
//        SearchResponse response = restClient
//                .get()
//                .uri(uriBuilder -> uriBuilder
//                        .path("/rest/api/3/search")
//                        .queryParam("jql", jqlQuery)
//                        .queryParam("fields", "summary,worklog,statusCategory,labels,timetracking")
//                        .build())
//                .retrieve()
//                .toEntity(SearchResponse.class)
//                .getBody();
//
//        if (response == null || response.issues() == null) {
//            log.warn("No Jira activity found for user {} on date {}", user, date);
//            return new UserActivity(user, date, new Issue[0], 0);
//        }
//
//        // Calculate total time spent
//        int totalTimeSpentSeconds = Arrays.stream(response.issues())
//                .filter(issue -> issue.fields() != null && issue.fields().timetracking() != null)
//                .mapToInt(issue -> issue.fields().timetracking().timeSpentSeconds())
//                .sum();
//
//        log.info("Found {} issues with activity for user {} on date {}",
//                response.issues().length, user, date);
//
//        return new UserActivity(user, date, response.issues(), totalTimeSpentSeconds);
//    }
//