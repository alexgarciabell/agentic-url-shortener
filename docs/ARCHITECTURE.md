# Architecture

The prototype is one orchestrator JVM and zero or more generated target JVMs.

`WorkflowController -> WorkflowService -> WorkflowEngine -> ScenarioStrategy -> AgentModelClient -> WorkspaceService -> MavenBuildService`

After verification and approval:

`WorkflowService -> DeploymentService -> package -> java -jar -> /actuator/health`

H2 persists workflow, audit, approval, and deployment metadata. Child process handles remain in an in-memory registry.
An explicit mutation phase distinguishes Greenfield bootstrap from protected repair. Maven is the objective release
gate.
