# Testing Approach, Limitations, and Trade-offs

## 1. Purpose

This document explains how the Agentic URL Shortener prototype is tested, what the current test suite validates, which risks remain, and which design compromises were intentionally made to keep the solution simple enough for a short interview assignment while still demonstrating professional engineering judgment.

The project is not only a URL-shortener implementation. It is an orchestration system that can generate or modify another Maven project, run that project's tests, repair production code, request human approval, and start the approved application as a separate process. Testing therefore covers two distinct systems:

1. **The orchestrator** — scenario selection, mutation controls, workflow state, model integration, Maven execution, approval, deployment, and auditing.
2. **The generated target application** — URL creation, redirection, analytics, expiration, deletion, persistence, and health reporting.

The central quality principle is that the language model does not decide whether its own work is correct. Maven verification and explicit policy checks are the objective release gates.

## 2. Testing objectives

The testing approach is designed to prove the following behaviors:

- Each of the three scenarios is selected and prepared correctly.
- Greenfield execution can create a complete Maven project in an empty workspace.
- Brownfield execution protects existing tests while allowing scoped production changes and new regression tests.
- Ambiguous requirements are resolved conservatively and routed to Greenfield or Brownfield based on workspace evidence.
- File operations cannot escape the configured workspace.
- Protected paths cannot be overwritten during repair.
- The generated project is compiled and tested with `mvn clean verify`.
- Failed verification results are returned to the model for bounded repair.
- A workflow cannot be deployed before verification and human approval.
- An approved target is packaged, started as a separate process, and health-checked.
- Audit events provide a reviewable execution history.

## 3. Test layers

### 3.1 Unit tests

Unit tests validate small deterministic rules without starting the full Spring application or external processes.

The current repository includes the following focused unit tests.

#### `AmbiguousScenarioStrategyTest`

This test verifies that the ambiguous strategy selects:

- `GREENFIELD` when the workspace is empty.
- `BROWNFIELD` when the workspace contains a `pom.xml`.

This confirms the current deterministic interpretation rule used by the prototype. It does not validate a conversational clarification process or model-based ambiguity scoring.

#### `DemoAgentModelClientTest`

This test verifies that deterministic Greenfield generation includes at least:

- A `pom.xml` operation.
- A test source operation under `src/test/**`.

This acts as a regression check for the earlier failure in which Greenfield execution reached Maven without creating a valid Maven project.

#### `MutationPolicyTest`

This test verifies the most important mutation guardrails:

- `pom.xml` is allowed during `GREENFIELD_BOOTSTRAP`.
- A new regression-test file may be created during `IMPLEMENTATION_REPAIR`.
- An existing test cannot be overwritten during repair.
- Parent-directory traversal such as `../outside.txt` is rejected.

These tests protect the integrity of the verification process. Without them, an autonomous repair could weaken or replace tests merely to manufacture a passing build.

### 3.2 Maven lifecycle verification

The orchestrator and every generated target use Maven as the deterministic build gate.

The primary command is:

```text
mvn clean verify
```

For the orchestrator, this command currently performs:

- Java compilation.
- Test compilation.
- JUnit execution through Maven Surefire.
- Spring Boot packaging.

For a generated Greenfield target, the same command verifies that the generated project is structurally complete and that its tests pass independently of the orchestrator.

A workflow may proceed to approval only when Maven returns exit code `0` and the mutation policy has accepted all applied changes.

### 3.3 Generated-project verification

Greenfield generation is tested at two levels:

1. The orchestrator checks that the model or deterministic generator returns valid file operations.
2. Maven compiles and tests the files after they are written into the target workspace.

This catches defects that ordinary object-level tests cannot detect, including:

- Invalid Java syntax.
- Incorrect package declarations.
- Missing dependencies.
- Invalid Maven configuration.
- Broken Spring wiring.
- Malformed generated tests.

The generated target is intentionally verified in its own workspace so that it is not accidentally relying on classes or dependencies from the orchestrator project.

### 3.4 Self-repair loop testing

When Maven verification fails, the workflow captures the command output and sends the failure evidence to the configured model client. The model may return production-code repairs, after which Maven verification runs again.

The loop is bounded by `agent.max-repair-attempts`, which defaults to three. Testing of this behavior should confirm:

