package de.konschack.lichtaus_agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "github")
@Configuration
public class GitHubProperties {
  private String token;
  private String baseUrl = "https://api.github.com";

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String apiurl) {
    this.baseUrl = apiurl;
  }
}
