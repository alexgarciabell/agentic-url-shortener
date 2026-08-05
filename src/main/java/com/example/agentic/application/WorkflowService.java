package com.example.agentic.application;

import com.example.agentic.deployment.DeploymentService;
import com.example.agentic.domain.ApprovalDecision;
import com.example.agentic.domain.NodeType;
import com.example.agentic.domain.WorkflowRequest;
import com.example.agentic.domain.WorkflowStatus;
import com.example.agentic.orchestration.AuditService;
import com.example.agentic.orchestration.WorkflowEngine;
import com.example.agentic.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkflowService {
    private final WorkflowEngine engine;
    private final WorkflowRepository workflows;
    private final AuditEventRepository events;
    private final ApprovalRepository approvals;
    private final DeploymentService deployments;
    private final AuditService audit;

    public WorkflowService(WorkflowEngine e, WorkflowRepository w, AuditEventRepository ev, ApprovalRepository a, DeploymentService d, AuditService audit) {
        engine = e;
        workflows = w;
        events = ev;
        approvals = a;
        deployments = d;
        this.audit = audit;
    }

    public WorkflowExecution start(WorkflowRequest r) {
        return engine.start(r);
    }

    public WorkflowExecution get(UUID id) {
        return workflows.findById(id).orElseThrow();
    }

    public List<AuditEvent> events(UUID id) {
        return events.findByWorkflowIdOrderByCreatedAt(id);
    }

    @Transactional
    public WorkflowExecution approve(UUID id, ApprovalDecision decision, String comment) {
        WorkflowExecution run = get(id);
        if (run.getStatus() != WorkflowStatus.WAITING_FOR_APPROVAL) throw new IllegalStateException("Workflow is not waiting for approval");
        ApprovalRequest req = approvals.findTopByWorkflowIdOrderByRequestedAtDesc(id).orElseThrow();
        req.resolve(decision, comment);
        approvals.save(req);
        run.currentNode(NodeType.APPROVAL);
        if (decision == ApprovalDecision.REJECTED) {
            run.status(WorkflowStatus.SAFE_STOPPED);
            audit.record(id, "APPROVAL_REJECTED", comment);
            return workflows.save(run);
        }
        try {
            run.status(WorkflowStatus.RUNNING);
            run.currentNode(NodeType.PACKAGE);
            workflows.save(run);
            deployments.deploy(run);
            run.currentNode(NodeType.COMPLETED);
            run.status(WorkflowStatus.COMPLETED);
            audit.record(id, "APPROVAL_APPROVED", comment);
            return workflows.save(run);
        } catch (RuntimeException e) {
            run.status(WorkflowStatus.SAFE_STOPPED);
            workflows.save(run);
            audit.record(id, "DEPLOYMENT_FAILED", e.toString());
            throw e;
        }
    }

    public Optional<ApplicationDeployment> deployment(UUID id) {
        return Optional.ofNullable(deployments.get(id));
    }

    public ApplicationDeployment stop(UUID id) {
        return deployments.stop(id);
    }
}
