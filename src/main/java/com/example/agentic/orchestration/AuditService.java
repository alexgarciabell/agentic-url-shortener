package com.example.agentic.orchestration;

import com.example.agentic.persistence.AuditEvent;
import com.example.agentic.persistence.AuditEventRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditService {
    private final AuditEventRepository repo;

    public AuditService(AuditEventRepository r) {
        repo = r;
    }

    public void record(UUID id, String type, String message) {
        repo.save(new AuditEvent(id, type, message));
    }
}
