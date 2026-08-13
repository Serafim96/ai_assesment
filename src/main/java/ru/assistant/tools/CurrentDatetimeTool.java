package ru.assistant.tools;

import java.time.Clock;
import java.util.Map;

public class CurrentDatetimeTool implements Tool {

    private final Clock clock;

    public CurrentDatetimeTool(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String name() {
        return "current_datetime";
    }

    @Override
    public String description() {
        return "Returns the current date and time as an ISO-8601 instant (UTC).";
    }

    @Override
    public String jsonSchema() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    @Override
    public String execute(Map<String, Object> args) throws ToolError {
        return clock.instant().toString();
    }
}
