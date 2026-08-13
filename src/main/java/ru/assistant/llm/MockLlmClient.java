package ru.assistant.llm;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class MockLlmClient implements LlmClient {

    private final Deque<ChatResponse> queue;
    private LlmException pendingException;

    public MockLlmClient(List<ChatResponse> responses) {
        this.queue = new ArrayDeque<>(responses);
    }

    public void throwOnNextCall(LlmException toThrow) {
        this.pendingException = toThrow;
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages, List<ToolSpec> tools) throws LlmException {
        if (pendingException != null) {
            LlmException toThrow = pendingException;
            pendingException = null;
            throw toThrow;
        }
        if (queue.isEmpty()) {
            throw new LlmException("MockLlmClient: response queue is empty (test misconfiguration) - "
                    + "configure enough queued ChatResponse entries for the expected number of chat() calls");
        }
        return queue.poll();
    }
}
