package com.example.agentic.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkflowRepository extends JpaRepository<WorkflowExecution, UUID> {
}
