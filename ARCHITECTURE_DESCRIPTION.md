# Architecture Description

## 1. Purpose

This project is a Java 21 and Spring Boot prototype of a governed, self-correcting software-engineering system. Its primary responsibility is to accept an engineering requirement, select an execution scenario, generate or modify source code in an isolated workspace, run Maven verification, repair production code when verification fails, request human approval, and deploy the approved generated application as a separate process.

The prototype supports three scenarios:

- **Greenfield** — creates a complete Maven application in a new or empty workspace.
- **Brownfield** — inspects and modifies an existing Maven project.
- **Ambiguous** — resolves an unclear requirement to a conservative interpretation and then selects a Greenfield or Brownfield execution path based on workspace evidence.

The generated target application is a URL shortener that exposes endpoints to create, redirect, inspect analytics, and delete shortened URLs.

## 2. Architectural style

The solution is a modular monolith for orchestration, combined with separately deployed generated applications.

```text
Client
  |
  v
Agentic Orchestrator JVM :8080
  |-- REST API
  |-- Scenario selection
  |-- Model integration
  |-- Workspace mutation
  |-- Maven verification and repair loop
  |-- H2 persistence and audit trail
  |-- Approval workflow
  |-- Packaging and deployment
  |
  +---- starts ----> Generated URL Shortener JVM :8090-8190
                         |-- REST API
                         |-- H2 database
                         |-- Actuator health endpoint
```

The orchestrator and generated application do not share a runtime or database. The generated application is compiled, packaged, and started with `java -jar` as a child process.

## 3. Main components

### 3.1 API layer

Package: `com.example.agentic.api`

The API layer exposes the orchestration lifecycle through `WorkflowController`.

Main endpoints:

- `POST /api/workflows` — starts a workflow.
- `GET /api/workflows/{id}` — returns workflow and deployment status.
- `GET /api/workflows/{id}/events` — returns the audit trail.
- `POST /api/workflows/{id}/approval` — approves or rejects a verified workflow.
- `GET /api/workflows/{id}/deployment` — returns deployment metadata.
- `POST /api/workflows/{id}/deployment/stop` — stops a running generated application.

`ApiExceptionHandler` converts application exceptions into API error responses.

The API layer is intentionally thin. It validates requests and delegates use cases to `WorkflowService`.

### 3.2 Application layer

Package: `com.example.agentic.application`

`WorkflowService` coordinates public application use cases:

- Starting a workflow.
- Reading workflow and event history.
- Resolving approvals.
- Triggering deployment after approval.
- Querying and stopping deployments.

The service enforces the approval gate. A workflow may be deployed only when its status is `WAITING_FOR_APPROVAL`. Rejection moves the workflow to `SAFE_STOPPED`. Approval triggers packaging, process startup, and health verification before the workflow is marked `COMPLETED`.

### 3.3 Orchestration layer

Package: `com.example.agentic.orchestration`

`WorkflowEngine` implements the central engineering lifecycle.

```text
REQUIREMENTS
    |
    v
SCENARIO DECISION
    |
    +--> GREENFIELD
    +--> BROWNFIELD
    +--> AMBIGUOUS -> GREENFIELD or BROWNFIELD
    |
    v
PLAN / REPOSITORY ANALYSIS
    |
    v
AGENT IMPLEMENTATION
    |
    v
MAVEN VERIFY
   / \
pass  fail
 |      |
 |      v
 |   ANALYZE FAILURE
 |      |
 |      v
 |    REPAIR
 |      |
 |      +------> MAVEN VERIFY
 |
 v
RELEASE REVIEW
    |
    v
WAITING FOR APPROVAL
```

The engine performs these steps:

1. Prepares the workspace.
2. Persists a new `WorkflowExecution`.
3. Records a `WORKFLOW_STARTED` audit event.
4. Invokes the selected `ScenarioStrategy`.
5. Stops a running deployment when a Brownfield workflow targets the same workspace.
6. Determines whether Greenfield bootstrap permissions are needed.
7. Sends the requirement and workspace snapshot to the configured `AgentModelClient`.
8. Applies validated file operations.
9. Runs `mvn clean verify`.
10. Repeats model-assisted repair until verification passes or the retry limit is reached.
11. Creates an approval request after successful verification.

Maven is the objective release gate. The model is not allowed to declare its own output correct.

### 3.4 Scenario strategy layer

