package com.example.agentic.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID workflowId;
    private String eventType;
    @Lob
    private String message;
    private Instant createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(UUID w, String t, String m) {
        workflowId = w;
        eventType = t;
        message = m;
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getWorkflowId() {
        return workflowId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
