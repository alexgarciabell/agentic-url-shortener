package com.example.agentic.persistence;

import com.example.agentic.domain.NodeType;
import com.example.agentic.domain.ScenarioType;
import com.example.agentic.domain.WorkflowStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_execution")
public class WorkflowExecution {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    private ScenarioType scenarioType;
    @Lob
    private String requirement;
    private String workspacePath;
    @Enumerated(EnumType.STRING)
    private WorkflowStatus status;
    @Enumerated(EnumType.STRING)
    private NodeType currentNode;
    private int repairAttempts;
    private Instant startedAt;
    private Instant completedAt;
    @Lob
    private String decisionSummary;

    protected WorkflowExecution() {
    }

    public WorkflowExecution(UUID id, ScenarioType type, String req, String path) {
        this.id = id;
        scenarioType = type;
        requirement = req;
        workspacePath = path;
        status = WorkflowStatus.CREATED;
        startedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ScenarioType getScenarioType() {
        return scenarioType;
    }

    public String getRequirement() {
        return requirement;
    }

    public String getWorkspacePath() {
        return workspacePath;
    }

    public WorkflowStatus getStatus() {
        return status;
    }

    public NodeType getCurrentNode() {
        return currentNode;
    }

    public int getRepairAttempts() {
        return repairAttempts;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getDecisionSummary() {
        return decisionSummary;
    }

    public void status(WorkflowStatus s) {
        status = s;
        if (s == WorkflowStatus.COMPLETED || s == WorkflowStatus.FAILED || s == WorkflowStatus.SAFE_STOPPED) completedAt = Instant.now();
    }

    public void currentNode(NodeType n) {
        currentNode = n;
    }

    public void incrementRepairAttempts() {
        repairAttempts++;
    }

    public void decisionSummary(String s) {
        decisionSummary = s;
    }
}
