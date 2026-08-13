package ru.assistant.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.assistant.llm.ChatMessage;
import ru.assistant.llm.ChatResponse;
import ru.assistant.llm.LlmClient;
import ru.assistant.llm.LlmException;
import ru.assistant.llm.ToolCall;
import ru.assistant.llm.ToolSpec;
import ru.assistant.tools.Tool;
import ru.assistant.tools.ToolError;
import ru.assistant.tools.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AgentToolLoop {

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final int maxSteps;
    private final ObjectMapper mapper = new ObjectMapper();

    public AgentToolLoop(LlmClient llmClient, ToolRegistry toolRegistry, int maxSteps) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.maxSteps = maxSteps;
    }

    public String run(String userMessage) throws LlmException {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user(userMessage));
        List<ToolSpec> toolSpecs = toolRegistry.toToolSpecs();

        for (int step = 0; step < maxSteps; step++) {
            ChatResponse response = llmClient.chat(messages, toolSpecs);

            if (response.getToolCalls().isEmpty()) {
                return response.getContent();
            }

            messages.add(ChatMessage.assistant(response.getContent()));
            for (ToolCall call : response.getToolCalls()) {
                String result = executeToolCall(call);
                messages.add(ChatMessage.tool(call.getId(), result));
            }
        }

        return "Reached the maximum number of steps without a final answer.";
    }

    private String executeToolCall(ToolCall call) {
        Optional<Tool> tool = toolRegistry.get(call.getName());
        if (!tool.isPresent()) {
            return "Error: unknown tool '" + call.getName() + "'";
        }

        Map<String, Object> args;
        try {
            args = parseArguments(call.getArgumentsJson());
        } catch (RuntimeException e) {
            return "Error: invalid arguments for tool '" + call.getName() + "'";
        }

        try {
            return tool.get().execute(args);
        } catch (ToolError e) {
            return "Error: " + e.getMessage();
        }
    }

    private Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        try {
            return mapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Invalid tool arguments JSON", e);
        }
    }
}
