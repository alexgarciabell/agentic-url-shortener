package com.example.agentic.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, NoSuchElementException.class, SecurityException.class})
    ResponseEntity<Map<String, Object>> handle(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getClass().getSimpleName(), "message", String.valueOf(e.getMessage())));
    }
}
