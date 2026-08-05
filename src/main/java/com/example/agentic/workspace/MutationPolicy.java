package com.example.agentic.workspace;

import com.example.agentic.domain.FileOperation;
import com.example.agentic.domain.MutationPhase;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class MutationPolicy {

    private static final List<String> ALWAYS_PROTECTED = List.of(
            ".git/",
            ".github/",
            "target/",
            "mvnw",
            "mvnw.cmd");

    public void assertAllowed(
            Path workspace,
            FileOperation operation,
            MutationPhase phase) {

        Path base = workspace.toAbsolutePath().normalize();
        Path target = base.resolve(operation.path()).normalize();

        if (!target.startsWith(base)) {
            throw new SecurityException("Path escapes workspace: " + operation.path());
        }

        String relativePath = base.relativize(target)
                .toString()
                .replace('\\', '/');

        if (matches(relativePath, ALWAYS_PROTECTED)) {
            throw new SecurityException("Protected path: " + relativePath);
        }

        if (phase == MutationPhase.GREENFIELD_BOOTSTRAP) {
            assertGreenfieldPath(relativePath);
            return;
        }

        assertImplementationMutation(target, relativePath, operation);
    }

    private void assertGreenfieldPath(String path) {
        boolean allowed = path.equals("pom.xml")
                || path.equals("README.md")
                || path.startsWith("src/main/")
                || path.startsWith("src/test/");

        if (!allowed) {
            throw new SecurityException(
                    "Path not allowed during Greenfield bootstrap: " + path);
        }
    }

    private void assertImplementationMutation(
            Path target,
            String relativePath,
            FileOperation operation) {

        if (relativePath.equals("pom.xml")) {
            throw new SecurityException("pom.xml requires human approval");
        }

        if (relativePath.startsWith("src/main/")) {
            return;
        }

        if (relativePath.startsWith("src/test/")) {
            assertTestMutationAllowed(target, relativePath, operation);
            return;
        }

        if (relativePath.equals("README.md")) {
            return;
        }

        throw new SecurityException(
                "Path outside allowed implementation scope: " + relativePath);
    }

    private void assertTestMutationAllowed(
            Path target,
            String relativePath,
            FileOperation operation) {

        if (Files.exists(target)) {
            throw new SecurityException(
                    "Existing test requires approval: " + relativePath);
        }

        if (operation.operation() != FileOperation.Operation.WRITE) {
            throw new SecurityException(
                    "Only creation of new tests is allowed: " + relativePath);
        }

        if (relativePath.contains("/acceptance/")) {
            throw new SecurityException(
                    "Acceptance-test changes require approval: " + relativePath);
        }
    }

    private boolean matches(String path, List<String> rules) {
        return rules.stream().anyMatch(rule -> path.equals(rule) || path.startsWith(rule));
    }
}
