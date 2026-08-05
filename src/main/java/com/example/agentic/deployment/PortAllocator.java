package com.example.agentic.deployment;

import com.example.agentic.config.AgentProperties;
import org.springframework.stereotype.Component;

import java.net.ServerSocket;

@Component
public class PortAllocator {
    private final AgentProperties p;

    public PortAllocator(AgentProperties p) {
        this.p = p;
    }

    public int allocate() {
        for (int port = p.getDeployment().getPortStart(); port <= p.getDeployment().getPortEnd(); port++) {
            try (ServerSocket s = new ServerSocket(port)) {
                s.setReuseAddress(true);
                return port;
            } catch (Exception ignored) {
            }
        }
        throw new IllegalStateException("No available deployment port");
    }
}
