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

```powershell
mvn clean verify
mvn spring-boot:run
```

The default model mode is deterministic `demo`, so no API key is required. For real structured generation:

```powershell
$env:AGENT_MODEL_MODE = "openai"
$env:OPENAI_API_KEY = "..."
$env:OPENAI_MODEL = "gpt-5"
mvn spring-boot:run
```

## Trigger Greenfield

```powershell
$body = @{
  scenarioType = "GREENFIELD"
  requirement = "Create a URL shortener with create, redirect, analytics, delete, expiration and HTTP/HTTPS validation."
  workspacePath = ".\workspaces\greenfield-url-shortener"
} | ConvertTo-Json

$response = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/workflows" -ContentType "application/json" -Body $body
$response
```

The orchestrator creates the workspace when needed. It then generates a complete target, verifies it, and returns
`WAITING_FOR_APPROVAL` when successful.

## Approve and deploy

```powershell
$workflowId = $response.id
$approval = @{ decision="APPROVED"; comment="Reviewed verified code" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/workflows/$workflowId/approval" -ContentType "application/json" -Body $approval
```

A successful response contains the generated application's base URL and templates for:

- `POST /api/urls`
- `GET /{shortCode}`
- `GET /api/urls/{shortCode}/analytics`
- `DELETE /api/urls/{shortCode}`

## Test the generated app

```powershell
$baseUrl = "http://localhost:8090" # use the returned deployment.baseUrl
$create = @{ targetUrl="https://example.com"; customAlias="docs" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$baseUrl/api/urls" -ContentType "application/json" -Body $create
Invoke-WebRequest -MaximumRedirection 0 -Uri "$baseUrl/docs"
Invoke-RestMethod -Uri "$baseUrl/api/urls/docs/analytics"
Invoke-RestMethod -Method Delete -Uri "$baseUrl/api/urls/docs"
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

```powershell
$env:AGENT_MODEL_MODE = "openai"
$env:AGENT_MODEL_API_KEY = "gsk_your_key"
$env:AGENT_MODEL_BASE_URL = "https://api.groq.com/openai/v1"
$env:AGENT_MODEL_NAME = "openai/gpt-oss-120b"
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
