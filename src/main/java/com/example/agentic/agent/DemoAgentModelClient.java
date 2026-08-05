package com.example.agentic.agent;

import com.example.agentic.domain.AgentResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "agent.model.mode", havingValue = "demo", matchIfMissing = true)
public class DemoAgentModelClient implements AgentModelClient {
    public AgentResponse execute(String system, String user) {
        if (user.contains("GREENFIELD_BOOTSTRAP"))
            return new AgentResponse("Generated deterministic URL shortener", "Created a complete Java 21 Spring Boot target with H2, API endpoints, tests, and Actuator.", List.of(), DemoProjectTemplate.operations());
        return new AgentResponse("No-op deterministic change", "Demo mode preserves an existing brownfield project unless a real model is configured.", List.of("No repository-specific mutation was inferred."), List.of());
    }
}
