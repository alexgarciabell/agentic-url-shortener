package com.example.agentic.api;

import com.example.agentic.domain.DeploymentStatus;
import com.example.agentic.domain.NodeType;
import com.example.agentic.domain.ScenarioType;
import com.example.agentic.domain.WorkflowStatus;
import com.example.agentic.persistence.ApplicationDeployment;
import com.example.agentic.persistence.WorkflowExecution;

import java.time.Instant;
import java.util.UUID;

public record WorkflowView(UUID id, ScenarioType scenarioType, WorkflowStatus status, NodeType currentNode, int repairAttempts,
                           String decisionSummary, Instant startedAt, Instant completedAt, DeploymentView deployment) {
    public static WorkflowView from(WorkflowExecution w, ApplicationDeployment d) {
        return new WorkflowView(w.getId(), w.getScenarioType(), w.getStatus(), w.getCurrentNode(), w.getRepairAttempts(), w.getDecisionSummary(), w.getStartedAt(), w.getCompletedAt(), d == null ? null : DeploymentView.from(d));
    }

    public record DeploymentView(DeploymentStatus status, String baseUrl, Integer port, String healthUrl, String createEndpoint,
                                 String redirectTemplate, String analyticsTemplate, String deleteTemplate, String failureReason) {
        static DeploymentView from(ApplicationDeployment d) {
            String b = d.getBaseUrl();
            return new DeploymentView(d.getStatus(), b, d.getPort(), b == null ? null : b + "/actuator/health", b == null ? null : "POST " + b + "/api/urls", b == null ? null : "GET " + b + "/{shortCode}", b == null ? null : "GET " + b + "/api/urls/{shortCode}/analytics", b == null ? null : "DELETE " + b + "/api/urls/{shortCode}", d.getFailureReason());
        }
    }
}
