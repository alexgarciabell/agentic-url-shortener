package com.example.agentic.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoAgentModelClientTest {
    @Test
    void greenfieldCreatesPomAndTests() {
        var r = new DemoAgentModelClient().execute("", "Phase: GREENFIELD_BOOTSTRAP");
        assertTrue(r.operations().stream().anyMatch(o -> o.path().equals("pom.xml")));
        assertTrue(r.operations().stream().anyMatch(o -> o.path().startsWith("src/test/")));
    }
}
