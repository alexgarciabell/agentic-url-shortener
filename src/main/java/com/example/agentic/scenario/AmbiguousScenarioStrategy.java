package com.example.agentic.scenario;

import com.example.agentic.domain.ScenarioType;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class AmbiguousScenarioStrategy implements ScenarioStrategy {
    public ScenarioType type() {
        return ScenarioType.AMBIGUOUS;
    }

    public ScenarioDecision prepare(String r, Path w) {
        boolean existing = Files.exists(w.resolve("pom.xml"));
        ScenarioType selected = existing ? ScenarioType.BROWNFIELD : ScenarioType.GREENFIELD;
        String normalized = r.trim() + ". Apply the safest local, reversible interpretation: ensure URL destinations accept only HTTP/HTTPS and preserve valid existing behavior.";
        return new ScenarioDecision(selected, normalized, List.of("The ambiguous request is resolved to a low-risk URL-safety improvement.", "Destructive, security-sensitive, or public-contract changes are excluded."), "Workspace evidence selected " + selected + " execution.");
    }
}
