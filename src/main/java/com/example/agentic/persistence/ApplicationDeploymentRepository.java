package com.example.agentic.persistence;

import com.example.agentic.domain.DeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationDeploymentRepository
        extends JpaRepository<ApplicationDeployment, Long> {

    Optional<ApplicationDeployment> findTopByWorkflowIdOrderByIdDesc(UUID workflowId);

    List<ApplicationDeployment> findByStatus(DeploymentStatus status);
}
