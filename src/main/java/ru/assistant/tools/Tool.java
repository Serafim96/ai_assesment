package ru.assistant.tools;

import java.util.Map;

public interface Tool {

    String name();

    String description();

    /**
     * Raw JSON Schema string describing the tool's parameters object,
     * e.g. {"type":"object","properties":{...},"required":[...]}.
     */
    String jsonSchema();

    String execute(Map<String, Object> args) throws ToolError;
}
