package com.example.agentic.persistence;

import com.example.agentic.domain.ApprovalDecision;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_request")
public class ApprovalRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID workflowId;
    private String status;
    private Instant requestedAt;
    private Instant resolvedAt;
    @Enumerated(EnumType.STRING)
    private ApprovalDecision decision;
    private String comment;

    protected ApprovalRequest() {
    }

    public ApprovalRequest(UUID w) {
        workflowId = w;
        status = "PENDING";
        requestedAt = Instant.now();
    }

    public void resolve(ApprovalDecision d, String c) {
        decision = d;
        comment = c;
        status = "RESOLVED";
        resolvedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getWorkflowId() {
        return workflowId;
    }
}