- A failed build produces a repair attempt.
- Repair attempts increment the workflow counter.
- Existing tests and `pom.xml` remain protected during repair.
- Success exits the loop and creates an approval request.
- Exhausting the maximum attempts produces `SAFE_STOPPED`.

The current repository demonstrates this behavior in production code, but it does not yet contain a full automated integration test that drives the complete failure–repair–success cycle with a fake model and fake Maven executor. That is a known gap.

### 3.5 API and workflow integration testing

The intended integration test scope includes:

- Starting a workflow through `POST /api/workflows`.
- Reading status through `GET /api/workflows/{id}`.
- Inspecting audit events through `GET /api/workflows/{id}/events`.
- Approving or rejecting through `POST /api/workflows/{id}/approval`.
- Querying and stopping a deployment.

The current code exposes these APIs, but the repository's automated suite is still weighted toward focused unit tests. Full controller and persistence integration tests should be added before treating the prototype as production-ready.

### 3.6 Deployment and health-check testing

After approval, the orchestrator packages the target, locates the executable JAR, allocates a port, starts the target with `java -jar`, and polls `/actuator/health`.

A professional integration test should verify:

- Only a verified and approved workflow can deploy.
- A rejected workflow does not start a process.
- JAR discovery cannot leave the approved workspace.
- An unavailable port is skipped.
- A healthy target reaches `RUNNING` and the workflow becomes `COMPLETED`.
- A failed health check stops the child process and records a failed deployment.
- Runtime metadata is persisted in H2.

These behaviors are implemented, but process-level tests are not yet comprehensive because they are slower and more operating-system-sensitive than ordinary unit tests.

### 3.7 Generated URL-shortener functional testing

The deterministic Greenfield template is expected to verify the generated application's public behavior:

- `POST /api/urls` creates a short URL.
- `GET /{shortCode}` returns an HTTP redirect.
- `GET /api/urls/{shortCode}/analytics` returns click information.
- `DELETE /api/urls/{shortCode}` removes the URL.
- Unknown codes return `404`.
- Expired URLs return `410`.
- Only successful redirects increment successful analytics.
- Invalid destination schemes are rejected.

Brownfield scenarios should add new regression-test classes rather than altering existing tests. This preserves the baseline definition of correct behavior.

## 4. Manual verification workflow

The recommended local verification sequence is:

### 4.1 Verify the orchestrator

```powershell
mvn clean verify
```

### 4.2 Start the orchestrator

```powershell
mvn spring-boot:run
```

### 4.3 Trigger Greenfield generation

Create a workflow against an empty workspace and wait for `WAITING_FOR_APPROVAL`.

### 4.4 Verify the generated project independently

```powershell
cd .\workspaces\greenfield-url-shortener
mvn clean verify
```

### 4.5 Approve and deploy

Approve the workflow, read the returned deployment base URL, and verify `/actuator/health` reports `UP`.

### 4.6 Exercise the generated API

Test create, redirect, analytics, and delete through HTTP calls.

### 4.7 Trigger Brownfield execution

Stop the running generated application first, or allow the orchestrator to stop it automatically, then submit a Brownfield requirement against the existing workspace.

### 4.8 Inspect the audit trail

Confirm that events record implementation, Maven verification, repair attempts, approval, packaging, startup, and health-check outcomes.

## 5. Test doubles and deterministic execution

The project supports a deterministic `demo` model client so that the main Greenfield workflow can be demonstrated without a provider account or network access.

This is valuable because it makes the following tests reproducible:

- Workspace bootstrap.
- File-operation application.
- Maven verification.
- Approval and deployment lifecycle.

The trade-off is that demo mode cannot perform repository-specific Brownfield reasoning. Brownfield changes require the OpenAI-compatible client, configured for a provider such as Groq.

For future tests, the model abstraction should be replaced with scripted test doubles that return predetermined operations or failures. The command executor should likewise be replaceable with a fake implementation. This would allow fast, deterministic tests of orchestration state transitions without running Maven or calling a model provider.

## 6. Current limitations

### 6.1 Limited automated coverage

The current suite validates scenario routing, deterministic Greenfield output, and mutation policy rules. It does not yet comprehensively test:

