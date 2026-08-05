package com.example.agentic.agent;

import com.example.agentic.config.AgentProperties;
import com.example.agentic.domain.AgentResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@ConditionalOnProperty(name = "agent.model.mode", havingValue = "openai")
public class OpenAiCompatibleAgentModelClient implements AgentModelClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(180);
    private static final int MAX_ERROR_BODY_LENGTH = 4_000;

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleAgentModelClient(
            AgentProperties properties,
            ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    @Override
    public AgentResponse execute(
            String systemPrompt,
            String userPrompt) {

        validateConfiguration();

        ModelCallException firstFailure;

        try {
            return executeStrict(
                    systemPrompt,
                    userPrompt,
                    false
            );
        } catch (ModelCallException exception) {
            if (!exception.isJsonValidationFailure()) {
                throw exception;
            }

            firstFailure = exception;
        }

        try {
            return executeStrict(
                    systemPrompt,
                    userPrompt,
                    true
            );
        } catch (ModelCallException exception) {
            if (!exception.isJsonValidationFailure()) {
                throw exception;
            }
        }

        try {
            return executeJsonObjectFallback(
                    systemPrompt,
                    userPrompt
            );
        } catch (RuntimeException fallbackFailure) {
            fallbackFailure.addSuppressed(firstFailure);

            throw new IllegalStateException(
                    "The model failed strict structured output "
                            + "and the JSON fallback also failed",
                    fallbackFailure
            );
        }
    }

    private AgentResponse executeStrict(
            String systemPrompt,
            String userPrompt,
            boolean retry) {

        Map<String, Object> requestBody =
                createStrictRequestBody(
                        systemPrompt,
                        retry
                                ? createRetryPrompt(userPrompt)
                                : userPrompt
                );

        String responseBody = sendRequest(requestBody);
        String output = extractOutputText(responseBody);

        return deserializeAgentResponse(output);
    }

    private AgentResponse deserializeAgentResponse(
            String content) {

        try {
            return objectMapper.readValue(
                    content,
                    AgentResponse.class
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Invalid AgentResponse JSON. Raw output: "
                            + abbreviate(content),
                    exception
            );
        }
    }

    private AgentResponse executeJsonObjectFallback(
            String systemPrompt,
            String userPrompt) {

        Map<String, Object> requestBody =
                createJsonObjectRequestBody(
                        systemPrompt,
                        userPrompt
                );

        String responseBody = sendRequest(requestBody);
        String output = extractOutputText(responseBody);
        String json = extractJsonObject(output);

        return deserializeAgentResponse(json);
    }

    private String createRetryPrompt(String originalPrompt) {
        return """
        The previous response could not be validated against the
        required JSON schema.

        Retry the task using the smallest valid set of file operations.

        Requirements:
        - Return only the required structured result.
        - Do not include analysis or explanations.
        - Do not repeat files that do not require changes.
        - Include complete content only for files that must be written.
        - Prefer modifying production code and creating one focused
          regression test.
        - Do not modify existing tests.
        - Use an empty content string for DELETE operations.
        - Keep summary, rationale, and assumptions concise.

        Original task:

        %s
        """.formatted(originalPrompt);
    }

    private String strictSystemPrompt(String suppliedPrompt) {
        return """
        %s

        Return exactly one structured response matching the supplied
        JSON schema.

        Do not include:
        - Markdown
        - code fences
        - analysis
        - chain-of-thought
        - introductions
        - comments outside the structured response
        Minimize the number of file operations.
        - Existing tests are read-only.
        - Create a new test class when regression coverage is needed.
        """.formatted(
                suppliedPrompt == null ? "" : suppliedPrompt.trim()
        );
    }

    private String jsonFallbackSystemPrompt(
            String suppliedPrompt) {

        return """
        %s

        Return only one valid JSON object with this exact shape:

        {
          "summary": "string",
          "rationale": "string",
          "assumptions": ["string"],
          "operations": [
            {
              "operation": "WRITE or DELETE",
              "path": "relative/path",
              "content": "complete content or empty for DELETE"
            }
          ]
        }

        The first character must be {
        The last character must be }
        """.formatted(
                suppliedPrompt == null
                        ? ""
                        : suppliedPrompt.trim()
        );
    }

    private String sendRequest(
            Map<String, Object> requestBody) {

        String serializedRequest;

        try {
            serializedRequest =
                    objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize model request",
                    exception
            );
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(resolveResponsesEndpoint())
                .timeout(REQUEST_TIMEOUT)
                .header(
                        "Authorization",
                        "Bearer "
                                + properties.getModel().getApiKey()
                )
                .header(
                        "Content-Type",
                        "application/json"
                )
                .header(
                        "Accept",
                        "application/json"
                )
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                serializedRequest
                        )
                )
                .build();

        HttpResponse<String> response;

        try {
            response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Model request interrupted",
                    exception
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to connect to model provider: "
                            + exception.getMessage(),
                    exception
            );
        }

        if (response.statusCode() / 100 != 2) {
            throw createModelCallException(
                    response.statusCode(),
                    response.body()
            );
        }

        return response.body();
    }

    private ModelCallException createModelCallException(
            int statusCode,
            String responseBody) {

        String errorCode = null;
        String errorMessage = null;
        String failedGeneration = null;

        try {
            JsonNode root =
                    objectMapper.readTree(responseBody);

            JsonNode error = root.path("error");

            errorCode = textOrNull(
                    error.path("code")
            );

            errorMessage = textOrNull(
                    error.path("message")
            );

            failedGeneration = textOrNull(
                    error.path("failed_generation")
            );
        } catch (JsonProcessingException ignored) {
            // Preserve the raw body below.
        }

        return new ModelCallException(
                statusCode,
                errorCode,
                errorMessage,
                failedGeneration,
                abbreviate(responseBody)
        );
    }

    private String textOrNull(JsonNode node) {
        if (node == null
                || node.isMissingNode()
                || node.isNull()) {
            return null;
        }

        if (node.isTextual()) {
            return node.asText();
        }

        return node.toString();
    }

    private Map<String, Object> createRequestBody(String systemPrompt, String userPrompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel().getModel());
        body.put("instructions", normalizeSystemPrompt(systemPrompt));
        body.put("input", normalizeUserPrompt(userPrompt));
        body.put("text", Map.of("format", createResponseFormat()));
        return body;
    }

    private Map<String, Object> createStrictRequestBody(
            String systemPrompt,
            String userPrompt) {

        Map<String, Object> body = new LinkedHashMap<>();

        body.put(
                "model",
                properties.getModel().getModel()
        );

        body.put(
                "instructions",
                strictSystemPrompt(systemPrompt)
        );

        body.put(
                "input",
                userPrompt
        );

        body.put(
                "temperature",
                0.1
        );

        body.put(
                "max_output_tokens",
                32_000
        );

        body.put(
                "text",
                Map.of(
                        "format",
                        Map.of(
                                "type", "json_schema",
                                "name", "agent_response",
                                "strict", true,
                                "schema", createAgentResponseSchema()
                        )
                )
        );

        return body;
    }

    private Map<String, Object> createJsonObjectRequestBody(
            String systemPrompt,
            String userPrompt) {

        Map<String, Object> body = new LinkedHashMap<>();

        body.put(
                "model",
                properties.getModel().getModel()
        );

        body.put(
                "instructions",
                jsonFallbackSystemPrompt(systemPrompt)
        );

        body.put(
                "input",
                userPrompt
        );

        body.put(
                "temperature",
                0.1
        );

        body.put(
                "max_output_tokens",
                32_000
        );

        body.put(
                "text",
                Map.of(
                        "format",
                        Map.of(
                                "type",
                                "json_object"
                        )
                )
        );

        return body;
    }

    private Map<String, Object> createResponseFormat() {
        return Map.of(
                "type", "json_schema",
                "name", "agent_response",
                "strict", true,
                "schema", createAgentResponseSchema());
    }

    private Map<String, Object> createAgentResponseSchema() {
        Map<String, Object> operationSchema = new LinkedHashMap<>();
        operationSchema.put("type", "object");
        operationSchema.put("additionalProperties", false);
        operationSchema.put("required", List.of("path", "content", "operation"));
        operationSchema.put("properties", Map.of(
                "path", Map.of("type", "string"),
                "content", Map.of("type", "string"),
                "operation", Map.of(
                        "type", "string",
                        "enum", List.of("WRITE", "DELETE"))));

        Map<String, Object> rootSchema = new LinkedHashMap<>();
        rootSchema.put("type", "object");
        rootSchema.put("additionalProperties", false);
        rootSchema.put("required", List.of(
                "summary", "rationale", "assumptions", "operations"));
        rootSchema.put("properties", Map.of(
                "summary", Map.of("type", "string"),
                "rationale", Map.of("type", "string"),
                "assumptions", Map.of(
                        "type", "array",
                        "items", Map.of("type", "string")),
                "operations", Map.of(
                        "type", "array",
                        "items", operationSchema)));
        return rootSchema;
    }

    private String normalizeSystemPrompt(String systemPrompt) {
        String supplied = systemPrompt == null ? "" : systemPrompt.trim();
        return """
                %s
                
                Return only the structured response required by the supplied JSON schema.
                Do not include Markdown, code fences, introductions, analysis, reasoning,
                or text before or after the JSON object.
                
                File-operation rules:
                - WRITE must contain the complete file content.
                - DELETE must use an empty content string.
                - Paths must be relative to the workspace.
                - Never use absolute paths or ../ path traversal.
                """.formatted(supplied);
    }

    private String normalizeUserPrompt(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("userPrompt must not be blank");
        }
        return userPrompt.trim();
    }

    private URI resolveResponsesEndpoint() {
        String baseUrl = properties.getModel().getBaseUrl().trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (baseUrl.endsWith("/responses")) {
            return URI.create(baseUrl);
        }
        return URI.create(baseUrl + "/responses");
    }

    String extractOutputText(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("Model returned an empty HTTP response");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Model returned invalid response JSON: " + abbreviate(responseBody), exception);
        }

        JsonNode directOutputText = root.path("output_text");
        if (directOutputText.isTextual() && !directOutputText.asText().isBlank()) {
            return directOutputText.asText().trim();
        }

        JsonNode output = root.path("output");
        if (!output.isArray()) {
            throw new IllegalStateException(
                    "Model response does not contain an output array: "
                            + abbreviate(responseBody));
        }

        for (JsonNode outputItem : output) {
            if (!"message".equals(outputItem.path("type").asText(""))) {
                continue;
            }
            JsonNode content = outputItem.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode contentItem : content) {
                String contentType = contentItem.path("type").asText("");
                if (!"output_text".equals(contentType) && !"text".equals(contentType)) {
                    continue;
                }
                JsonNode textNode = contentItem.path("text");
                if (textNode.isTextual() && !textNode.asText().isBlank()) {
                    return textNode.asText().trim();
                }
            }
        }

        throw new IllegalStateException(
                "Model returned no output_text content. Raw response: "
                        + abbreviate(responseBody));
    }

