package com.example.agentic.scenario;

import com.example.agentic.domain.ScenarioType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ScenarioRegistry {
    private final Map<ScenarioType, ScenarioStrategy> map;

    public ScenarioRegistry(List<ScenarioStrategy> strategies) {
        var m = new EnumMap<ScenarioType, ScenarioStrategy>(ScenarioType.class);
        strategies.forEach(s -> m.put(s.type(), s));
        map = Map.copyOf(m);
    }

    public ScenarioStrategy get(ScenarioType type) {
        return Optional.ofNullable(map.get(type)).orElseThrow();
    }
}
