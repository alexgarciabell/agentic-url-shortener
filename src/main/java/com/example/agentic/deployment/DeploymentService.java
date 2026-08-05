package com.example.agentic.deployment;

import com.example.agentic.build.MavenBuildService;
import com.example.agentic.config.AgentProperties;
import com.example.agentic.domain.CommandResult;
import com.example.agentic.domain.DeploymentStatus;
import com.example.agentic.orchestration.AuditService;
import com.example.agentic.persistence.ApplicationDeployment;
import com.example.agentic.persistence.ApplicationDeploymentRepository;
import com.example.agentic.persistence.WorkflowExecution;
import com.example.agentic.persistence.WorkflowRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class DeploymentService {

    private final MavenBuildService maven;
    private final PortAllocator ports;
    private final RunningProcessRegistry processRegistry;
    private final ApplicationDeploymentRepository deployments;
    private final WorkflowRepository workflows;
    private final AuditService audit;
    private final AgentProperties properties;
    private final RestClient http = RestClient.create();

    public DeploymentService(
            MavenBuildService maven,
            PortAllocator ports,
            RunningProcessRegistry processRegistry,
            ApplicationDeploymentRepository deployments,
            WorkflowRepository workflows,
            AuditService audit,
            AgentProperties properties) {
        this.maven = maven;
        this.ports = ports;
        this.processRegistry = processRegistry;
        this.deployments = deployments;
        this.workflows = workflows;
        this.audit = audit;
        this.properties = properties;
    }

    public ApplicationDeployment deploy(WorkflowExecution workflow) {
        Path workspace = Path.of(workflow.getWorkspacePath()).toAbsolutePath().normalize();
        ApplicationDeployment deployment = deployments.save(
                new ApplicationDeployment(workflow.getId()));

        audit.record(workflow.getId(), "PACKAGE_STARTED", workspace.toString());
        CommandResult packaged = maven.packageApp(workspace);
        if (!packaged.successful()) {
            deployment.failed(packaged.output());
            deployments.save(deployment);
            throw new IllegalStateException("Packaging failed: " + packaged.output());
        }

        Path jar = findJar(workspace);
        deployment.packaged(jar.toString());
        deployments.save(deployment);

        int port = ports.allocate();
        Path log = workspace.resolve("runtime.log");
        deployment.starting(port, log.toString());
        deployments.save(deployment);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    javaExecutable(),
                    "-jar",
                    jar.toString(),
                    "--server.port=" + port);
            processBuilder.directory(workspace.toFile());
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(log.toFile());

            Process process = processBuilder.start();
            processRegistry.register(workflow.getId(), process);

            String baseUrl = "http://localhost:" + port;
            deployment.healthChecking(process.pid(), baseUrl);
            deployments.save(deployment);
            audit.record(workflow.getId(), "APPLICATION_STARTED", baseUrl);

            Instant deadline = Instant.now().plus(
                    properties.getDeployment().getStartupTimeout());
            while (Instant.now().isBefore(deadline)) {
                if (!process.isAlive()) {
                    break;
                }
                try {
                    String body = http.get()
                            .uri(baseUrl + "/actuator/health")
                            .retrieve()
                            .body(String.class);
                    if (body != null && body.contains("UP")) {
                        deployment.running();
                        deployments.save(deployment);
                        audit.record(workflow.getId(), "HEALTH_CHECK_PASSED", baseUrl);
                        return deployment;
                    }
                } catch (RuntimeException ignored) {
                    // The generated process may still be starting.
                }
                sleepBriefly();
            }

            processRegistry.stop(workflow.getId());
            deployment.failed("Application did not become healthy");
            deployments.save(deployment);
            throw new IllegalStateException("Generated application failed health check");
        } catch (Exception exception) {
            processRegistry.stop(workflow.getId());
            deployment.failed(exception.getMessage());
            deployments.save(deployment);
            throw new IllegalStateException(
                    "Unable to start generated application", exception);
        }
    }

    public void stopRunningForWorkspace(Path workspace) {
        Path normalized = workspace.toAbsolutePath().normalize();
        for (ApplicationDeployment deployment
                : deployments.findByStatus(DeploymentStatus.RUNNING)) {
            workflows.findById(deployment.getWorkflowId())
                    .filter(workflow -> Path.of(workflow.getWorkspacePath())
                            .toAbsolutePath()
                            .normalize()
                            .equals(normalized))
                    .ifPresent(workflow -> {
                        processRegistry.stop(workflow.getId());
                        deployment.stopped();
                        deployments.save(deployment);
                        audit.record(
                                workflow.getId(),
                                "DEPLOYMENT_STOPPED_FOR_MUTATION",
                                normalized.toString());
                    });
        }
    }

    public ApplicationDeployment get(UUID workflowId) {
        return deployments.findTopByWorkflowIdOrderByIdDesc(workflowId)
                .orElseThrow();
    }

    public ApplicationDeployment stop(UUID workflowId) {
        ApplicationDeployment deployment = get(workflowId);
        processRegistry.stop(workflowId);
        deployment.stopped();
        audit.record(
                workflowId,
                "APPLICATION_STOPPED",
                String.valueOf(deployment.getBaseUrl()));
        return deployments.save(deployment);
    }

    private Path findJar(Path workspace) {
        Path normalizedWorkspace = workspace.toAbsolutePath().normalize();
        Path target = normalizedWorkspace.resolve("target").normalize();
        if (!target.startsWith(normalizedWorkspace)) {
            throw new SecurityException("Invalid target directory");
        }

        try (Stream<Path> paths = Files.list(target)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> !path.getFileName().toString().startsWith("original-"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No executable JAR found in " + target));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot locate executable JAR", exception);
        }
    }

    private String javaExecutable() {
        boolean windows = System.getProperty("os.name")
                .toLowerCase()
                .contains("win");
        return Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        windows ? "java.exe" : "java")
                .toString();
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Health check interrupted", exception);
        }
    }
}
