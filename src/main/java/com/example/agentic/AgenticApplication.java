package com.example.agentic;

import com.example.agentic.config.AgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AgentProperties.class)
public class AgenticApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgenticApplication.class, args);
    }
}
