package ru.assistant.llm;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MockLlmClientTest {

    private List<ChatMessage> anyMessages() {
        return Collections.singletonList(ChatMessage.user("hello"));
    }

    private List<ToolSpec> noTools() {
        return Collections.emptyList();
    }

    @Test
    public void returnsQueuedResponsesInOrder() throws Exception {
        ChatResponse first = new ChatResponse("first", Collections.<ToolCall>emptyList(), "stop");
        ChatResponse second = new ChatResponse("second", Collections.<ToolCall>emptyList(), "stop");
        MockLlmClient client = new MockLlmClient(Arrays.asList(first, second));

        ChatResponse actualFirst = client.chat(anyMessages(), noTools());
        ChatResponse actualSecond = client.chat(anyMessages(), noTools());

        assertEquals("first", actualFirst.getContent());
        assertEquals("second", actualSecond.getContent());
    }

    @Test
    public void emptyQueueThrowsLlmExceptionNotNpe() {
        MockLlmClient client = new MockLlmClient(Collections.<ChatResponse>emptyList());

        try {
            client.chat(anyMessages(), noTools());
            fail("Expected LlmException");
        } catch (LlmException e) {
            assertTrue(e.getMessage().toLowerCase().contains("misconfiguration"));
        }
    }

    @Test
    public void throwOnNextCallThrowsOnceThenResumesNormalBehavior() throws Exception {
        ChatResponse queued = new ChatResponse("after-throw", Collections.<ToolCall>emptyList(), "stop");
        MockLlmClient client = new MockLlmClient(Arrays.asList(queued));
        LlmException scheduled = new LlmException("boom");
        client.throwOnNextCall(scheduled);

        try {
            client.chat(anyMessages(), noTools());
            fail("Expected scheduled LlmException on first call");
        } catch (LlmException e) {
            assertEquals(scheduled, e);
        }

        ChatResponse actual = client.chat(anyMessages(), noTools());
        assertEquals("after-throw", actual.getContent());
    }
}
