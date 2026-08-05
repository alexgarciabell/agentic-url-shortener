# Agentic URL Shortener — Complete Prototype

A Java 21/Spring Boot prototype that implements three governed software-engineering scenarios:

- **GREENFIELD**: bootstraps a complete URL-shortener Maven project in an empty or new workspace.
- **BROWNFIELD**: inspects and changes an existing Maven project.
- **AMBIGUOUS**: resolves an unclear request to the safest reversible interpretation, then selects Greenfield or
  Brownfield from workspace evidence.

The orchestrator writes files, runs `mvn clean verify`, performs bounded production-code repair, waits for human
approval, packages the approved target, starts its executable JAR on an available port, checks `/actuator/health`, and
returns endpoint details.

## Requirements

- Java 21
- Maven 3.9+

## Run

```bash
mvn clean verify
mvn spring-boot:run
```

The default model mode is deterministic `demo`, so no API key is required. For real structured generation:

```bash
export AGENT_MODEL_MODE="openai"
export OPENAI_API_KEY="..."
export OPENAI_MODEL="gpt-5"
mvn spring-boot:run
```

## Trigger Greenfield

```bash
curl -sS -X POST "http://localhost:8080/api/workflows" \
  -H "Content-Type: application/json" \
  -d '{
    "scenarioType": "GREENFIELD",
    "requirement": "Create a URL shortener with create, redirect, analytics, delete, expiration and HTTP/HTTPS validation.",
    "workspacePath": "./workspaces/greenfield-url-shortener"
  }'
```

The orchestrator creates the workspace when needed. It then generates a complete target, verifies it, and returns
`WAITING_FOR_APPROVAL` when successful.

Example response:

```json
{
  "id": "bb90a669-1e9c-49eb-bfa1-8ebb1b188f1d",
  "scenarioType": "GREENFIELD",
  "status": "WAITING_FOR_APPROVAL",
  "currentNode": "RELEASE_REVIEW",
  "repairAttempts": 0,
  "decisionSummary": "Bootstrap a complete Java 21 Maven application in the target workspace. Assumptions: []",
  "startedAt": "2026-08-05T06:19:59.859324500Z",
  "completedAt": null,
  "deployment": null
}
```

A report after triggering a scenario provides workflow events like this:

```json
[
  {
    "id": 11,
    "workflowId": "bb90a669-1e9c-49eb-bfa1-8ebb1b188f1d",
    "eventType": "WORKFLOW_STARTED",
    "message": "Create a Java 21 Maven URL shortener with create, redirect, validation,\nunit tests and related workflow steps...",
    "createdAt": "2026-08-05T06:20:00.136103Z"
  },
  {
    "id": 12,
    "workflowId": "bb90a669-1e9c-49eb-bfa1-8ebb1b188f1d",
    "eventType": "AGENT_IMPLEMENTATION",
    "message": "Generated deterministic URL shortener - Created a complete Java 21 Spring Boot target\nwith H2, API endpoints, tests, and Actuator...",
    "createdAt": "2026-08-05T06:20:00.233641Z"
  }
]
```

## Approve and deploy

```bash
curl -sS -X POST "http://localhost:8080/api/workflows/<workflow-id>/approval" \
  -H "Content-Type: application/json" \
  -d '{
    "decision": "APPROVED",
    "comment": "Reviewed verified code"
  }'
```

A successful approval response is similar to this:

```json
{
  "id": "bb90a669-1e9c-49eb-bfa1-8ebb1b188f1d",
  "scenarioType": "GREENFIELD",
  "status": "COMPLETED",
  "currentNode": "COMPLETED",
  "repairAttempts": 0,
  "decisionSummary": "Bootstrap a complete Java 21 Maven application in the target workspace. Assumptions: []",
  "startedAt": "2026-08-05T06:19:59.859325Z",
  "completedAt": "2026-08-05T06:28:36.498671600Z",
  "deployment": {
    "status": "RUNNING",
    "baseUrl": "http://localhost:8091",
    "port": 8091,
    "healthUrl": "http://localhost:8091/actuator/health",
    "createEndpoint": "POST http://localhost:8091/api/urls",
    "redirectTemplate": "GET http://localhost:8091/{shortCode}",
    "analyticsTemplate": "GET http://localhost:8091/api/urls/{shortCode}/analytics",
    "deleteTemplate": "DELETE http://localhost:8091/api/urls/{shortCode}",
    "failureReason": null
  }
}
```

A successful response contains the generated application's base URL and templates for:

- `POST /api/urls`
- `GET /{shortCode}`
- `GET /api/urls/{shortCode}/analytics`
- `DELETE /api/urls/{shortCode}`

To run another scenario, stop the current deployment first. After a scenario is created and approved, a new
deployment is launched automatically.

## Test the generated app

```bash
baseUrl="http://localhost:8090" # use the returned deployment.baseUrl

curl -sS -X POST "$baseUrl/api/urls" \
  -H "Content-Type: application/json" \
  -d '{
    "targetUrl": "https://example.com",
    "customAlias": "docs"
  }'

curl -i "$baseUrl/docs"

curl -sS "$baseUrl/api/urls/docs/analytics"
curl -sS -X DELETE "$baseUrl/api/urls/docs"
```

## Other APIs

- `GET /api/workflows/{id}`
- `GET /api/workflows/{id}/events`
- `GET /api/workflows/{id}/deployment`
- `POST /api/workflows/{id}/deployment/stop`

## Safety and prototype limitations

- Greenfield bootstrap can create `pom.xml` and tests.
- Repair attempts cannot modify `pom.xml`, existing tests, CI, Git metadata, Maven wrappers, or `target`.
- Maximum repair attempts default to three.
- Generated apps run as child processes. Live `Process` references are intentionally in memory; after an orchestrator
  restart, old processes are not automatically reattached.
- Demo mode provides deterministic Greenfield generation. Brownfield repository-specific edits require OpenAI-compatible
  mode.

## Groq configuration

The real model client uses Groq through its OpenAI-compatible Responses API.

```bash
export AGENT_MODEL_MODE="openai"
export AGENT_MODEL_API_KEY="gsk_your_key"
export AGENT_MODEL_BASE_URL="https://api.groq.com/openai/v1"
export AGENT_MODEL_NAME="openai/gpt-oss-120b"
mvn spring-boot:run
```

The default remains `demo`, which can generate the deterministic Greenfield sample but intentionally performs no
autonomous Brownfield modification.

## Brownfield mutation policy

Brownfield and repair workflows may:

- Modify production files under `src/main/**`.
- Create new regression-test files under `src/test/**`.
- Update `README.md`.

They may not automatically:

- Modify or delete existing tests.
- Modify `pom.xml`.
- Modify Maven wrappers, Git metadata, CI configuration, or `target/**`.

When a new Brownfield workflow targets a workspace whose generated JAR is still running, the orchestrator stops that
deployment before executing `mvn clean verify`. This prevents Windows file-lock failures during Maven clean.
