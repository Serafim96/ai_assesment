package ru.assistant.tools;

import ru.assistant.llm.ToolSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ToolRegistry {

    private final Map<String, Tool> tools;

    public ToolRegistry(List<Tool> toolList) {
        Map<String, Tool> byName = new LinkedHashMap<>();
        for (Tool tool : toolList) {
            String name = tool.name();
            if (byName.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate tool name: " + name);
            }
            byName.put(name, tool);
        }
        this.tools = byName;
    }

    public Optional<Tool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<ToolSpec> toToolSpecs() {
        List<ToolSpec> specs = new ArrayList<>();
        for (Tool tool : tools.values()) {
            specs.add(new ToolSpec(tool.name(), tool.description(), tool.jsonSchema()));
        }
        return specs;
    }

    public Collection<Tool> all() {
        return Collections.unmodifiableCollection(tools.values());
    }
}
