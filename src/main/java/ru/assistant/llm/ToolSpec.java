package ru.assistant.llm;

public class ToolSpec {

    private final String name;
    private final String description;
    private final String jsonSchema;

    public ToolSpec(String name, String description, String jsonSchema) {
        this.name = name;
        this.description = description;
        this.jsonSchema = jsonSchema;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getJsonSchema() {
        return jsonSchema;
    }
}
