package ru.assistant.agent;

import org.junit.Test;
import ru.assistant.llm.ChatResponse;
import ru.assistant.llm.MockLlmClient;
import ru.assistant.llm.ToolCall;
import ru.assistant.tools.CurrentDatetimeTool;
import ru.assistant.tools.Tool;
import ru.assistant.tools.ToolError;
import ru.assistant.tools.ToolRegistry;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AgentToolLoopTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-13T10:00:00Z"), ZoneOffset.UTC);

    @Test
    public void scenarioA_singleToolCallThenFinalAnswer() throws Exception {
        ToolRegistry registry = new ToolRegistry(Arrays.asList((Tool) new CurrentDatetimeTool(FIXED_CLOCK)));
        MockLlmClient llm = new MockLlmClient(Arrays.asList(
                new ChatResponse(null, Arrays.asList(new ToolCall("call1", "current_datetime", "{}")), "tool_calls"),
                new ChatResponse("The current time is 2026-08-13T10:00:00Z", null, "stop")
        ));
        AgentToolLoop loop = new AgentToolLoop(llm, registry, 5);

        String result = loop.run("What time is it?");

        assertEquals("The current time is 2026-08-13T10:00:00Z", result);
    }

    @Test
    public void scenarioB_noToolCallsReturnsImmediateFinalAnswer() throws Exception {
        ToolRegistry registry = new ToolRegistry(Collections.<Tool>emptyList());
        MockLlmClient llm = new MockLlmClient(Arrays.asList(
                new ChatResponse("Hello there", null, "stop")
        ));
        AgentToolLoop loop = new AgentToolLoop(llm, registry, 5);

        String result = loop.run("hi");

        assertEquals("Hello there", result);
    }

    @Test
    public void scenarioC_maxStepsExceededStopsWithPredictableFallback() throws Exception {
        ToolRegistry registry = new ToolRegistry(Arrays.asList((Tool) new AlwaysOkTool()));
        MockLlmClient llm = new MockLlmClient(Arrays.asList(
                new ChatResponse(null, Arrays.asList(new ToolCall("c1", "always_ok", "{}")), "tool_calls"),
                new ChatResponse(null, Arrays.asList(new ToolCall("c2", "always_ok", "{}")), "tool_calls"),
                new ChatResponse(null, Arrays.asList(new ToolCall("c3", "always_ok", "{}")), "tool_calls")
        ));
        AgentToolLoop loop = new AgentToolLoop(llm, registry, 3);

        String result = loop.run("keep going forever");

        assertTrue(result.length() > 0);
    }

    @Test
    public void scenarioD_unknownToolNameDoesNotCrashLoop() throws Exception {
        ToolRegistry registry = new ToolRegistry(Collections.<Tool>emptyList());
        MockLlmClient llm = new MockLlmClient(Arrays.asList(
                new ChatResponse(null, Arrays.asList(new ToolCall("id1", "does_not_exist", "{}")), "tool_calls"),
                new ChatResponse("Sorry, I could not complete that.", null, "stop")
        ));
        AgentToolLoop loop = new AgentToolLoop(llm, registry, 5);

        String result = loop.run("do something unsupported");

        assertEquals("Sorry, I could not complete that.", result);
    }

    @Test
    public void scenarioE_invalidArgumentsJsonDoesNotCrashLoop() throws Exception {
        ToolRegistry registry = new ToolRegistry(Arrays.asList((Tool) new CurrentDatetimeTool(FIXED_CLOCK)));
        MockLlmClient llm = new MockLlmClient(Arrays.asList(
                new ChatResponse(null, Arrays.asList(new ToolCall("id1", "current_datetime", "not-json")), "tool_calls"),
                new ChatResponse("Final answer despite bad args", null, "stop")
        ));
        AgentToolLoop loop = new AgentToolLoop(llm, registry, 5);

        String result = loop.run("please call with garbage args");

        assertEquals("Final answer despite bad args", result);
    }

    @Test
    public void scenarioF_toolThrowsToolErrorDoesNotCrashLoop() throws Exception {
        ToolRegistry registry = new ToolRegistry(Arrays.asList((Tool) new AlwaysThrowsTool()));
        MockLlmClient llm = new MockLlmClient(Arrays.asList(
                new ChatResponse(null, Arrays.asList(new ToolCall("id1", "always_throws", "{}")), "tool_calls"),
                new ChatResponse("Recovered from tool failure", null, "stop")
        ));
        AgentToolLoop loop = new AgentToolLoop(llm, registry, 5);

        String result = loop.run("trigger a tool failure");

        assertEquals("Recovered from tool failure", result);
    }

    private static class AlwaysOkTool implements Tool {
        @Override
        public String name() {
            return "always_ok";
        }

        @Override
        public String description() {
            return "test stub tool that always succeeds";
        }

        @Override
        public String jsonSchema() {
            return "{\"type\":\"object\",\"properties\":{}}";
        }

        @Override
        public String execute(Map<String, Object> args) throws ToolError {
            return "ok";
        }
    }

    private static class AlwaysThrowsTool implements Tool {
        @Override
        public String name() {
            return "always_throws";
        }

        @Override
        public String description() {
            return "test stub tool that always throws ToolError";
        }

        @Override
        public String jsonSchema() {
            return "{\"type\":\"object\",\"properties\":{}}";
        }

        @Override
        public String execute(Map<String, Object> args) throws ToolError {
            throw new ToolError("simulated tool failure");
        }
    }
}
