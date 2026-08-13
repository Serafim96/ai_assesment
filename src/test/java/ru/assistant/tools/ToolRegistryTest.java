package ru.assistant.tools;

import org.junit.Test;
import ru.assistant.llm.ToolSpec;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ToolRegistryTest {

    @Test
    public void getFindsToolByName() {
        Tool tool = new StubTool("alpha");
        ToolRegistry registry = new ToolRegistry(Arrays.asList(tool));

        Optional<Tool> found = registry.get("alpha");

        assertTrue(found.isPresent());
        assertSame(tool, found.get());
    }

    @Test
    public void getReturnsEmptyForUnknownName() {
        ToolRegistry registry = new ToolRegistry(Arrays.asList(new StubTool("alpha")));

        assertFalse(registry.get("unknown").isPresent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsDuplicateNames() {
        new ToolRegistry(Arrays.asList(new StubTool("alpha"), new StubTool("alpha")));
    }

    @Test
    public void toToolSpecsReturnsOneSpecPerToolWithMatchingFields() {
        Tool a = new StubTool("alpha");
        Tool b = new StubTool("beta");
        ToolRegistry registry = new ToolRegistry(Arrays.asList(a, b));

        List<ToolSpec> specs = registry.toToolSpecs();

        assertEquals(2, specs.size());
        for (ToolSpec spec : specs) {
            Tool matching = spec.getName().equals("alpha") ? a : b;
            assertEquals(matching.name(), spec.getName());
            assertEquals(matching.description(), spec.getDescription());
            assertEquals(matching.jsonSchema(), spec.getJsonSchema());
        }
    }

    @Test
    public void allReturnsAllRegisteredTools() {
        Tool a = new StubTool("alpha");
        Tool b = new StubTool("beta");
        ToolRegistry registry = new ToolRegistry(Arrays.asList(a, b));

        assertEquals(2, registry.all().size());
        assertTrue(registry.all().contains(a));
        assertTrue(registry.all().contains(b));
    }

    private static class StubTool implements Tool {
        private final String name;

        StubTool(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return "stub tool " + name;
        }

        @Override
        public String jsonSchema() {
            return "{\"type\":\"object\",\"properties\":{}}";
        }

        @Override
        public String execute(Map<String, Object> args) throws ToolError {
            return "stub result";
        }
    }
}
