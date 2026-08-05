package com.example.agentic.scenario;

import com.example.agentic.domain.ScenarioType;

import java.nio.file.Path;

public interface ScenarioStrategy {
    ScenarioType type();

    ScenarioDecision prepare(String requirement, Path workspace);
}
