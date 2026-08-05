package com.example.agentic.build;

import com.example.agentic.domain.CommandResult;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public interface CommandExecutor {
    CommandResult execute(Path directory, List<String> command, Duration timeout);
}