- `WorkflowEngine` state transitions.
- H2 persistence across the full workflow.
- Approval authorization and invalid-state handling.
- Model-provider retry and fallback behavior.
- Real process startup and shutdown.
- Port allocation under contention.
- Health-check timeout handling.
- Complete REST-controller behavior.
- End-to-end Greenfield and Brownfield execution in CI.

These are the highest-priority test additions.

### 6.2 External provider variability

Real Brownfield and ambiguous execution depends on a remote model provider. Model responses may vary because of:

- Provider availability.
- Rate limits.
- Model deprecations or access restrictions.
- Structured-output validation failures.
- Output truncation.
- Prompt sensitivity.

Maven and mutation-policy checks reduce the risk of accepting incorrect output, but they do not eliminate provider nondeterminism.

### 6.3 Large structured responses

Returning complete source files inside one JSON response can be fragile. Large responses are more likely to:

- Exceed provider output limits.
- Fail strict JSON validation.
- Be truncated.
- Contain escaping mistakes.

The current prototype accepts this trade-off for simplicity. A more robust design would generate one artifact at a time or use a tool-calling protocol with incremental file writes.

### 6.4 Local process execution

Generated applications run as local child processes. This has several limitations:

- It executes generated code on the host machine.
- Resource isolation is limited.
- A child process may survive an orchestrator crash.
- The in-memory process registry cannot reattach after restart.
- Windows may lock the running JAR and prevent Maven clean until the process is stopped.

The project mitigates the last issue by stopping a deployment before Brownfield mutation of the same workspace, but it does not provide container-grade isolation.

### 6.5 H2 persistence

H2 is appropriate for a portable prototype, but it is not intended to demonstrate production-scale concurrency, high availability, or operational resilience. It also does not solve process-recovery problems after an orchestrator restart.

### 6.6 Simplified ambiguity handling

The ambiguous strategy currently uses deterministic workspace evidence and conservative assumptions. It does not yet:

- Score multiple interpretations.
- Ask interactive clarification questions.
- Persist competing options.
- Route high-risk ambiguity to a dedicated pre-implementation approval.

It demonstrates controlled decision-making, but not a full requirements-negotiation agent.

### 6.7 Simplified dependency graph

The lifecycle is explicit and stateful, but the implementation is closer to a controlled state machine than a general-purpose DAG engine. Parallel architecture, security, documentation, and testing branches are not fully modeled as independently scheduled nodes with synchronization barriers.

### 6.8 Rollback depth

The prototype protects files and uses bounded repair, but rollback is not a complete transactional Git workflow. A production implementation should create a branch and checkpoint before every mutation stage, then reset or revert failed attempts deterministically.

### 6.9 Security scanning

The project validates paths and protects important files, but it does not yet include:

- Dependency vulnerability scanning.
- Static security analysis.
- Secret scanning.
- Sandbox enforcement.
- Network egress restrictions.
- Authentication and authorization for approval APIs.

Passing tests is therefore not equivalent to proving the generated code is secure.

## 7. Key trade-offs

### 7.1 Custom orchestration versus Temporal or Camunda

**Decision:** use a custom in-process workflow engine.

**Advantages:**

- Keeps the orchestration logic visible to reviewers.
- Minimizes infrastructure and setup.
- Fits a two-to-three-day prototype.
- Makes scenario and safety behavior easy to explain.

**Costs:**

- No durable workflow replay.
- Limited recovery after process failure.
- More custom state-transition code.
- No production-grade distributed worker model.

### 7.2 H2 versus PostgreSQL

**Decision:** use H2.

**Advantages:**

- Zero external database installation.
- Fast local startup.
- Easy evaluation and demonstration.

**Costs:**

- Not representative of production concurrency or availability.
- Limited operational tooling.
- Migration to a production database would require additional testing.

### 7.3 `ProcessBuilder` versus Maven Invoker

**Decision:** invoke Maven and Java through `ProcessBuilder`.

**Advantages:**

- Minimal dependencies.
- Transparent commands and output.
- Works with the evaluator's local Maven installation.

**Costs:**

- More platform-specific behavior.
- Process cleanup and timeout handling are custom responsibilities.
- Shell and executable resolution must be tested on Windows and Unix-like systems.

### 7.4 Deterministic demo mode versus fully autonomous execution

**Decision:** provide both modes.

**Advantages:**

