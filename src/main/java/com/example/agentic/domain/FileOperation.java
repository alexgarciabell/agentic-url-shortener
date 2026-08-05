package com.example.agentic.domain;

public record FileOperation(String path, String content, Operation operation) {
    public enum Operation {WRITE, DELETE}
}
