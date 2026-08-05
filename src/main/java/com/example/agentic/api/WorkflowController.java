package com.example.agentic.api;

import com.example.agentic.application.WorkflowService;
import com.example.agentic.domain.WorkflowRequest;
import com.example.agentic.persistence.ApplicationDeployment;
import com.example.agentic.persistence.AuditEvent;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {
    private final WorkflowService service;

    public WorkflowController(WorkflowService s) {
        service = s;
    }

    @PostMapping
    public ResponseEntity<WorkflowView> start(@Valid @RequestBody WorkflowRequest r) {
        var w = service.start(r);
        return ResponseEntity.status(201).body(WorkflowView.from(w, null));
    }

    @GetMapping("/{id}")
    public WorkflowView get(@PathVariable UUID id) {
        var w = service.get(id);
        ApplicationDeployment d = null;
        try {
            d = service.deployment(id).orElse(null);
        } catch (NoSuchElementException ignored) {
        }
        return WorkflowView.from(w, d);
    }

    @GetMapping("/{id}/events")
    public List<AuditEvent> events(@PathVariable UUID id) {
        return service.events(id);
    }

    @PostMapping("/{id}/approval")
    public WorkflowView approve(@PathVariable UUID id, @Valid @RequestBody ApprovalBody body) {
        var w = service.approve(id, body.decision(), body.comment());
        ApplicationDeployment d = null;
        try {
            d = service.deployment(id).orElse(null);
        } catch (NoSuchElementException ignored) {
        }
        return WorkflowView.from(w, d);
    }

    @GetMapping("/{id}/deployment")
    public WorkflowView.DeploymentView deployment(@PathVariable UUID id) {
        return WorkflowView.DeploymentView.from(service.deployment(id).orElseThrow());
    }

    @PostMapping("/{id}/deployment/stop")
    public WorkflowView.DeploymentView stop(@PathVariable UUID id) {
        return WorkflowView.DeploymentView.from(service.stop(id));
    }
}
