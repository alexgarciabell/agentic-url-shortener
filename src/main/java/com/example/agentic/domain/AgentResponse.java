package com.example.agentic.domain;

import java.util.List;

public record AgentResponse(String summary, String rationale, List<String> assumptions, List<FileOperation> operations) {
}
