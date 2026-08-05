package com.example.agentic.workspace;

import com.example.agentic.domain.FileOperation;
import com.example.agentic.domain.MutationPhase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MutationPolicyTest {

    private final MutationPolicy policy = new MutationPolicy();
    @TempDir
    Path directory;

    @Test
    void allowsPomDuringBootstrap() {
        FileOperation operation = new FileOperation(
                "pom.xml", "<project/>", FileOperation.Operation.WRITE);

        assertDoesNotThrow(() -> policy.assertAllowed(
                directory, operation, MutationPhase.GREENFIELD_BOOTSTRAP));
    }

    @Test
    void allowsCreatingNewRegressionTestDuringRepair() {
        FileOperation operation = new FileOperation(
                "src/test/java/example/NewRegressionTest.java",
                "class NewRegressionTest {}",
                FileOperation.Operation.WRITE);

        assertDoesNotThrow(() -> policy.assertAllowed(
                directory, operation, MutationPhase.IMPLEMENTATION_REPAIR));
    }

    @Test
    void protectsExistingTestsDuringRepair() throws Exception {
        Path existing = directory.resolve("src/test/java/example/ExistingTest.java");
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "class ExistingTest {}");

        FileOperation operation = new FileOperation(
                "src/test/java/example/ExistingTest.java",
                "class ExistingTest { /* modified */ }",
                FileOperation.Operation.WRITE);

        assertThrows(SecurityException.class, () -> policy.assertAllowed(
                directory, operation, MutationPhase.IMPLEMENTATION_REPAIR));
    }

    @Test
    void blocksTraversal() {
        FileOperation operation = new FileOperation(
                "../outside.txt", "x", FileOperation.Operation.WRITE);

        assertThrows(SecurityException.class, () -> policy.assertAllowed(
                directory, operation, MutationPhase.GREENFIELD_BOOTSTRAP));
    }
}
