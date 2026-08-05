# Greenfield template escaping fix

The deterministic Greenfield template previously embedded JSON in a generated Java string using a single escaping layer.
Java text blocks consume that layer while compiling the orchestrator, so the emitted `UrlControllerTest.java` contained
unescaped JSON quotes and failed test compilation.

The template now uses two escaping layers so the generated Java source contains:

```java
.content("{\"targetUrl\":\"https://example.com\",\"customAlias\":\"docs\"}")
```

Existing failed workspaces should be deleted and regenerated because repair mode intentionally protects generated tests.
