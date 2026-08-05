package com.example.agentic.domain;

public record CommandResult(int exitCode, boolean timedOut, String output) {
    public boolean successful() {
        return exitCode == 0 && !timedOut;
    }
}