//    String extractJsonObject(String content) {
//        if (content == null || content.isBlank()) {
//            throw new IllegalStateException("Model returned empty content");
//        }
//        String trimmed = content.trim();
//        if (trimmed.startsWith("```json")) {
//            trimmed = trimmed.substring(7);
//        } else if (trimmed.startsWith("```")) {
//            trimmed = trimmed.substring(3);
//        }
//        if (trimmed.endsWith("```")) {
//            trimmed = trimmed.substring(0, trimmed.length() - 3);
//        }
//        trimmed = trimmed.trim();
//        int start = trimmed.indexOf('{');
//        int end = trimmed.lastIndexOf('}');
//        if (start < 0 || end < start) {
//            throw new IllegalStateException(
//                    "Model returned no JSON object: " + abbreviate(content));
//        }
//        return trimmed.substring(start, end + 1);
//    }

    private String extractJsonObject(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException(
                    "Model returned empty content"
            );
        }

        String value = content.trim();

        if (value.startsWith("```json")) {
            value = value.substring(7).trim();
        } else if (value.startsWith("```")) {
            value = value.substring(3).trim();
        }

        if (value.endsWith("```")) {
            value = value.substring(
                    0,
                    value.length() - 3
            ).trim();
        }

        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');

        if (start < 0 || end < start) {
            throw new IllegalStateException(
                    "Model response contains no JSON object: "
                            + abbreviate(content)
            );
        }

        return value.substring(start, end + 1);
    }

    private void validateConfiguration() {
        AgentProperties.Model model = properties.getModel();
        if (model.getApiKey() == null || model.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "Agent model API key is missing. Set AGENT_MODEL_API_KEY.");
        }
        if (model.getBaseUrl() == null || model.getBaseUrl().isBlank()) {
            throw new IllegalStateException("Agent model base URL is missing");
        }
        if (model.getModel() == null || model.getModel().isBlank()) {
            throw new IllegalStateException("Agent model name is missing");
        }
        if (!model.getBaseUrl().startsWith("https://")) {
            throw new IllegalStateException("Agent model base URL must use HTTPS");
        }
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "<null>";
        }
        String normalized = value.trim();
        return normalized.length() <= MAX_ERROR_BODY_LENGTH
                ? normalized
                : normalized.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
    }
}
