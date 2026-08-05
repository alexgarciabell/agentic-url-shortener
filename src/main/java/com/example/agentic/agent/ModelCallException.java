package com.example.agentic.agent;

public class ModelCallException extends IllegalStateException {

    private final int statusCode;
    private final String errorCode;
    private final String failedGeneration;

    public ModelCallException(
            int statusCode,
            String errorCode,
            String errorMessage,
            String failedGeneration,
            String rawBody) {

        super(
                "Model HTTP %d code=%s message=%s failedGeneration=%s body=%s"
                        .formatted(
                                statusCode,
                                errorCode,
                                errorMessage,
                                failedGeneration,
                                rawBody
                        )
        );

        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.failedGeneration = failedGeneration;
    }

    public boolean isJsonValidationFailure() {
        return statusCode == 400
                && "json_validate_failed".equals(errorCode);
    }

    public int statusCode() {
        return statusCode;
    }

    public String errorCode() {
        return errorCode;
    }

    public String failedGeneration() {
        return failedGeneration;
    }
}

