package ru.assistant.llm;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ModelsTest {

    @Test
    public void chatMessageBasicConstructorAndGetters() {
        ChatMessage message = new ChatMessage("tool", "the result", "call_1");
        assertEquals("tool", message.getRole());
        assertEquals("the result", message.getContent());
        assertEquals("call_1", message.getToolCallId());
    }

    @Test
    public void chatMessageFactories() {
        assertEquals("system", ChatMessage.system("s").getRole());
        assertEquals("user", ChatMessage.user("u").getRole());
        assertEquals("assistant", ChatMessage.assistant("a").getRole());

        ChatMessage toolMessage = ChatMessage.tool("call_42", "result");
        assertEquals("tool", toolMessage.getRole());
        assertEquals("call_42", toolMessage.getToolCallId());
        assertEquals("result", toolMessage.getContent());

        assertNull(ChatMessage.user("u").getToolCallId());
    }

    @Test
    public void toolSpecGetters() {
        ToolSpec spec = new ToolSpec("get_weather", "returns weather", "{\"type\":\"object\"}");
        assertEquals("get_weather", spec.getName());
        assertEquals("returns weather", spec.getDescription());
        assertEquals("{\"type\":\"object\"}", spec.getJsonSchema());
    }

    @Test
    public void toolCallGetters() {
        ToolCall call = new ToolCall("call_1", "get_weather", "{\"city\":\"Moscow\"}");
        assertEquals("call_1", call.getId());
        assertEquals("get_weather", call.getName());
        assertEquals("{\"city\":\"Moscow\"}", call.getArgumentsJson());
    }

    @Test
    public void chatResponseNullToolCallsBecomesEmptyList() {
        ChatResponse response = new ChatResponse("hi", null, "stop");
        assertEquals("hi", response.getContent());
        assertEquals("stop", response.getFinishReason());
        assertTrue(response.getToolCalls().isEmpty());
    }

    @Test
    public void chatResponseWithToolCalls() {
        ToolCall call = new ToolCall("call_1", "get_weather", "{}");
        ChatResponse response = new ChatResponse(null, Arrays.asList(call), "tool_calls");
        assertNull(response.getContent());
        assertEquals(1, response.getToolCalls().size());
        assertEquals("tool_calls", response.getFinishReason());
    }
}
