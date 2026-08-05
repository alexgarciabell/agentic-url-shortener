# Repository instructions

This is a Java 21 Spring Boot interview prototype. Keep it simple, professional, testable, and governed.

The orchestrator supports GREENFIELD, BROWNFIELD, and AMBIGUOUS scenarios. It generates or modifies code in an isolated
workspace, runs `mvn clean verify`, analyzes failures, performs bounded implementation repairs, requests human approval,
packages an approved target, starts it as a separate process, and checks `/actuator/health`.

Never weaken, delete, or disable protected tests to manufacture success. During repair, protect `pom.xml`,
`src/test/**`, `.git/**`, `.github/**`, Maven wrappers, and `target/**`. Use Java 21 records for data carriers,
constructor injection, focused classes, and explicit workflow states. Do not add Temporal, Camunda, Kafka, Redis,
PostgreSQL, Docker, Kubernetes, or a second AI framework.
