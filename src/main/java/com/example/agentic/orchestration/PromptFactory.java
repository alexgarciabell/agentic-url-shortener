package com.example.agentic.orchestration;

import com.example.agentic.scenario.ScenarioDecision;
import org.springframework.stereotype.Component;

@Component
public class PromptFactory {

    public String system() {
        return """
                You are a conservative Java 21 engineering agent.
                Return only structured file operations.
                
                Initial Greenfield bootstrap may create pom.xml, README.md,
                production source files, resources, and tests.
                
                Brownfield and repair rules:
                - Existing files under src/test/** are read-only.
                - Create a new uniquely named regression test instead of modifying an existing test.
                - pom.xml, Maven wrappers, Git metadata, CI files, and target/** are protected.
                - Never disable, delete, weaken, or bypass tests.
                - Never add @Disabled or remove assertions.
                - Repair production code under src/main/**.
                
                Produce complete files, not patches or shell commands.
                """;
    }

    public String implement(
            ScenarioDecision decision,
            String snapshot,
            boolean bootstrap) {

        return """
                Phase: %s
                Requirement: %s
                Rationale: %s
                Assumptions: %s
                
                Repository snapshot:
                %s
                
                %s
                
                The resulting project must pass mvn clean verify.
                """.formatted(
                bootstrap ? "GREENFIELD_BOOTSTRAP" : "IMPLEMENTATION_REPAIR",
                decision.normalizedRequirement(),
                decision.rationale(),
                decision.assumptions(),
                snapshot,
                bootstrap
                        ? """
                        Generate a complete runnable Java 21 Maven URL shortener.
                        It must expose create, redirect, analytics, delete, H2 persistence,
                        Actuator health, tests, and a README.
                        """
                        : """
                        Preserve the existing public API unless the requirement explicitly changes it.
                        Modify production code as needed. Do not modify existing tests.
                        Create a new regression test file when additional coverage is needed.
                        """);
    }

    public String repair(
            ScenarioDecision decision,
            String snapshot,
            String failure,
            int attempt) {

        return """
                Phase: IMPLEMENTATION_REPAIR
                Attempt: %d
                Requirement: %s
                
                Maven failure:
                %s
                
                Repository snapshot:
                %s
                
                Repair production code only.
                Existing tests and pom.xml are protected.
                If regression coverage is required, create a new test file with a unique name.
                Return only the minimum complete file operations needed to resolve the failure.
                """.formatted(
                attempt,
                decision.normalizedRequirement(),
                failure,
                snapshot);
    }
}
