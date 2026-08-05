package com.example.agentic.scenario;

import com.example.agentic.domain.ScenarioType;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class BrownfieldScenarioStrategy implements ScenarioStrategy {
    public ScenarioType type() {
        return ScenarioType.BROWNFIELD;
    }

    public ScenarioDecision prepare(String r, Path w) {
        if (!Files.exists(w.resolve("pom.xml"))) throw new IllegalArgumentException("Brownfield workspace must contain pom.xml");
        return new ScenarioDecision(type(), r.trim(), List.of("Preserve compatible behavior unless explicitly changed."), "Inspect the existing Maven project, establish a baseline, and apply the smallest scoped change.");
    }
}
