package com.example.agentic.agent;

import com.example.agentic.domain.AgentResponse;

public interface AgentModelClient {
    AgentResponse execute(String systemPrompt, String userPrompt);
}