Package: `com.example.agentic.scenario`

The project applies the Strategy pattern through `ScenarioStrategy` and `ScenarioRegistry`.

#### GreenfieldScenarioStrategy

- Accepts a new-system or new-feature requirement.
- Allows the workspace to be created when missing.
- Produces a decision to bootstrap a complete Java 21 Maven application.
- Uses `GREENFIELD_BOOTSTRAP` mutation permissions when no `pom.xml` exists.

#### BrownfieldScenarioStrategy

- Requires an existing workspace containing `pom.xml`.
- Preserves existing compatible behavior unless the requirement explicitly changes it.
- Directs the agent to inspect the project and apply the smallest scoped modification.

#### AmbiguousScenarioStrategy

- Checks whether the workspace already contains a Maven project.
- Chooses Brownfield when `pom.xml` exists; otherwise chooses Greenfield.
- Appends a conservative URL-safety interpretation to the unclear requirement.
- Avoids destructive, security-sensitive, or public-contract changes.

The current ambiguous strategy is deterministic. It demonstrates controlled decision-making but does not implement a conversational clarification loop.

### 3.5 Model integration layer

Package: `com.example.agentic.agent`

`AgentModelClient` abstracts the model provider.

Two implementations are available:

#### DemoAgentModelClient

- Enabled when `agent.model.mode=demo`.
- Generates a deterministic complete URL-shortener project for Greenfield bootstrap.
- Performs no repository-specific Brownfield modifications.
- Allows the orchestration lifecycle to be demonstrated without an API key.

#### OpenAiCompatibleAgentModelClient

- Enabled when `agent.model.mode=openai`.
- Uses an OpenAI-compatible API, configured by default for Groq.
- Sends structured prompts and expects an `AgentResponse` containing:
  - summary
  - rationale
  - assumptions
  - file operations
- Extracts the final `output_text` from Responses API output, ignoring reasoning items.
- Validates provider configuration and preserves provider HTTP error details.

`PromptFactory` produces implementation and repair prompts. The prompts instruct the model to minimize operations, preserve protected files, and return complete file contents for every write.

### 3.6 Workspace and mutation control

Package: `com.example.agentic.workspace`

`WorkspaceService` provides:

- Workspace creation and validation.
- Repository snapshot generation for model context.
- Application of `WRITE` and `DELETE` file operations.
- Best-effort Git diff generation through JGit.

`MutationPolicy` is the primary code-change guardrail.

Two mutation phases are supported:

#### GREENFIELD_BOOTSTRAP

May create:

- `pom.xml`
- `README.md`
- `src/main/**`
- `src/test/**`

#### IMPLEMENTATION_REPAIR

May:

- Modify files under `src/main/**`.
- Create new tests under `src/test/**`.
- Update `README.md`.

May not automatically:

- Modify or delete an existing test.
- Modify `pom.xml`.
- Modify `.git/**` or `.github/**`.
- Modify Maven wrappers.
- Modify `target/**`.
- Escape the workspace through path traversal.

This distinction prevents the agent from weakening existing tests simply to make verification pass.

### 3.7 Build execution

Package: `com.example.agentic.build`

`LocalCommandExecutor` starts external commands with `ProcessBuilder`, captures merged standard output and error output, and enforces a timeout.

`MavenBuildService` resolves the operating-system-specific Maven executable and runs the Maven lifecycle in the target workspace.

The main verification command is:

```text
mvn clean verify
```

The build result is represented by `CommandResult`, which contains:

- exit code
- timeout state
- captured output

A failed build is passed back to the model as repair evidence.

### 3.8 Deployment layer

Package: `com.example.agentic.deployment`

`DeploymentService` handles the post-approval lifecycle:

1. Packages the verified target application.
2. Locates an executable JAR inside the workspace `target` directory.
3. Allocates an available port through `PortAllocator`.
4. Starts the JAR as a child process.
5. Redirects process output to a runtime log.
6. Polls `/actuator/health` until it reports healthy or the configured timeout expires.
7. Persists deployment metadata.
8. Records deployment events.

`RunningProcessRegistry` stores live Java `Process` references in memory. H2 stores metadata, not live process handles.

Before Brownfield mutation, `WorkflowEngine` calls `stopRunningForWorkspace`. This is necessary on Windows because a running JAR may lock the file and cause `mvn clean` to fail.

### 3.9 Persistence and audit

