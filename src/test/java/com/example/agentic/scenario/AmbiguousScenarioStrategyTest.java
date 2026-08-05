package com.example.agentic.scenario;

import com.example.agentic.domain.ScenarioType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AmbiguousScenarioStrategyTest {
    @TempDir
    Path dir;

    @Test
    void choosesGreenfieldForEmptyWorkspace() {
        assertEquals(ScenarioType.GREENFIELD, new AmbiguousScenarioStrategy().prepare("make safer", dir).executionType());
    }

    @Test
    void choosesBrownfieldForMavenWorkspace() throws Exception {
        Files.writeString(dir.resolve("pom.xml"), "x");
        assertEquals(ScenarioType.BROWNFIELD, new AmbiguousScenarioStrategy().prepare("make safer", dir).executionType());
    }
}
