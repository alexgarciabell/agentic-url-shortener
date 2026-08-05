package com.example.agentic.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WorkflowRequest(@NotNull ScenarioType scenarioType, @NotBlank String requirement, @NotBlank String workspacePath) {
}
