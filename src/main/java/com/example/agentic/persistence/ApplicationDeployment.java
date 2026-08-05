package com.example.agentic.persistence;

import com.example.agentic.domain.DeploymentStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "application_deployment")
public class ApplicationDeployment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID workflowId;
    @Enumerated(EnumType.STRING)
    private DeploymentStatus status;
    private Integer port;
    private String baseUrl;
    private Long processId;
    private String jarPath;
    private String logPath;
    private Instant startedAt;
    private Instant stoppedAt;
    @Lob
    private String failureReason;

    protected ApplicationDeployment() {
    }

    public ApplicationDeployment(UUID w) {
        workflowId = w;
        status = DeploymentStatus.PACKAGING;
    }

    public Long getId() {
        return id;
    }

    public UUID getWorkflowId() {
        return workflowId;
    }

    public DeploymentStatus getStatus() {
        return status;
    }

    public Integer getPort() {
        return port;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Long getProcessId() {
        return processId;
    }

    public String getJarPath() {
        return jarPath;
    }

    public String getLogPath() {
        return logPath;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void packaged(String jar) {
        jarPath = jar;
        status = DeploymentStatus.STARTING;
    }

    public void starting(int p, String log) {
        port = p;
        logPath = log;
        status = DeploymentStatus.STARTING;
    }

    public void healthChecking(long pid, String url) {
        processId = pid;
        baseUrl = url;
        status = DeploymentStatus.HEALTH_CHECKING;
        startedAt = Instant.now();
    }

    public void running() {
        status = DeploymentStatus.RUNNING;
    }

    public void failed(String reason) {
        status = DeploymentStatus.FAILED;
        failureReason = reason;
        stoppedAt = Instant.now();
    }

    public void stopped() {
        status = DeploymentStatus.STOPPED;
        stoppedAt = Instant.now();
    }
}
