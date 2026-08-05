package com.example.agentic.build;

import com.example.agentic.domain.CommandResult;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Service
public class MavenBuildService {
    private final CommandExecutor executor;

    public MavenBuildService(CommandExecutor e) {
        executor = e;
    }

    public CommandResult verify(Path w) {
        return executor.execute(w, List.of(maven(), "-q", "clean", "verify"), Duration.ofMinutes(5));
    }

    public CommandResult packageApp(Path w) {
        return executor.execute(w, List.of(maven(), "-q", "-DskipTests", "package"), Duration.ofMinutes(5));
    }

    private String maven() {
        return System.getProperty("os.name").toLowerCase().contains("win") ? "mvn.cmd" : "mvn";
    }
}