- The project can run without an API key.
- Greenfield orchestration is reproducible.
- Provider failures do not prevent demonstration of the architecture.

**Costs:**

- Demo mode is not a realistic Brownfield coding agent.
- Behavior differs by mode and must be documented clearly.

### 7.5 Protecting existing tests versus allowing test maintenance

**Decision:** existing tests are read-only during autonomous Brownfield repair; new tests may be added.

**Advantages:**

- Prevents the model from weakening the definition of success.
- Preserves regression evidence.
- Makes approval decisions more trustworthy.

**Costs:**

- Legitimate test refactors require manual intervention or a separate approval path.
- Some API changes cannot be implemented autonomously when existing tests intentionally need updating.

### 7.6 One large model response versus artifact-by-artifact generation

**Decision:** the current client returns a list of complete file operations in one structured response.

**Advantages:**

- Simple API contract.
- Fewer provider calls.
- Easy persistence and audit summaries.

**Costs:**

- Higher JSON-validation risk.
- Larger token usage per call.
- Harder partial recovery.
- More severe impact if one file in the response is malformed.

Artifact-by-artifact generation is the preferred production evolution.

### 7.7 Local child process versus container deployment

**Decision:** run approved generated applications using `java -jar`.

**Advantages:**

- No Docker requirement.
- Fast startup and simple demonstration.
- Easy access to the generated endpoints.

**Costs:**

- Weak isolation.
- Host resource and filesystem exposure.
- Process lifecycle is more fragile.

### 7.8 Bounded repair versus indefinite autonomy

**Decision:** stop after a configurable number of attempts.

**Advantages:**

- Prevents infinite loops and uncontrolled provider spending.
- Makes failures visible and reviewable.
- Supports safe-stop governance.

**Costs:**

- A repairable problem may remain unresolved after the limit.
- The fixed attempt count does not yet consider whether each attempt is making progress.

A future implementation should compare failure sets and stop early when attempts are not improving.

## 8. Recommended test improvements

The following additions would provide the greatest increase in confidence:

1. Add `WorkflowEngineTest` with fake model, workspace, Maven, deployment, and audit collaborators.
2. Add state-transition tests for success, repair success, retry exhaustion, and mutation-policy rejection.
3. Add `@DataJpaTest` coverage for workflow, approval, audit, and deployment repositories.
4. Add `@WebMvcTest` coverage for workflow, approval, events, and deployment endpoints.
5. Add provider-client tests using a local mock HTTP server for:
   - valid structured response
   - `401`
   - `404 model_not_found`
   - `429`
   - `json_validate_failed`
   - reasoning item followed by `output_text`
   - malformed or truncated JSON
6. Add process-level integration tests for package, start, health check, and stop.
7. Add an end-to-end deterministic Greenfield test that creates a temporary workspace and verifies the generated project with Maven.
8. Add an end-to-end Brownfield fixture containing a known bug and protected baseline tests.
9. Add tests confirming a running deployment is stopped before Maven clean on Windows.
10. Add static analysis, coverage checks, and dependency scanning to `mvn verify`.

## 9. Acceptance criteria for the prototype

For the prototype to be considered successfully demonstrated:

- The orchestrator itself passes `mvn clean verify`.
- Greenfield creates a complete target in an empty workspace.
- The generated target passes its own `mvn clean verify`.
- Brownfield applies a scoped production change without modifying existing tests.
- Ambiguous execution records the chosen interpretation and route.
- A failed build triggers bounded repair or safe-stop.
- A verified workflow waits for human approval.
- Rejection prevents deployment.
- Approval packages and starts the target.
- The target becomes healthy and exposes create, redirect, analytics, and delete endpoints.
- Audit events provide enough evidence to explain the execution and outcome.

## 10. Conclusion

The testing strategy prioritizes the risks that are unique to an autonomous code-generation system: generated code must compile, tests must remain trustworthy, filesystem changes must stay within scope, repair must be bounded, and deployment must remain approval-controlled.

The current implementation is appropriate for a professional prototype, but its automated coverage is not yet sufficient for production. The strongest evidence in the prototype is the combination of mutation-policy enforcement, independent Maven verification, bounded repair, explicit approval, and process health checking. The most important next step is to add full orchestration and deployment integration tests using deterministic fakes and temporary workspaces.
