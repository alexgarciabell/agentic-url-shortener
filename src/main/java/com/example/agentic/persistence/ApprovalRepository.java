package com.example.agentic.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApprovalRepository extends JpaRepository<ApprovalRequest, Long> {
    Optional<ApprovalRequest> findTopByWorkflowIdOrderByRequestedAtDesc(UUID id);
}
