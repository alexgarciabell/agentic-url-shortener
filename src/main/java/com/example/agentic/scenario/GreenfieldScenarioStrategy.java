package com.example.agentic.scenario;

import com.example.agentic.domain.ScenarioType;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class GreenfieldScenarioStrategy implements ScenarioStrategy {
    public ScenarioType type() {
        return ScenarioType.GREENFIELD;
    }

    public ScenarioDecision prepare(String r, Path w) {
        return new ScenarioDecision(type(), r.trim(), List.of(), "Bootstrap a complete Java 21 Maven application in the target workspace.");
    }
}
