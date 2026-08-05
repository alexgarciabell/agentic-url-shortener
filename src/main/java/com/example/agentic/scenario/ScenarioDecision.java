package com.example.agentic.scenario;

import com.example.agentic.domain.ScenarioType;

import java.util.List;

public record ScenarioDecision(ScenarioType executionType, String normalizedRequirement, List<String> assumptions, String rationale) {
}
