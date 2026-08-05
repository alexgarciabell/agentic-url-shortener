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
    public AgentResponse execute(String systemPrompt, String userPrompt) {
        validateConfiguration();

        String serializedRequest;
        try {
            serializedRequest = objectMapper.writeValueAsString(
                    createRequestBody(systemPrompt, userPrompt));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize model request", exception);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(resolveResponsesEndpoint())
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + properties.getModel().getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(serializedRequest))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Model execution was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Model connection failed: " + exception.getMessage(), exception);
        }

        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Model HTTP %d: %s".formatted(
                    response.statusCode(), abbreviate(response.body())));
        }

        String rawModelContent = extractOutputText(response.body());
        String jsonContent = extractJsonObject(rawModelContent);

        try {
            return objectMapper.readValue(jsonContent, AgentResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Model returned an invalid AgentResponse. Raw output: "
                            + abbreviate(rawModelContent), exception);
        }
    }

    private Map<String, Object> createRequestBody(String systemPrompt, String userPrompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel().getModel());
        body.put("instructions", normalizeSystemPrompt(systemPrompt));
        body.put("input", normalizeUserPrompt(userPrompt));
        body.put("text", Map.of("format", createResponseFormat()));
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

    String extractJsonObject(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Model returned empty content");
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        trimmed = trimmed.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalStateException(
                    "Model returned no JSON object: " + abbreviate(content));
        }
        return trimmed.substring(start, end + 1);
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
