package com.example.agentic.orchestration;

import com.example.agentic.agent.AgentModelClient;
import com.example.agentic.build.MavenBuildService;
import com.example.agentic.config.AgentProperties;
import com.example.agentic.deployment.DeploymentService;
import com.example.agentic.domain.*;
import com.example.agentic.persistence.ApprovalRepository;
import com.example.agentic.persistence.ApprovalRequest;
import com.example.agentic.persistence.WorkflowExecution;
import com.example.agentic.persistence.WorkflowRepository;
import com.example.agentic.scenario.ScenarioDecision;
import com.example.agentic.scenario.ScenarioRegistry;
import com.example.agentic.workspace.WorkspaceService;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class WorkflowEngine {

    private final ScenarioRegistry scenarioRegistry;
    private final WorkspaceService workspaces;
    private final AgentModelClient model;
    private final PromptFactory prompts;
    private final MavenBuildService maven;
    private final WorkflowRepository workflows;
    private final ApprovalRepository approvals;
    private final AuditService audit;
    private final AgentProperties properties;
    private final DeploymentService deployments;

    public WorkflowEngine(
            ScenarioRegistry scenarioRegistry,
            WorkspaceService workspaces,
            AgentModelClient model,
            PromptFactory prompts,
            MavenBuildService maven,
            WorkflowRepository workflows,
            ApprovalRepository approvals,
            AuditService audit,
            AgentProperties properties,
            DeploymentService deployments) {
        this.scenarioRegistry = scenarioRegistry;
        this.workspaces = workspaces;
        this.model = model;
        this.prompts = prompts;
        this.maven = maven;
        this.workflows = workflows;
        this.approvals = approvals;
        this.audit = audit;
        this.properties = properties;
        this.deployments = deployments;
    }

    public WorkflowExecution start(WorkflowRequest request) {
        Path workspace = workspaces.prepare(
                request.workspacePath(), request.scenarioType());
        WorkflowExecution workflow = new WorkflowExecution(
                UUID.randomUUID(),
                request.scenarioType(),
                request.requirement(),
                workspace.toString());
        workflows.save(workflow);

        try {
            workflow.status(WorkflowStatus.RUNNING);
            workflow.currentNode(NodeType.REQUIREMENTS);
            workflows.save(workflow);
            audit.record(
                    workflow.getId(), "WORKFLOW_STARTED", request.requirement());

            ScenarioDecision decision = scenarioRegistry
                    .get(request.scenarioType())
                    .prepare(request.requirement(), workspace);
            workflow.decisionSummary(
                    decision.rationale() + " Assumptions: " + decision.assumptions());
            workflows.save(workflow);

            if (decision.executionType() == ScenarioType.BROWNFIELD) {
                deployments.stopRunningForWorkspace(workspace);
            }

            boolean bootstrap = decision.executionType() == ScenarioType.GREENFIELD
                    && !Files.exists(workspace.resolve("pom.xml"));

            workflow.currentNode(
                    decision.executionType() == ScenarioType.BROWNFIELD
                            ? NodeType.REPOSITORY_ANALYSIS
                            : NodeType.PLAN);
            workflows.save(workflow);

            AgentResponse response = model.execute(
                    prompts.system(),
                    prompts.implement(
                            decision,
                            workspaces.snapshot(workspace),
                            bootstrap));

            workspaces.apply(
                    workspace,
                    response.operations(),
                    bootstrap
                            ? MutationPhase.GREENFIELD_BOOTSTRAP
                            : MutationPhase.IMPLEMENTATION_REPAIR);
            audit.record(
                    workflow.getId(),
                    "AGENT_IMPLEMENTATION",
                    response.summary() + " - " + response.rationale());

            if (!verifyAndRepair(workflow, decision, workspace)) {
                return workflow;
            }

            workflow.currentNode(NodeType.RELEASE_REVIEW);
            workflow.status(WorkflowStatus.WAITING_FOR_APPROVAL);
            workflows.save(workflow);
            approvals.save(new ApprovalRequest(workflow.getId()));
            audit.record(
                    workflow.getId(),
                    "APPROVAL_REQUESTED",
                    "Verification passed; review required before deployment.");
            return workflow;
        } catch (RuntimeException exception) {
            workflow.status(WorkflowStatus.FAILED);
            workflows.save(workflow);
            audit.record(workflow.getId(), "WORKFLOW_FAILED", exception.toString());
            throw exception;
        }
    }

    private boolean verifyAndRepair(
            WorkflowExecution workflow,
            ScenarioDecision decision,
            Path workspace) {

        for (int attempt = 0; ; attempt++) {
            workflow.currentNode(NodeType.VERIFY);
            workflows.save(workflow);

            CommandResult result = maven.verify(workspace);
            audit.record(
                    workflow.getId(),
                    "MAVEN_VERIFY",
                    "exit=" + result.exitCode()
                            + " timedOut=" + result.timedOut()
                            + "\n" + result.output());

            if (result.successful()) {
                return true;
            }

            if (attempt >= properties.getMaxRepairAttempts() - 1) {
                workflow.status(WorkflowStatus.SAFE_STOPPED);
                workflows.save(workflow);
                audit.record(
                        workflow.getId(),
                        "SAFE_STOP",
                        "Maximum repair attempts reached");
                return false;
            }

            workflow.incrementRepairAttempts();
            workflow.currentNode(NodeType.ANALYZE_FAILURE);
            workflows.save(workflow);

            AgentResponse repair = model.execute(
                    prompts.system(),
                    prompts.repair(
                            decision,
                            workspaces.snapshot(workspace),
                            result.output(),
                            attempt + 1));

            workflow.currentNode(NodeType.REPAIR);
            workflows.save(workflow);
            workspaces.apply(
                    workspace,
                    repair.operations(),
                    MutationPhase.IMPLEMENTATION_REPAIR);
            audit.record(workflow.getId(), "AGENT_REPAIR", repair.summary());
        }
    }

}