Package: `com.example.agentic.persistence`

The orchestrator uses file-backed H2:

```text
jdbc:h2:file:./data/agentic-db
```

Spring Data JPA repositories persist:

- `WorkflowExecution`
- `AuditEvent`
- `ApprovalRequest`
- `ApplicationDeployment`

#### WorkflowExecution

Stores:

- workflow identifier
- requested scenario
- requirement
- workspace path
- current workflow status
- current node
- repair-attempt count
- start and completion timestamps
- decision summary

#### AuditEvent

Provides an ordered execution history. Typical event types include:

- `WORKFLOW_STARTED`
- `AGENT_IMPLEMENTATION`
- `MAVEN_VERIFY`
- `AGENT_REPAIR`
- `SAFE_STOP`
- `APPROVAL_REQUESTED`
- `APPROVAL_APPROVED`
- `APPROVAL_REJECTED`
- deployment events

#### ApprovalRequest

Persists the pending and resolved human decision, including comments and timestamps.

#### ApplicationDeployment

Stores deployment status, workspace, JAR path, base URL, port, process ID, log path, timestamps, and failure information.

This design preserves workflow lineage and supports review through the events API.

## 4. Workflow state model

The domain package defines explicit enums for state and control flow.

### WorkflowStatus

Represents lifecycle status such as:

- `CREATED`
- `RUNNING`
- `WAITING_FOR_APPROVAL`
- `COMPLETED`
- `FAILED`
- `SAFE_STOPPED`

### NodeType

Represents the active orchestration stage, including requirements, planning, repository analysis, verification, failure analysis, repair, release review, approval, packaging, and completion.

### DeploymentStatus

Represents generated-application deployment states, such as packaging, starting, health checking, running, failed, and stopped.

### MutationPhase

Separates initial Greenfield project creation from constrained implementation repair.

Explicit enums make execution state persistable, observable, and easier to defend than implicit method sequencing alone.

## 5. Greenfield execution

A Greenfield request may point to a missing or empty workspace.

```text
POST /api/workflows
  -> WorkspaceService creates directory
  -> GreenfieldScenarioStrategy normalizes requirement
  -> Demo or real model generates Maven project
  -> MutationPolicy allows bootstrap files
  -> WorkspaceService writes project
  -> MavenBuildService runs clean verify
  -> repair loop runs when necessary
  -> workflow waits for approval
  -> approval triggers packaging and startup
```

In demo mode, `DemoProjectTemplate` generates a complete Spring Boot URL shortener with:

- H2 persistence
- URL creation
- HTTP redirect
- click analytics
- URL expiration
- URL deletion
- validation for HTTP and HTTPS targets
- Actuator health endpoint
- unit and integration tests

## 6. Brownfield execution

A Brownfield request must point to an existing Maven project.

```text
POST /api/workflows
  -> validate pom.xml exists
  -> stop deployment for same workspace
  -> snapshot relevant Java and Maven files
  -> model proposes scoped operations
  -> protect existing tests and build definition
  -> write production changes and new regression tests
  -> run clean verify
  -> repair production code within bounded attempts
  -> request approval
```

The current demo model intentionally returns no Brownfield modifications. Repository-specific Brownfield work requires the OpenAI-compatible model mode.

## 7. Ambiguous execution

An Ambiguous request is routed through `AmbiguousScenarioStrategy`.

The strategy currently makes two decisions:

1. Select Greenfield or Brownfield based on the existence of `pom.xml`.
2. Resolve the unclear requirement to a low-risk, reversible URL-safety improvement.

The resulting `ScenarioDecision` records the selected execution type, normalized requirement, assumptions, and rationale. The rest of the workflow reuses the normal Greenfield or Brownfield path.

## 8. Approval and controlled autonomy

The system is autonomous only up to the verified release candidate.

It may automatically:

- analyze a requirement
- generate or modify code
- create new regression tests
- run Maven
- analyze failures
- repair production code
- repeat within bounded limits

It may not automatically deploy the result. Deployment requires an explicit approval request through the API.

Rejection moves the workflow to a safe stop. Approval starts packaging and health-checked deployment.

## 9. Reliability and safety controls

The prototype includes these controls:

