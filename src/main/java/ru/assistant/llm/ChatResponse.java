package ru.assistant.llm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatResponse {

    private final String content;
    private final List<ToolCall> toolCalls;
    private final String finishReason;

    public ChatResponse(String content, List<ToolCall> toolCalls, String finishReason) {
        this.content = content;
        this.toolCalls = toolCalls == null
                ? Collections.<ToolCall>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(toolCalls));
        this.finishReason = finishReason;
    }

    public String getContent() {
        return content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public String getFinishReason() {
        return finishReason;
    }
}
