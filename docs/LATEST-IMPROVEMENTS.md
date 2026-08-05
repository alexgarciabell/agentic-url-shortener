# Latest improvements

This build consolidates the fixes discussed during interactive testing:

1. Groq-compatible OpenAI Responses API client.
2. Strict JSON-schema output for `AgentResponse`.
3. Robust extraction of `output_text` when GPT-OSS returns reasoning items before the final message.
4. Defensive extraction of a JSON object from accidental prose or code fences.
5. Full provider HTTP status and response body surfaced in errors.
6. Generic model environment variables (`AGENT_MODEL_*`).
7. Greenfield bootstrap may create the Maven project, production code, and tests.
8. Brownfield changes may create new regression tests but cannot overwrite existing tests.
9. Existing tests, `pom.xml`, wrappers, Git/CI metadata, and build output remain protected.
10. Workspace mutation validates path traversal and applies full `FileOperation` objects.
11. Running generated applications are stopped before Brownfield mutation of the same workspace, avoiding locked-JAR
    Maven failures on Windows.
12. Approval still occurs only after `mvn clean verify`; approved results are packaged, started as a child process, and
    health checked.
