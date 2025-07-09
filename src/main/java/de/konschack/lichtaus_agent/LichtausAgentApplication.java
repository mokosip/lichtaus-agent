package de.konschack.lichtaus_agent;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LichtausAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(LichtausAgentApplication.class, args);
	}

	@Bean
	public ToolCallbackProvider workCommitTools(WorkCommitService workCommitService) {
		return MethodToolCallbackProvider.builder().toolObjects(workCommitService).build();
	}

}
