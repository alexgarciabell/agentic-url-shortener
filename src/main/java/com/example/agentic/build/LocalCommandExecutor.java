package com.example.agentic.build;

import com.example.agentic.domain.CommandResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class LocalCommandExecutor implements CommandExecutor {
    public CommandResult execute(Path dir, List<String> cmd, Duration timeout) {
        try {
            ProcessBuilder b = new ProcessBuilder(cmd);
            b.directory(dir.toFile());
            b.redirectErrorStream(true);
            Process p = b.start();
            var future = CompletableFuture.supplyAsync(() -> {
                try {
                    return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    return e.toString();
                }
            });
            boolean done = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!done) {
                p.destroyForcibly();
                return new CommandResult(-1, true, future.get(5, TimeUnit.SECONDS));
            }
            return new CommandResult(p.exitValue(), false, future.get(5, TimeUnit.SECONDS));
        } catch (Exception e) {
            throw new IllegalStateException("Command failed: " + cmd, e);
        }
    }
}
