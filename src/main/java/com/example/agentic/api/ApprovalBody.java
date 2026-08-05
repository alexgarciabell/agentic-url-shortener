package com.example.agentic.api;

import com.example.agentic.domain.ApprovalDecision;
import jakarta.validation.constraints.NotNull;

public record ApprovalBody(@NotNull ApprovalDecision decision, String comment) {
}
