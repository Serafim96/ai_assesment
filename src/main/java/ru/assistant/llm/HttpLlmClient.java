package ru.assistant.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HttpLlmClient implements LlmClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final int MAX_ATTEMPTS = 2;

    private final OkHttpClient httpClient;
    private final String endpoint;
    private final String model;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public HttpLlmClient(OkHttpClient httpClient, String endpoint, String model, String apiKey, long timeoutMs) {
        this.httpClient = httpClient.newBuilder()
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
        this.endpoint = endpoint;
        this.model = model;
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages, List<ToolSpec> tools) throws LlmException {
        Request request;
        try {
            request = buildRequest(messages, tools);
        } catch (IOException e) {
            throw new LlmException("Failed to build LLM request payload", e);
        }

        Response response = null;
        IOException lastNetworkError = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (response != null) {
                response.close();
                response = null;
            }
            try {
                response = httpClient.newCall(request).execute();
                lastNetworkError = null;
            } catch (IOException e) {
                lastNetworkError = e;
                if (attempt == MAX_ATTEMPTS) {
                    throw new LlmException("LLM request failed: network/timeout error, host=" + safeHost(request), e);
                }
                continue;
            }
            if (isServerError(response.code()) && attempt < MAX_ATTEMPTS) {
                continue;
            }
            break;
        }

        try {
            if (response == null) {
                throw new LlmException("LLM request failed: no response received, host=" + safeHost(request), lastNetworkError);
            }
            if (!response.isSuccessful()) {
                throw new LlmException("LLM request failed: status=" + response.code() + ", host=" + safeHost(request));
            }
            String bodyString;
            try {
                ResponseBody responseBody = response.body();
                bodyString = responseBody != null ? responseBody.string() : "";
            } catch (IOException e) {
                throw new LlmException("Failed to read LLM response body, host=" + safeHost(request), e);
            }
            try {
                return parseResponse(bodyString);
            } catch (IOException | RuntimeException e) {
                throw new LlmException("Failed to parse LLM response, host=" + safeHost(request), e);
            }
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private boolean isServerError(int code) {
        return code / 100 == 5;
    }

    private String safeHost(Request request) {
        return request.url().host();
    }

    private Request buildRequest(List<ChatMessage> messages, List<ToolSpec> tools) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);

        ArrayNode messagesNode = root.putArray("messages");
        for (ChatMessage message : messages) {
            ObjectNode messageNode = messagesNode.addObject();
            messageNode.put("role", message.getRole());
            if (message.getContent() != null) {
                messageNode.put("content", message.getContent());
            } else {
                messageNode.putNull("content");
            }
            if ("tool".equals(message.getRole()) && message.getToolCallId() != null) {
                messageNode.put("tool_call_id", message.getToolCallId());
            }
        }

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsNode = root.putArray("tools");
            for (ToolSpec tool : tools) {
                ObjectNode toolNode = toolsNode.addObject();
                toolNode.put("type", "function");
                ObjectNode functionNode = toolNode.putObject("function");
                functionNode.put("name", tool.getName());
                functionNode.put("description", tool.getDescription());
                JsonNode parametersNode = objectMapper.readTree(tool.getJsonSchema());
                functionNode.set("parameters", parametersNode);
            }
        }

        String json = objectMapper.writeValueAsString(root);
        RequestBody body = RequestBody.create(json, JSON_MEDIA_TYPE);

        return new Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();
    }

    private ChatResponse parseResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        JsonNode choice = choices.isArray() && choices.size() > 0 ? choices.get(0) : objectMapper.createObjectNode();
        JsonNode message = choice.path("message");

        String content = null;
        JsonNode contentNode = message.path("content");
        if (!contentNode.isMissingNode() && !contentNode.isNull()) {
            content = contentNode.asText();
        }

        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode toolCallsNode = message.path("tool_calls");
        if (toolCallsNode.isArray()) {
            for (JsonNode toolCallNode : toolCallsNode) {
                String id = toolCallNode.path("id").asText(null);
                JsonNode function = toolCallNode.path("function");
                String name = function.path("name").asText(null);
                String argumentsJson = function.path("arguments").asText(null);
                toolCalls.add(new ToolCall(id, name, argumentsJson));
            }
        }

        JsonNode finishReasonNode = choice.path("finish_reason");
        String finishReason = (finishReasonNode.isMissingNode() || finishReasonNode.isNull())
                ? null
                : finishReasonNode.asText();

        return new ChatResponse(content, toolCalls, finishReason);
    }
}
