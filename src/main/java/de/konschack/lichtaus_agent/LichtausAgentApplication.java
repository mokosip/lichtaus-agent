package de.konschack.lichtaus_agent;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
public class LichtausAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(LichtausAgentApplication.class, args);
	}

	@Bean
	public ToolCallbackProvider workCommitTools(WorkCommitService workCommitService) {
		return MethodToolCallbackProvider.builder().toolObjects(workCommitService).build();
	}

	@Bean
	public ToolCallbackProvider jiraActivityTools(JiraActivityService jiraActivityService) {
		return MethodToolCallbackProvider.builder().toolObjects(jiraActivityService).build();
	}

	@Bean
	public List<McpServerFeatures.SyncPromptSpecification> myPrompts() {
		var prompt = new McpSchema.Prompt("greeting", "A friendly greeting prompt",
				List.of(new McpSchema.PromptArgument("name", "The name to greet", true)));

		var promptSpecification = new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, getPromptRequest) -> {
			String nameArgument = (String) getPromptRequest.arguments().get("name");
			if (nameArgument == null) { nameArgument = "friend"; }
			var userMessage = new PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent("Hello " + nameArgument + "! How can I assist you today?"));
			return new McpSchema.GetPromptResult("A personalized greeting message", List.of(userMessage));
		});

		return List.of(promptSpecification);
	}



}
