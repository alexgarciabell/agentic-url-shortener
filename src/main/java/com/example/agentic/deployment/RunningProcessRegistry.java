package com.example.agentic.deployment;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class RunningProcessRegistry {
    private final Map<UUID, Process> processes = new ConcurrentHashMap<>();

    public void register(UUID id, Process p) {
        processes.put(id, p);
    }

    public void stop(UUID id) {
        Process p = processes.remove(id);
        if (p == null) return;
        p.destroy();
        try {
            if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        }
    }
}