- Maximum repair-attempt limit, defaulting to three.
- Maven verification as an independent gate.
- Workspace path normalization and traversal prevention.
- Protected existing tests.
- Protected build, CI, Git, wrapper, and target paths.
- Process execution timeout.
- Deployment startup timeout.
- Available-port selection.
- Health verification before completion.
- Automatic process cleanup after failed startup.
- Automatic stop before Brownfield mutation of a running workspace.
- Audit events for success and failure transitions.
- Human approval before deployment.

## 10. Configuration

The main configuration is in `src/main/resources/application.yml`.

### Orchestrator

- Port: `8080`
- H2 console: enabled
- H2 storage: `./data/agentic-db`
- Actuator endpoints: health, info, metrics

### Agent

- Maximum repair attempts: `3`
- Default model mode: `demo`
- Default Groq-compatible base URL: `https://api.groq.com/openai/v1`
- Default model: `openai/gpt-oss-120b`

Environment variables:

- `AGENT_MODEL_MODE`
- `AGENT_MODEL_API_KEY`
- `AGENT_MODEL_BASE_URL`
- `AGENT_MODEL_NAME`

### Deployment

- Port range: `8090` through `8190`
- Startup timeout: `30s`

## 11. Generated URL-shortener API

After verification and approval, the generated application exposes:

### Create a short URL

```http
POST /api/urls
```

### Redirect

```http
GET /{shortCode}
```

### View analytics

```http
GET /api/urls/{shortCode}/analytics
```

### Delete a short URL

```http
DELETE /api/urls/{shortCode}
```

Expected redirect behavior:

- Existing active URL: HTTP redirect.
- Unknown URL: `404 Not Found`.
- Expired URL: `410 Gone`.

Successful redirects increment click analytics. Expired or missing URL accesses do not count as successful redirects.

## 12. Key design decisions

### Single orchestrator JVM

The prototype avoids distributed workflow infrastructure such as Temporal, Camunda, Kafka, and Kubernetes. This keeps setup simple and allows reviewers to inspect the orchestration logic directly.

### H2 instead of an external database

H2 provides durable local workflow state without adding infrastructure. It is appropriate for a self-contained interview prototype.

### Model abstraction

The core workflow depends on `AgentModelClient`, not a specific provider. Demo and OpenAI-compatible implementations can be selected by configuration.

### ProcessBuilder for builds and deployment

External Maven and Java processes remain explicit and observable. The generated application is isolated from the orchestrator JVM.

### Maven as the source of truth

The model proposes code, but Maven compile and test results determine success.

### Immutable operation payloads

Records such as `AgentResponse`, `FileOperation`, `ScenarioDecision`, and `CommandResult` communicate structured data across layers.

### Test integrity over unrestricted repair

The agent can create a regression test but cannot rewrite an existing test during repair. This prevents false success through test weakening.

## 13. Current limitations

The project is intentionally a prototype. Current limitations include:

- Child-process references are in memory and are not reattached after orchestrator restart.
- H2 uses automatic Hibernate schema updates rather than versioned migrations.
- Ambiguous resolution is deterministic and does not pause for interactive clarification.
- The orchestration graph is encoded in the workflow engine rather than stored as a fully configurable DAG.
- Parallel agent execution is not implemented.
- Demo mode cannot perform repository-specific Brownfield changes.
- Workspace snapshots are simple file-content collections and not full Java semantic analysis.
- Strict structured model output may require provider-specific retries or fallback handling for large code-generation responses.
- Deployment is local only and does not implement production-grade sandboxing.
- The project assumes Maven and Java are available on the host.

## 14. Production evolution path

A production version could add:

- Containerized build and runtime isolation.
- Explicit configurable dependency graphs.
- Parallel nodes with synchronization barriers.
- Durable workflow execution through Temporal or an equivalent engine.
- Versioned database migrations.
- Semantic Java analysis using JavaParser.
- Richer policy decisions with approval-required mutations.
- Artifact-by-artifact model generation to reduce structured-output failures.
- Model-call retry and fallback policies.
- Authentication and authorization for workflow and approval APIs.
- Persistent process supervision or an external deployment platform.
- Prometheus and distributed tracing exporters.
- Repository branches, commits, and pull-request generation.

## 15. Summary

The architecture separates reasoning, orchestration, workspace mutation, objective verification, governance, persistence, and deployment. One Spring Boot orchestrator manages three scenario strategies and produces independently runnable URL-shortener applications. The model has controlled authority to generate and repair code, while mutation policies, Maven verification, bounded retries, audit events, and human approval keep that autonomy governed and reviewable.
