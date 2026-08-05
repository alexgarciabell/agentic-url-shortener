package com.example.agentic.workspace;

import com.example.agentic.domain.FileOperation;
import com.example.agentic.domain.MutationPhase;
import com.example.agentic.domain.ScenarioType;
import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

@Service
public class WorkspaceService {

    private final MutationPolicy mutationPolicy;

    public WorkspaceService(MutationPolicy mutationPolicy) {
        this.mutationPolicy = mutationPolicy;
    }

    public Path prepare(String workspacePath, ScenarioType scenarioType) {
        Path workspace = Path.of(workspacePath).toAbsolutePath().normalize();
        try {
            if (scenarioType == ScenarioType.GREENFIELD
                    || scenarioType == ScenarioType.AMBIGUOUS) {
                Files.createDirectories(workspace);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create workspace", exception);
        }

        if (!Files.isDirectory(workspace)) {
            throw new IllegalArgumentException("Workspace does not exist: " + workspace);
        }
        return workspace;
    }

    public void apply(
            Path workspace,
            List<FileOperation> operations,
            MutationPhase phase) {

        for (FileOperation operation : operations) {
            mutationPolicy.assertAllowed(workspace, operation, phase);
            applyOperation(workspace, operation);
        }
    }

    private void applyOperation(Path workspace, FileOperation operation) {
        Path target = workspace.resolve(operation.path()).normalize();
        try {
            if (operation.operation() == FileOperation.Operation.DELETE) {
                Files.deleteIfExists(target);
                return;
            }
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(
                    target,
                    operation.content(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot apply " + operation.path(), exception);
        }
    }

    public String snapshot(Path workspace) {
        StringBuilder builder = new StringBuilder();
        try (var paths = Files.walk(workspace)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains("target"))
                    .filter(path -> path.toString().endsWith(".java")
                            || path.getFileName().toString().equals("pom.xml"))
                    .limit(100)
                    .forEach(path -> {
                        try {
                            builder.append("\n--- ")
                                    .append(workspace.relativize(path))
                                    .append(" ---\n")
                                    .append(Files.readString(path));
                        } catch (Exception ignored) {
                            // Best-effort snapshot for model context.
                        }
                    });
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot inspect workspace", exception);
        }
        return builder.toString();
    }

    public String diff(Path workspace) {
        try (Git git = Git.open(workspace.toFile())) {
            return git.diff().call().toString();
        } catch (Exception exception) {
            return "Git diff unavailable: " + exception.getMessage();
        }
    }
}
